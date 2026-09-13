package com.ayoshiko.productivebeesgenesis.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Essence Conversion recipe index — scans crafting recipes to build a
 * conversion map (compression: N items -> 1 block; decompression: 1 block -> N items).
 * <br/>
 * Ported from NeoForge 1.21 to Forge 1.20.1:
 * <ul>
 *   <li>{@code DataComponentPatch} -> {@link CompoundTag} (NBT tag) for StackKey</li>
 *   <li>{@code getResultItem(HolderLookup.Provider)} -> {@code getResultItem()} (no arg)</li>
 *   <li>{@code getAllRecipesFor} returns {@code List<CraftingRecipe>} directly (no RecipeHolder wrapping)</li>
 *   <li>{@code isSameItemSameComponents} -> {@code isSameItemSameTags}</li>
 * </ul>
 *
 * @since 1.0.7
 */
final class EssenceConversionRecipeIndex {

	private static final List<TagKey<Item>> EXCLUDED_INPUT_TAGS = List.of(
			itemTag("c", "raw_materials"), itemTag("c", "raw_ores"),
			itemTag("forge", "raw_materials"), itemTag("forge", "raw_ores"),
			itemTag("c", "ingots"), itemTag("forge", "ingots"),
			itemTag("c", "gems"), itemTag("forge", "gems"));
	private static final List<TagKey<Item>> DECOMPRESSION_INPUT_TAGS = List.of(
			itemTag("c", "ingots"), itemTag("forge", "ingots"),
			itemTag("c", "gems"), itemTag("forge", "gems"));
	private static final List<TagKey<Item>> STORAGE_BLOCK_TAGS = List.of(
			itemTag("c", "storage_blocks"), itemTag("forge", "storage_blocks"));

	private static final ResourceLocation OBSIDIAN_SHARD_ID =
			ResourceLocation.fromNamespaceAndPath("productivebees", "obsidian_shard");
	private static final ResourceLocation OBSIDIAN_ID =
			ResourceLocation.fromNamespaceAndPath("minecraft", "obsidian");
	private static final ResourceLocation REDSTONE_ESSENCE_ID =
			ResourceLocation.fromNamespaceAndPath("mysticalagriculture", "redstone_essence");
	private static final ResourceLocation REDSTONE_ID =
			ResourceLocation.fromNamespaceAndPath("minecraft", "redstone");
	private static final ResourceLocation NETHER_STAR_ESSENCE_ID =
			ResourceLocation.fromNamespaceAndPath("mysticalagriculture", "nether_star_essence");
	private static final ResourceLocation NETHER_STAR_SHARD_ID =
			ResourceLocation.fromNamespaceAndPath("mysticalagradditions", "nether_star_shard");
	private static final ResourceLocation NETHER_STAR_ID =
			ResourceLocation.fromNamespaceAndPath("minecraft", "nether_star");

	private static final long BUILD_RETRY_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(5L);
	private static volatile ConversionSnapshot conversionSnapshot = ConversionSnapshot.EMPTY;
	private static volatile boolean conversionSnapshotLoaded;
	private static volatile long lastFailedBuildNanos = Long.MIN_VALUE;

	private EssenceConversionRecipeIndex() {
	}

	static ConversionSnapshot snapshotFor(Level level) {
		return ensureConversionSnapshot(level);
	}

	static synchronized void invalidate() {
		conversionSnapshotLoaded = false;
		conversionSnapshot = ConversionSnapshot.EMPTY;
		lastFailedBuildNanos = Long.MIN_VALUE;
	}

	private static ConversionSnapshot ensureConversionSnapshot(Level level) {
		if (conversionSnapshotLoaded) {
			return conversionSnapshot;
		}
		if (isBuildRetryThrottled()) {
			return conversionSnapshot;
		}
		synchronized (EssenceConversionRecipeIndex.class) {
			if (conversionSnapshotLoaded || isBuildRetryThrottled()) {
				return conversionSnapshot;
			}
			try {
				conversionSnapshot = buildConversionSnapshot(level);
				conversionSnapshotLoaded = true;
				lastFailedBuildNanos = Long.MIN_VALUE;
			} catch (RuntimeException e) {
				lastFailedBuildNanos = System.nanoTime();
				LogThrottle.error("essence_conversion_index",
						"精华转化配方索引构建失败，将在 5 秒后重试", e);
			}
			return conversionSnapshot;
		}
	}

