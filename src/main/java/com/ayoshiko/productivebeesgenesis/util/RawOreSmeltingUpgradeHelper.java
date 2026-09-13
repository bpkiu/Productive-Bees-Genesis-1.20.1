package com.ayoshiko.productivebeesgenesis.util;

import com.ayoshiko.productivebeesgenesis.apiary.IPbUpgradeProvider;
import com.ayoshiko.productivebeesgenesis.apiary.PbUpgradeType;
import com.ayoshiko.productivebeesgenesis.compat.productivelib.InventoryHandlerHelper;
import com.ayoshiko.productivebeesgenesis.compat.productivelib.IUpgradeableBlockEntity;
import com.ayoshiko.productivebeesgenesis.init.ModItems;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.common.recipe.MekanismRecipeType;
import mekanism.common.recipe.lookup.cache.InputRecipeCache.SingleItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Raw Ore Smelting upgrade helper — converts raw ore items into ingots
 * using Mekanism or vanilla smelting recipes.
 * <br/>
 * Ported from NeoForge 1.21 to Forge 1.20.1:
 * <ul>
 *   <li>{@code SingleRecipeInput} -> {@link SimpleContainer} for vanilla recipe lookup</li>
 *   <li>{@code RecipeHolder} unwrapping removed (1.20.1 returns recipes directly)</li>
 *   <li>{@code getResultItem(HolderLookup.Provider)} -> {@code getResultItem()} (no arg)</li>
 *   <li>Mekanism recipe API wrapped in try-catch for version safety</li>
 *   <li>{@code isSameItemSameComponents} -> {@code isSameItemSameTags}</li>
 * </ul>
 *
 * @since 1.0.7
 */
public final class RawOreSmeltingUpgradeHelper {

	private static final TagKey<Item> COMMON_RAW_MATERIALS = itemTag("c", "raw_materials");
	private static final TagKey<Item> COMMON_RAW_ORES = itemTag("c", "raw_ores");
	private static final TagKey<Item> FORGE_RAW_MATERIALS = itemTag("forge", "raw_materials");
	private static final TagKey<Item> FORGE_RAW_ORES = itemTag("forge", "raw_ores");
	private static final TagKey<Item> COMMON_INGOTS = itemTag("c", "ingots");
	private static final TagKey<Item> FORGE_INGOTS = itemTag("forge", "ingots");

	private static final List<TagKey<Item>> RAW_TAGS = List.of(
			COMMON_RAW_MATERIALS, COMMON_RAW_ORES, FORGE_RAW_MATERIALS, FORGE_RAW_ORES);
	private static final List<TagKey<Item>> INGOT_TAGS = List.of(COMMON_INGOTS, FORGE_INGOTS);

	private static final ConcurrentHashMap<Item, Optional<Conversion>> CONVERSIONS = new ConcurrentHashMap<>();

	private RawOreSmeltingUpgradeHelper() {
	}

	public static boolean hasUpgrade(BlockEntity blockEntity) {
		if (blockEntity instanceof IPbUpgradeProvider provider) {
			return provider.getPbUpgradeInstalledCount(PbUpgradeType.RAW_ORE_SMELTING) > 0;
		}
		if (blockEntity instanceof IUpgradeableBlockEntity upgradeable) {
			return upgradeable.getUpgradeCount(ModItems.RAW_ORE_SMELTING_UPGRADE.get()) > 0;
		}
		return false;
	}

	public static boolean convertPendingOutputs(Level level, Map<ItemStack, Integer> map) {
		if (level == null || map == null || map.isEmpty()) {
			return false;
		}
		ArrayList<ItemStack> original = new ArrayList<>(map.size());
		for (Map.Entry<ItemStack, Integer> entry : map.entrySet()) {
			int count = Math.max(0, entry.getValue());
			if (count <= 0) continue;
			original.add(entry.getKey().copyWithCount(count));
		}
		if (original.isEmpty()) {
			return false;
		}
		List<ItemStack> converted = convert(level, original);
		if (sameStacks(original, converted)) {
			return false;
		}
		map.clear();
		for (ItemStack stack : converted) {
			if (stack.isEmpty()) continue;
			map.put(stack.copyWithCount(stack.getCount()), stack.getCount());
		}
		return true;
	}

	public static List<ItemStack> convert(Level level, List<ItemStack> list) {
		if (level == null || list == null || list.isEmpty()) {
			return list;
		}
		ArrayList<ItemStack> result = new ArrayList<>(list.size());
		for (ItemStack stack : list) {
			if (stack == null || stack.isEmpty()) continue;
			Conversion conversion = findConversion(level, stack).orElse(null);
			if (conversion == null) {
				addAmount(result, stack, stack.getCount());
				continue;
			}
			long multiplier = (long) stack.getCount() / (long) conversion.inputCount();
			long remainder = stack.getCount() % conversion.inputCount();
			if (multiplier > 0) {
				addAmount(result, conversion.result(), multiplier * (long) conversion.result().getCount());
			}
			if (remainder > 0) {
				addAmount(result, stack, remainder);
			}
		}
		return result;
	}

