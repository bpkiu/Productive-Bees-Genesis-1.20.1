package com.ayoshiko.productivebeesgenesis.util;

import com.ayoshiko.productivebeesgenesis.apiary.IPbUpgradeProvider;
import com.ayoshiko.productivebeesgenesis.apiary.PbUpgradeType;
import com.ayoshiko.productivebeesgenesis.compat.productivelib.InventoryHandlerHelper;
import com.ayoshiko.productivebeesgenesis.compat.productivelib.IUpgradeableBlockEntity;
import com.ayoshiko.productivebeesgenesis.init.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Essence Conversion upgrade helper — converts pending/stored outputs
 * using the crafting-recipe-based conversion index.
 * <br/>
 * Ported from NeoForge 1.21 to Forge 1.20.1:
 * <ul>
 *   <li>NeoForge {@code IItemHandler}/{@code IItemHandlerModifiable} -> Forge equivalents</li>
 *   <li>ProductiveLib {@code IUpgradeableBlockEntity}/{@code InventoryHandlerHelper} -> local stubs</li>
 *   <li>{@code isSameItemSameComponents} -> {@code isSameItemSameTags}</li>
 * </ul>
 *
 * @since 1.0.7
 */
public final class EssenceConversionUpgradeHelper {

	private EssenceConversionUpgradeHelper() {
	}

	public static boolean hasUpgrade(BlockEntity blockEntity) {
		if (blockEntity instanceof IPbUpgradeProvider provider) {
			return provider.getPbUpgradeInstalledCount(PbUpgradeType.ESSENCE_CONVERSION) > 0;
		}
		if (blockEntity instanceof IUpgradeableBlockEntity upgradeable) {
			return upgradeable.getUpgradeCount(ModItems.ESSENCE_CONVERSION_UPGRADE.get()) > 0;
		}
		return false;
	}

	public static List<ItemStack> convert(Level level, List<ItemStack> list) {
		if (level == null || list == null || list.isEmpty()) {
			return list;
		}
		return convert(EssenceConversionRecipeIndex.snapshotFor(level), list);
	}

	private static List<ItemStack> convert(EssenceConversionRecipeIndex.ConversionSnapshot snapshot, List<ItemStack> list) {
		ArrayList<ItemStack> merged = new ArrayList<>(list.size());
		for (ItemStack stack : list) {
			if (stack == null || stack.isEmpty()) continue;
			addAmount(merged, stack, stack.getCount());
		}
		if (merged.isEmpty()) {
			return merged;
		}
		ArrayList<ItemStack> result = new ArrayList<>(merged.size());
		for (ItemStack stack : merged) {
			convertStack(snapshot, result, stack);
		}
		return result;
	}

	private static void convertStack(EssenceConversionRecipeIndex.ConversionSnapshot snapshot,
			List<ItemStack> list, ItemStack itemStack) {
		EssenceConversionRecipeIndex.Conversion conversion = snapshot.find(itemStack);
		if (conversion == null) {
			addAmount(list, itemStack, itemStack.getCount());
			return;
		}
		AmountConversion amounts = calculateAmounts(itemStack.getCount(),
				conversion.inputCount(), conversion.result().getCount());
		if (amounts.resultAmount() == 0L) {
			addAmount(list, itemStack, itemStack.getCount());
			return;
		}
		addAmount(list, itemStack, amounts.remainder());
		if (!conversion.continueChain()) {
			addAmount(list, conversion.result(), amounts.resultAmount());
			return;
		}
		EssenceConversionRecipeIndex.Conversion next = snapshot.find(conversion.resultKey());
		if (next == null) {
			addAmount(list, conversion.result(), amounts.resultAmount());
			return;
		}
		AmountConversion nextAmounts = calculateAmounts(amounts.resultAmount(),
				next.inputCount(), next.result().getCount());
		addAmount(list, conversion.result(), nextAmounts.remainder());
		addAmount(list, next.result(), nextAmounts.resultAmount());
	}

	static AmountConversion calculateAmounts(long input, int inputCount, int resultCount) {
		if (input <= 0L || inputCount <= 0 || resultCount <= 0) {
			return AmountConversion.EMPTY;
		}
		long multiplier = input / (long) inputCount;
		return new AmountConversion(input % (long) inputCount, multiplier * (long) resultCount);
	}