	private static boolean isBuildRetryThrottled() {
		if (lastFailedBuildNanos == Long.MIN_VALUE) {
			return false;
		}
		long elapsed = System.nanoTime() - lastFailedBuildNanos;
		return elapsed >= 0L && elapsed < BUILD_RETRY_INTERVAL_NANOS;
	}

	private static ConversionSnapshot buildConversionSnapshot(Level level) {
		HashMap<StackKey, Map<RecipeSignature, RecipePattern>> byInput = new HashMap<>();
		for (CraftingRecipe recipe : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
			try {
				for (RecipePattern pattern : parseRecipes(level, recipe)) {
					byInput.computeIfAbsent(pattern.inputKey(), k -> new HashMap<>())
							.merge(pattern.signature(), pattern, EssenceConversionRecipeIndex::preferExplicitPattern);
				}
			} catch (RuntimeException e) {
				LogThrottle.warn("essence_conversion_recipe",
						"精华转化跳过无法解析的合成配方 {}", recipe.getId());
			}
		}

		HashMap<StackKey, List<RecipePattern>> patternsByInput = new HashMap<>(byInput.size());
		for (Map.Entry<StackKey, Map<RecipeSignature, RecipePattern>> entry : byInput.entrySet()) {
			patternsByInput.put(entry.getKey(), List.copyOf(entry.getValue().values()));
		}

		Map<StackKey, RecipePattern> selected = new HashMap<>();
		for (Map.Entry<StackKey, List<RecipePattern>> entry : patternsByInput.entrySet()) {
			RecipePattern best = selectCompression(entry.getValue(), patternsByInput);
			if (best != null) {
				selected.put(entry.getKey(), best);
			}
		}

		HashSet<StackKey> resultKeys = new HashSet<>();
		for (RecipePattern p : selected.values()) {
			resultKeys.add(p.resultKey());
		}

		HashMap<StackKey, Conversion> conversions = new HashMap<>(selected.size());
		for (Map.Entry<StackKey, RecipePattern> entry : selected.entrySet()) {
			RecipePattern pattern = entry.getValue();
			if (resultKeys.contains(entry.getKey()) && !isNetherStarShardStep(entry.getKey(), pattern)) {
				continue;
			}
			conversions.put(entry.getKey(), new Conversion(pattern.inputCount(), pattern.result(),
					pattern.resultKey(), isNetherStarEssenceStep(entry.getKey(), pattern)));
		}
		return ConversionSnapshot.create(conversions);
	}

	private static List<RecipePattern> parseRecipes(Level level, CraftingRecipe craftingRecipe) {
		HashMap<StackKey, ItemStack> firstSlotOptions = new HashMap<>();
		int ingredientCount = 0;
		for (Ingredient ingredient : craftingRecipe.getIngredients()) {
			if (ingredient.isEmpty()) continue;
			ItemStack[] items = ingredient.getItems();
			if (items.length == 0) {
				return List.of();
			}
			if (++ingredientCount == 1) {
				for (ItemStack itemStack : items) {
					if (itemStack.isEmpty()) continue;
					ItemStack single = itemStack.copyWithCount(1);
					firstSlotOptions.putIfAbsent(stackKey(single), single);
				}
			} else {
				HashSet<StackKey> set = new HashSet<>(items.length);
				for (ItemStack item : items) {
					if (item.isEmpty()) continue;
					set.add(stackKey(item));
				}
				firstSlotOptions.keySet().retainAll(set);
			}
			if (firstSlotOptions.isEmpty()) {
				return List.of();
			}
		}
		if (ingredientCount == 0) {
			return List.of();
		}
		ItemStack resultItem = craftingRecipe.getResultItem(level.registryAccess());
		if (resultItem.isEmpty()) {
			return List.of();
		}
		List<RecipePattern> patterns = new ArrayList<>(firstSlotOptions.size() * 2);
		for (ItemStack input : firstSlotOptions.values()) {
			if (ItemStack.isSameItemSameTags(resultItem, input)) continue;
			patterns.add(createPattern(input, ingredientCount, resultItem, false));
			if (isDecompressionShape(ingredientCount, resultItem.getCount(),
					isStorageBlock(input), isStorageBlock(resultItem),
					isDecompressionInput(input), isExcludedInput(resultItem))) {
				patterns.add(createPattern(resultItem.copyWithCount(resultItem.getCount()),
						resultItem.getCount(), input.copyWithCount(ingredientCount), true));
			}
		}
		return List.copyOf(patterns);
	}