	public static boolean convertStored(Level level, IItemHandler handler) {
		if (level == null || level.isClientSide()
				|| !(handler instanceof IItemHandlerModifiable)) {
			return false;
		}
		IItemHandlerModifiable modifiable = (IItemHandlerModifiable) handler;
		int[] slots = resolveOutputSlots(handler);
		if (slots.length == 0) {
			return false;
		}
		ArrayList<ItemStack> original = new ArrayList<>(slots.length);
		for (int slot : slots) {
			ItemStack stack = handler.getStackInSlot(slot);
			if (stack.isEmpty()) continue;
			addAmount(original, stack, stack.getCount());
		}
		if (original.isEmpty()) {
			return false;
		}
		List<ItemStack> converted = convert(level, original);
		if (sameStacks(original, converted) || !canFit(handler, slots, converted)) {
			return false;
		}
		ItemStack[] backup = new ItemStack[slots.length];
		for (int i = 0; i < slots.length; i++) {
			backup[i] = handler.getStackInSlot(slots[i]).copy();
		}
		for (int slot : slots) {
			handler.extractItem(slot, handler.getStackInSlot(slot).getCount(), false);
		}
		if (insertAll(handler, slots, converted)) {
			return true;
		}
		for (int i = 0; i < slots.length; i++) {
			modifiable.setStackInSlot(slots[i], backup[i]);
		}
		return false;
	}

	public static void invalidateCache() {
		CONVERSIONS.clear();
	}

	static long convertedCount(int input, int inputCount, int resultCount) {
		if (input <= 0 || inputCount <= 0 || resultCount <= 0) {
			return input;
		}
		return (long) input / (long) inputCount * (long) resultCount;
	}

	private static Optional<Conversion> findConversion(Level level, ItemStack itemStack) {
		if (level == null || itemStack == null || itemStack.isEmpty() || !isRawMaterial(itemStack)) {
			return Optional.empty();
		}
		Item item = itemStack.getItem();
		return CONVERSIONS.computeIfAbsent(item, k -> resolveConversion(level, itemStack));
	}

	private static Optional<Conversion> resolveConversion(Level level, ItemStack itemStack) {
		Optional<Conversion> mekResult = resolveMekanismConversion(level, itemStack);
		if (mekResult.isPresent()) {
			return mekResult;
		}
		return resolveVanillaSmelting(level, itemStack);
	}

	@SuppressWarnings("unchecked")
	private static Optional<Conversion> resolveMekanismConversion(Level level, ItemStack itemStack) {
		try {
			SingleItem<ItemStackToItemStackRecipe> cache = MekanismRecipeType.SMELTING.getInputCache();
			ItemStackToItemStackRecipe recipe = cache.findFirstRecipe(level, itemStack);
			if (recipe != null) {
				ItemStack output = recipe.getOutput(itemStack);
				long inputCount = recipe.getInput().getNeededAmount(itemStack);
				if (isValidOutput(itemStack, output) && inputCount > 0 && inputCount <= Integer.MAX_VALUE) {
					return Optional.of(new Conversion((int) inputCount, output.copy()));
				}
			}
		} catch (Throwable ignored) {
			// Mekanism API mismatch or not loaded — fall through to vanilla
		}
		return Optional.empty();
	}

	private static ItemStackToItemStackRecipe extractRecipe(Object result) {
		if (result instanceof ItemStackToItemStackRecipe r) {
			return r;
		}
		if (result instanceof Optional<?> opt && opt.isPresent()
				&& opt.get() instanceof ItemStackToItemStackRecipe r) {
			return r;
		}
		return null;
	}

	private static Optional<Conversion> resolveVanillaSmelting(Level level, ItemStack itemStack) {
		try {
			SimpleContainer container = new SimpleContainer(itemStack);
			Optional<SmeltingRecipe> recipe = level.getRecipeManager()
					.getRecipeFor(RecipeType.SMELTING, container, level);
			if (recipe.isEmpty()) {
				return Optional.empty();
			}
			ItemStack result = recipe.get().getResultItem(level.registryAccess());
			return isValidOutput(itemStack, result)
					? Optional.of(new Conversion(1, result.copy()))
					: Optional.empty();
		} catch (Throwable t) {
			return Optional.empty();
		}
	}

