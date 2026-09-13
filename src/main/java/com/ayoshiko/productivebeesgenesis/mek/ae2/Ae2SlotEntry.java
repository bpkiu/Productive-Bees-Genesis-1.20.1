package com.ayoshiko.productivebeesgenesis.mek.ae2;

import appeng.api.stacks.AEItemKey;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.world.item.ItemStack;

/**
 * AE2 输出推送槽位条目（可复用数据持有者）。
 * <br/>
 * 封装单个槽位的槽引用、物品栈、AE 物品键、数量、进程索引、槽位索引与指纹，
 * 供推送扫描跨 tick 复用以减少对象分配。
 */
final class Ae2SlotEntry {

	/** Mekanism 槽引用 */
	IInventorySlot slot;

	/** 槽内物品栈 */
	ItemStack stack;

	/** AE2 物品键 */
	AEItemKey key;

	/** 待推送数量 */
	int count;

	/** 所属进程索引 */
	int process;

	/** 槽位索引 */
	int slotIdx;

	/** 物品指纹（用于去重与缓存） */
	String fingerprint;

	Ae2SlotEntry() {
	}

	/**
	 * 一次性设置全部字段，供对象池复用时调用。
	 *
	 * @param slot         Mekanism 槽引用
	 * @param stack        槽内物品栈
	 * @param key          AE2 物品键
	 * @param count        待推送数量
	 * @param process      所属进程索引
	 * @param slotIdx      槽位索引
	 * @param fingerprint  物品指纹
	 */
	void set(IInventorySlot slot, ItemStack stack, AEItemKey key, int count, int process, int slotIdx, String fingerprint) {
		this.slot = slot;
		this.stack = stack;
		this.key = key;
		this.count = count;
		this.process = process;
		this.slotIdx = slotIdx;
		this.fingerprint = fingerprint;
	}
}