	private static RecipePattern createPattern(ItemStack input, int inputCount,
			ItemStack result, boolean inferred) {
		ItemStack inputSingle = input.copyWithCount(1);
		ItemStack resultCopy = result.copy();
		return new RecipePattern(stackKey(inputSingle), inputCount, resultCopy,
				stackKey(resultCopy), isStorageBlock(inputSingle), isStorageBlock(resultCopy),
				isExcludedInput(inputSingle), inferred);
	}

	private static RecipePattern preferExplicitPattern(RecipePattern a, RecipePattern b) {
		return a.inferred() && !b.inferred() ? b : a;
	}

	static boolean isCompressionShape(int inputCount, int resultCount,
			boolean inputIsBlock, boolean resultIsBlock, boolean excludedInput) {
		return inputCount >= 2 && resultCount > 0 && inputCount > resultCount
				&& !inputIsBlock && !resultIsBlock && !excludedInput;
	}

	static boolean isDecompressionShape(int inputCount, int resultCount,
			boolean inputIsBlock, boolean resultIsBlock,
			boolean isDecompressionInput, boolean excludedOutput) {
		return inputCount == 1 && resultCount > 1
				&& !inputIsBlock && !resultIsBlock && isDecompressionInput && !excludedOutput;
	}

	private static RecipePattern selectCompression(List<RecipePattern> list,
			Map<StackKey, List<RecipePattern>> map) {
		boolean hasExplicit = false;
		for (RecipePattern p : list) {
			if (p.inferred() || !p.isCompression()) continue;
			hasExplicit = true;
			break;
		}
		RecipePattern fallback = null;
		RecipePattern withReverse = null;
		int total = 0;
		int reverseCount = 0;
		for (RecipePattern p : list) {
			if (!p.isCompression() || (hasExplicit && p.inferred())) continue;
			total++;
			fallback = p;
			if (hasExactReverse(p, map)) {
				reverseCount++;
				withReverse = p;
			}
		}
		if (total == 1) {
			return fallback;
		}
		return reverseCount == 1 ? withReverse : null;
	}

	private static boolean hasExactReverse(RecipePattern pattern,
			Map<StackKey, List<RecipePattern>> map) {
		List<RecipePattern> list = map.get(pattern.resultKey());
		if (list == null) {
			return false;
		}
		for (RecipePattern p : list) {
			if (p.inputCount() != pattern.result().getCount()
					|| p.result().getCount() != pattern.inputCount()
					|| !p.resultKey().equals(pattern.inputKey())) continue;
			return true;
		}
		return false;
	}

	private static boolean isExcludedInput(ItemStack itemStack) {
		for (TagKey<Item> tag : EXCLUDED_INPUT_TAGS) {
			if (itemStack.is(tag)) return true;
		}
		return false;
	}

	private static boolean isDecompressionInput(ItemStack itemStack) {
		for (TagKey<Item> tag : DECOMPRESSION_INPUT_TAGS) {
			if (itemStack.is(tag)) return true;
		}
		return false;
	}

	private static boolean isStorageBlock(ItemStack itemStack) {
		if (itemStack.getItem() instanceof BlockItem) {
			return true;
		}
		for (TagKey<Item> tag : STORAGE_BLOCK_TAGS) {
			if (itemStack.is(tag)) return true;
		}
		return false;
	}

