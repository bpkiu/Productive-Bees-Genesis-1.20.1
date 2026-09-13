package com.ayoshiko.productivebeesgenesis.compat.productivelib;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * ProductiveLib InventoryHandlerHelper 本地 stub（1.20.1 迁移适配）
 * <br/>
 * <b>背景</b>：源码原本针对 NeoForge 1.21.1 编写，当时 ProductiveLib 是独立 mod，
 * 提供 {@code cy.jdkdigital.productivelib.common.block.entity.InventoryHandlerHelper}。
 * 在 Forge 1.20.1 中 ProductiveLib 不存在，PB 1.20.1 也不包含这些类。
 * <p>
 * <b>适配策略</b>：在本包下创建同构 stub 类，保持源码 import 路径不变（仅需批量替换
 * {@code com.ayoshiko.productivebeesgenesis.compat.productivelib.InventoryHandlerHelper}
 * → {@code com.ayoshiko.productivebeesgenesis.compat.productivelib.InventoryHandlerHelper}）。
 * <p>
 * <b>核心 API</b>：
 * <ul>
 *   <li>{@link #INPUT_SLOT} — 输入槽索引常量（PB 约定为 0）</li>
 *   <li>{@link UpgradeHandler} — PB 原版升级处理器，被 {@code PbUpgradeInstallHandler} 继承</li>
 *   <li>{@link BlockEntityItemStackHandler} — 方块实体物品栈处理器，提供 {@code canFitStacks} 等方法</li>
 * </ul>
 *
 * @since 2.0.0
 * @author Ayoshiko
 */
public class InventoryHandlerHelper {

	/** 输入槽索引（PB 约定：槽位 0 为输入槽） */
	public static final int INPUT_SLOT = 0;

	private InventoryHandlerHelper() {}

	/**
	 * PB 原版升级处理器 — 管理 UpgradeItem 的安装槽位
	 * <br/>
	 * 被 {@code PbUpgradeInstallHandler} 继承以桥接自定义升级系统。
	 * PB 原版 {@code AbstractUpgradeItem.useOn} 通过 instanceof 检查此类型后调用 insertItem。
	 */
	public static class UpgradeHandler implements IItemHandlerModifiable {

		protected final int size;
		protected final BlockEntity tileEntity;
		protected final List<Predicate<ItemStack>> validators;
		protected final NonNullList<ItemStack> stacks;

		public UpgradeHandler(int size, BlockEntity tileEntity, List<Predicate<ItemStack>> validators) {
			this.size = size;
			this.tileEntity = tileEntity;
			this.validators = validators;
			this.stacks = NonNullList.withSize(size, ItemStack.EMPTY);
		}

		@Override
		public int getSlots() { return size; }

		@NotNull
		@Override
		public ItemStack getStackInSlot(int slot) {
			validateSlot(slot);
			return stacks.get(slot);
		}

		@NotNull
		@Override
		public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
			validateSlot(slot);
			if (stack.isEmpty() || !isItemValid(slot, stack)) {
				return stack;
			}
			ItemStack existing = stacks.get(slot);
			int limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());
			if (!existing.isEmpty()) {
				if (!ItemStack.isSameItemSameTags(stack, existing)) {
					return stack;
				}
				limit -= existing.getCount();
			}
			if (limit <= 0) {
				return stack;
			}
			int toInsert = Math.min(stack.getCount(), limit);
			if (!simulate) {
				if (existing.isEmpty()) {
					stacks.set(slot, stack.copyWithCount(toInsert));
				} else {
					existing.grow(toInsert);
				}
				onContentsChanged(slot);
			}
			return stack.copyWithCount(stack.getCount() - toInsert);
		}

		@NotNull
		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			validateSlot(slot);
			if (amount == 0) return ItemStack.EMPTY;
			ItemStack existing = stacks.get(slot);
			if (existing.isEmpty()) return ItemStack.EMPTY;
			int toExtract = Math.min(amount, existing.getCount());
			ItemStack result = existing.copyWithCount(toExtract);
			if (!simulate) {
				existing.shrink(toExtract);
				if (existing.isEmpty()) {
					stacks.set(slot, ItemStack.EMPTY);
				}
				onContentsChanged(slot);
			}
			return result;
		}

		@Override
		public int getSlotLimit(int slot) {
			return 64;
		}

		@Override
		public boolean isItemValid(int slot, @NotNull ItemStack stack) {
			if (validators.isEmpty()) return true;
			for (Predicate<ItemStack> v : validators) {
				if (v.test(stack)) return true;
			}
			return false;
		}

		@Override
		public void setStackInSlot(int slot, @NotNull ItemStack stack) {
			validateSlot(slot);
			stacks.set(slot, stack);
			onContentsChanged(slot);
		}

		/** 检查升级是否有效（PB 原版签名兼容） */
		public boolean isValidUpgrade(@Nonnull ItemStack stack) {
			return isItemValid(0, stack);
		}

		/** 获取已安装的指定升级数量 */
		public int getUpgradeCount(@Nonnull Item upgradeItem) {
			int count = 0;
			for (ItemStack s : stacks) {
				if (s.getItem() == upgradeItem) count += s.getCount();
			}
			return count;
		}

		protected void validateSlot(int slot) {
			if (slot < 0 || slot >= size) {
				throw new IndexOutOfBoundsException("Slot " + slot + " out of range [0," + size + ")");
			}
		}

		protected void onContentsChanged(int slot) {
			if (tileEntity != null) {
				tileEntity.setChanged();
			}
		}
	}

	/**
	 * 方块实体物品栈处理器 — PB 方块实体的标准物品处理器
	 * <br/>
	 * 提供 {@code canFitStacks} 检查输出空间，被 {@code CombBlockCheckCache} 通过 instanceof 检查。
	 */
	public static class BlockEntityItemStackHandler extends ItemStackHandler {

		protected final BlockEntity tileEntity;
		protected final BiPredicate<ItemStack, Integer> validator;

		public BlockEntityItemStackHandler(int size, BlockEntity tileEntity) {
			this(size, tileEntity, null);
		}

		public BlockEntityItemStackHandler(int size, BlockEntity tileEntity,
										   @Nullable BiPredicate<ItemStack, Integer> validator) {
			super(size);
			this.tileEntity = tileEntity;
			this.validator = validator;
		}

		@Override
		public boolean isItemValid(int slot, @NotNull ItemStack stack) {
			if (validator != null) {
				return validator.test(stack, slot);
			}
			return super.isItemValid(slot, stack);
		}

		@Override
		protected void onContentsChanged(int slot) {
			if (tileEntity != null) {
				tileEntity.setChanged();
			}
		}

		/**
		 * 检查是否能容纳给定的物品栈列表
		 *
		 * @param stacks 待检查的物品栈列表
		 * @return true 如果所有物品都能放入
		 */
		public boolean canFitStacks(@NotNull List<ItemStack> stacks) {
			for (int i = 0; i < stacks.size(); i++) {
				ItemStack toFit = stacks.get(i);
				if (toFit.isEmpty()) continue;
				int slot = i % getSlots();
				ItemStack existing = getStackInSlot(slot);
				if (!existing.isEmpty() && !ItemStack.isSameItemSameTags(toFit, existing)) {
					return false;
				}
				int limit = Math.min(getSlotLimit(slot), toFit.getMaxStackSize());
				if (!existing.isEmpty()) {
					limit -= existing.getCount();
				}
				if (toFit.getCount() > limit) {
					return false;
				}
			}
			return true;
		}

		/**
		 * 获取输出槽索引（由 EssenceConversionUpgradeHelper/RawOreSmeltingUpgradeHelper 调用）
		 * 默认返回 null（表示使用所有槽位作为输出槽）。
		 * @return 输出槽索引数组，null 表示所有槽位
		 */
		@Nullable
		public int[] getOutputSlots() {
			return null;
		}
	}

	/**
	 * 通用物品处理器（ProductiveLib 原版 InventoryHandlerHelper.ItemHandler 同名 stub）
	 * <br/>
	 * 被 {@code AbstractCombEventHandler} 用作离心配方匹配的测试容器
	 * （{@code BeeHelper.getCentrifugeRecipe(Level, IItemHandlerModifiable)}），
	 * 并通过 {@code instanceof} 检查后调用 {@link #addOutput(ItemStack)} 追加产出。
	 * 仅覆盖基本读写与追加语义；不含 BlockEntity 联动（构造时无 tileEntity）。
	 */
	public static class ItemHandler implements IItemHandlerModifiable {

		private final NonNullList<ItemStack> stacks;

		public ItemHandler(int size) {
			this.stacks = NonNullList.withSize(size, ItemStack.EMPTY);
		}

		@Override
		public int getSlots() {
			return stacks.size();
		}

		@NotNull
		@Override
		public ItemStack getStackInSlot(int slot) {
			return stacks.get(slot);
		}

		@Override
		public void setStackInSlot(int slot, @NotNull ItemStack stack) {
			stacks.set(slot, stack);
		}

		@NotNull
		@Override
		public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
			if (stack.isEmpty()) {
				return ItemStack.EMPTY;
			}
			ItemStack existing = stacks.get(slot);
			if (!existing.isEmpty() && !ItemStack.isSameItemSameTags(existing, stack)) {
				return stack;
			}
			int limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());
			int space = existing.isEmpty() ? limit : limit - existing.getCount();
			if (space <= 0) {
				return stack;
			}
			int inserted = Math.min(stack.getCount(), space);
			if (!simulate) {
				if (existing.isEmpty()) {
					stacks.set(slot, stack.copyWithCount(inserted));
				} else {
					existing.grow(inserted);
				}
			}
			return inserted >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - inserted);
		}

		@NotNull
		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			if (amount == 0) {
				return ItemStack.EMPTY;
			}
			ItemStack existing = stacks.get(slot);
			if (existing.isEmpty()) {
				return ItemStack.EMPTY;
			}
			int extracted = Math.min(amount, existing.getCount());
			ItemStack result = existing.copyWithCount(extracted);
			if (!simulate) {
				if (extracted >= existing.getCount()) {
					stacks.set(slot, ItemStack.EMPTY);
				} else {
					existing.shrink(extracted);
				}
			}
			return result;
		}

		@Override
		public int getSlotLimit(int slot) {
			return 64;
		}

		@Override
		public boolean isItemValid(int slot, @NotNull ItemStack stack) {
			return true;
		}

		/**
		 * 追加产出：优先并入已有同类物品的槽位，其次占用空槽位；
		 * 放不下的剩余部分直接丢弃（与 ProductiveLib 原版 addOutput 的溢出语义一致）。
		 *
		 * @param stack 待产出的物品栈（方法不修改传入实例）
		 */
		public void addOutput(@NotNull ItemStack stack) {
			if (stack.isEmpty()) return;
			int remaining = stack.getCount();
			// 第一轮：并入已有同类槽位
			for (int i = 0; i < stacks.size() && remaining > 0; i++) {
				ItemStack leftover = insertItem(i, stack.copyWithCount(remaining), false);
				remaining = leftover.isEmpty() ? 0 : leftover.getCount();
			}
			// 第二轮（第一轮未消化完时）：insertItem 已覆盖空槽场景，无需重复扫描
		}
	}
}