	public static boolean convertPendingOutputs(Level level, Map<ItemStack, Integer> map) {
		if (level == null || map == null || map.isEmpty()) {
			return false;
		}
		EssenceConversionRecipeIndex.ConversionSnapshot snapshot =
				EssenceConversionRecipeIndex.snapshotFor(level);
		if (!containsConversionCandidate(map, snapshot)) {
			return false;
		}
		ArrayList<ItemStack> original = new ArrayList<>(map.size());
		for (Map.Entry<ItemStack, Integer> entry : map.entrySet()) {
			int count = Math.max(0, entry.getValue());
			if (count <= 0) continue;
			original.add(entry.getKey().copyWithCount(count));
		}
		List<ItemStack> converted = convert(snapshot, original);
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

	public static boolean convertStored(Level level, IItemHandler handler) {
		if (level == null || level.isClientSide() || handler == null
				|| !(handler instanceof IItemHandlerModifiable)) {
			return false;
		}
		IItemHandlerModifiable modifiable = (IItemHandlerModifiable) handler;
		int[] slots = resolveOutputSlots(handler);
		if (slots.length == 0) {
			return false;
		}
		EssenceConversionRecipeIndex.ConversionSnapshot snapshot =
				EssenceConversionRecipeIndex.snapshotFor(level);
		if (!containsConversionCandidate(handler, slots, snapshot)) {
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
		List<ItemStack> converted = convert(snapshot, original);
		if (sameStacks(original, converted) || !canFitReplacement(handler, slots, converted)) {
			return false;
		}
		ItemStack[] backup = snapshot(handler, slots);
		extractAll(handler, slots);
		if (insertAll(handler, slots, converted)) {
			return true;
		}
		for (int i = 0; i < slots.length; i++) {
			modifiable.setStackInSlot(slots[i], backup[i]);
		}
		LogThrottle.error("essence_conversion_restore",
				"精华转化写入失败，已恢复原输出库存");
		return false;
	}

	public static void invalidateCache() {
		EssenceConversionRecipeIndex.invalidate();
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

	private static boolean containsConversionCandidate(IItemHandler handler, int[] slots,
			EssenceConversionRecipeIndex.ConversionSnapshot snapshot) {
		for (int slot : slots) {
			ItemStack stack = handler.getStackInSlot(slot);
			if (stack.isEmpty() || snapshot.find(stack) == null) continue;
			return true;
		}
		return false;
	}

	private static boolean containsConversionCandidate(Map<ItemStack, Integer> map,
			EssenceConversionRecipeIndex.ConversionSnapshot snapshot) {
		for (Map.Entry<ItemStack, Integer> entry : map.entrySet()) {
			if (entry.getValue() <= 0 || entry.getKey().isEmpty()
					|| snapshot.find(entry.getKey()) == null) continue;
			return true;
		}
		return false;
	}

	private static boolean canFitReplacement(IItemHandler handler, int[] slots, List<ItemStack> list) {
		ItemStack[] sim = new ItemStack[slots.length];
		int[] limits = new int[slots.length];
		for (int i = 0; i < slots.length; i++) {
			sim[i] = ItemStack.EMPTY;
			limits[i] = Math.max(1, handler.getSlotLimit(slots[i]));
		}
		for (ItemStack stack : list) {
			long remaining = stack.getCount();
			for (int i = 0; i < sim.length && remaining > 0; i++) {
				ItemStack existing = sim[i];
				if (existing.isEmpty() || !ItemStack.isSameItemSameTags(existing, stack)) continue;
				int cap = Math.min(limits[i], existing.getMaxStackSize());
				remaining -= (long) Math.max(0, cap - existing.getCount());
				if (existing.getCount() >= cap) continue;
				existing.setCount(cap);
			}
			for (int i = 0; i < sim.length && remaining > 0; i++) {
				if (!sim[i].isEmpty()) continue;
				int cap = Math.min(limits[i], stack.getMaxStackSize());
				int placed = (int) Math.min(remaining, (long) cap);
				sim[i] = stack.copyWithCount(placed);
				remaining -= (long) placed;
			}
			if (remaining > 0) return false;
		}
		return true;
	}

	private static void extractAll(IItemHandler handler, int[] slots) {
		for (int slot : slots) {
			ItemStack stack = handler.getStackInSlot(slot);
			if (stack.isEmpty()) continue;
			handler.extractItem(slot, stack.getCount(), false);
		}
	}

	private static boolean insertAll(IItemHandler handler, int[] slots, List<ItemStack> list) {
		for (ItemStack stack : list) {
			int remaining = stack.getCount();
			for (int slot : slots) {
				if (remaining <= 0) break;
				ItemStack existing = handler.getStackInSlot(slot);
				if (existing.isEmpty() || !ItemStack.isSameItemSameTags(existing, stack)) continue;
				int cap = Math.min(remaining, Math.min(handler.getSlotLimit(slot), stack.getMaxStackSize()));
				ItemStack leftover = handler.insertItem(slot, stack.copyWithCount(cap), false);
				remaining -= cap - leftover.getCount();
			}
			for (int slot : slots) {
				if (remaining <= 0 || !handler.getStackInSlot(slot).isEmpty()) continue;
				int cap = Math.min(remaining, Math.min(handler.getSlotLimit(slot), stack.getMaxStackSize()));
				ItemStack leftover = handler.insertItem(slot, stack.copyWithCount(cap), false);
				remaining -= cap - leftover.getCount();
			}
			if (remaining > 0) return false;
		}
		return true;
	}

	private static ItemStack[] snapshot(IItemHandler handler, int[] slots) {
		ItemStack[] result = new ItemStack[slots.length];
		for (int i = 0; i < slots.length; i++) {
			result[i] = handler.getStackInSlot(slots[i]).copy();
		}
		return result;
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

	record AmountConversion(long remainder, long resultAmount) {
		private static final AmountConversion EMPTY = new AmountConversion(0L, 0L);
	}
}