	private static boolean isNetherStarEssenceStep(StackKey stackKey, RecipePattern pattern) {
		return pattern.inputCount() == 9 && pattern.result().getCount() == 1
				&& hasItemId(stackKey, NETHER_STAR_ESSENCE_ID)
				&& hasItemId(pattern.resultKey(), NETHER_STAR_SHARD_ID);
	}

	private static boolean isNetherStarShardStep(StackKey stackKey, RecipePattern pattern) {
		return pattern.inputCount() == 3 && pattern.result().getCount() == 1
				&& hasItemId(stackKey, NETHER_STAR_SHARD_ID)
				&& hasItemId(pattern.resultKey(), NETHER_STAR_ID);
	}

	private static boolean hasItemId(StackKey stackKey, ResourceLocation id) {
		return BuiltInRegistries.ITEM.getKey(stackKey.item()).equals(id);
	}

	private static StackKey stackKey(ItemStack itemStack) {
		return new StackKey(itemStack.getItem(), itemStack.getTag());
	}

	private static TagKey<Item> itemTag(String namespace, String path) {
		return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(namespace, path));
	}

	// ===== Records =====

	record StackKey(Item item, @Nullable CompoundTag components) {
	}

	private record RecipeSignature(int inputCount, StackKey resultKey, int resultCount) {
	}

	private record RecipePattern(StackKey inputKey, int inputCount, ItemStack result,
			StackKey resultKey, boolean inputIsBlock, boolean resultIsBlock,
			boolean excludedInput, boolean inferred) {
		private RecipePattern {
			result = result.copy();
		}

		private boolean isCompression() {
			return isCompressionShape(inputCount, result.getCount(), inputIsBlock, resultIsBlock, excludedInput)
					|| isAllowedObsidianShardConversion()
					|| isAllowedRedstoneEssenceConversion()
					|| isAllowedNetherStarConversion();
		}

		private boolean isAllowedObsidianShardConversion() {
			return inputCount == 9 && result.getCount() == 1
					&& hasItemId(inputKey, OBSIDIAN_SHARD_ID)
					&& hasItemId(resultKey, OBSIDIAN_ID);
		}

		private boolean isAllowedRedstoneEssenceConversion() {
			return inputCount == 8 && result.getCount() == 12
					&& hasItemId(inputKey, REDSTONE_ESSENCE_ID)
					&& hasItemId(resultKey, REDSTONE_ID);
		}

		private boolean isAllowedNetherStarConversion() {
			return isNetherStarEssenceStep(inputKey, this)
					|| isNetherStarShardStep(inputKey, this);
		}

		private RecipeSignature signature() {
			return new RecipeSignature(inputCount, resultKey, result.getCount());
		}
	}

	record ConversionSnapshot(Map<StackKey, Conversion> byInput, Map<Item, Conversion> byDefaultItem) {
		private static final ConversionSnapshot EMPTY = new ConversionSnapshot(Map.of(), Map.of());

		private static ConversionSnapshot create(Map<StackKey, Conversion> map) {
			Map<StackKey, Conversion> byInput = Map.copyOf(map);
			HashMap<Item, Conversion> byDefaultItem = new HashMap<>();
			for (Map.Entry<StackKey, Conversion> entry : map.entrySet()) {
				if (entry.getKey().components() != null && !entry.getKey().components().isEmpty()) continue;
				byDefaultItem.put(entry.getKey().item(), entry.getValue());
			}
			return new ConversionSnapshot(byInput, Map.copyOf(byDefaultItem));
		}

		Conversion find(ItemStack itemStack) {
			CompoundTag tag = itemStack.getTag();
			if (tag == null || tag.isEmpty()) {
				return byDefaultItem.get(itemStack.getItem());
			}
			return byInput.get(stackKey(itemStack));
		}

		Conversion find(StackKey stackKey) {
			return byInput.get(stackKey);
		}
	}

	record Conversion(int inputCount, ItemStack result, StackKey resultKey, boolean continueChain) {
		Conversion {
			result = result.copy();
		}
	}
}