	private static boolean isValidOutput(ItemStack input, ItemStack output) {
		return output != null && !output.isEmpty()
				&& !ItemStack.isSameItemSameTags(input, output)
				&& isIngot(output);
	}

	private static boolean isRawMaterial(ItemStack itemStack) {
		for (TagKey<Item> tag : RAW_TAGS) {
			if (itemStack.is(tag)) return true;
		}
		return false;
	}

	private static boolean isIngot(ItemStack itemStack) {
		for (TagKey<Item> tag : INGOT_TAGS) {
			if (itemStack.is(tag)) return true;
		}
		return false;
	}

	private static int[] resolveOutputSlots(IItemHandler handler) {
		if (handler instanceof InventoryHandlerHelper.BlockEntityItemStackHandler beHandler) {
			int[] outputSlots = beHandler.getOutputSlots();
			return outputSlots == null ? new int[]{} : outputSlots;
		}
		int[] all = new int[handler.getSlots()];
		for (int i = 0; i < all.length; i++) {
			all[i] = i;
		}
		return all;
	}

	private static boolean canFit(IItemHandler handler, int[] slots, List<ItemStack> list) {
		ItemStack[] sim = new ItemStack[slots.length];
		for (int i = 0; i < slots.length; i++) {
			sim[i] = ItemStack.EMPTY;
		}
		for (ItemStack stack : list) {
			int remaining = stack.getCount();
			for (int i = 0; i < sim.length && remaining > 0; i++) {
				ItemStack existing = sim[i];
				if (existing.isEmpty() || !ItemStack.isSameItemSameTags(existing, stack)) continue;
				int cap = Math.min(Math.max(1, handler.getSlotLimit(slots[i])), existing.getMaxStackSize());
				int add = Math.min(remaining, Math.max(0, cap - existing.getCount()));
				existing.grow(add);
				remaining -= add;
			}
			for (int i = 0; i < sim.length && remaining > 0; i++) {
				if (!sim[i].isEmpty()) continue;
				int cap = Math.min(Math.max(1, handler.getSlotLimit(slots[i])), stack.getMaxStackSize());
				int place = Math.min(remaining, cap);
				sim[i] = stack.copyWithCount(place);
				remaining -= place;
			}
			if (remaining > 0) return false;
		}
		return true;
	}

	private static boolean insertAll(IItemHandler handler, int[] slots, List<ItemStack> list) {
		for (ItemStack stack : list) {
			int remaining = stack.getCount();
			for (int slot : slots) {
				if (remaining <= 0) break;
				ItemStack existing = handler.getStackInSlot(slot);
				if (existing.isEmpty() || !ItemStack.isSameItemSameTags(existing, stack)) continue;
				int cap = Math.min(remaining, Math.min(Math.max(1, handler.getSlotLimit(slot)), stack.getMaxStackSize()));
				ItemStack leftover = handler.insertItem(slot, stack.copyWithCount(cap), false);
				remaining -= cap - leftover.getCount();
			}
			for (int slot : slots) {
				if (remaining <= 0 || !handler.getStackInSlot(slot).isEmpty()) continue;
				int cap = Math.min(remaining, Math.min(Math.max(1, handler.getSlotLimit(slot)), stack.getMaxStackSize()));
				ItemStack leftover = handler.insertItem(slot, stack.copyWithCount(cap), false);
				remaining -= cap - leftover.getCount();
			}
			if (remaining > 0) return false;
		}
		return true;
	}

	private static boolean sameStacks(List<ItemStack> a, List<ItemStack> b) {
		if (a.size() != b.size()) {
			return false;
		}
		for (int i = 0; i < a.size(); i++) {
			if (!ItemStack.isSameItemSameTags(a.get(i), b.get(i))
					|| a.get(i).getCount() != b.get(i).getCount()) {
				return false;
			}
		}
		return true;
	}

	private static void addAmount(List<ItemStack> list, ItemStack stack, long amount) {
		if (stack == null || stack.isEmpty() || amount <= 0) {
			return;
		}
		for (ItemStack existing : list) {
			if (!ItemStack.isSameItemSameTags(existing, stack)) continue;
			int add = (int) Math.min(amount, Integer.MAX_VALUE - (long) existing.getCount());
			existing.grow(add);
			if ((amount -= (long) add) > 0) continue;
			return;
		}
		while (amount > 0) {
			int chunk = (int) Math.min(amount, Integer.MAX_VALUE);
			list.add(stack.copyWithCount(chunk));
			amount -= (long) chunk;
		}
	}

	private static TagKey<Item> itemTag(String namespace, String path) {
		return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(namespace, path));
	}

	private record Conversion(int inputCount, ItemStack result) {
		private Conversion {
			result = result.copy();
		}
	}
}
