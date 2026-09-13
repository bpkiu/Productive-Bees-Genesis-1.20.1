package com.ayoshiko.productivebeesgenesis.mek.ae2;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.core.HolderLookup;

import java.util.List;

/**
 * AE2 输出提交器 — 收集输出槽物品并推送到 AE2 网络。
 * <br/>
 * 提供 slot 收集、台账结算、直接按槽推送和按 key 批量推送的静态工具方法，
 * 供 {@link Ae2OutputMergedPass} 与 {@link Ae2OutputSlotPass} 复用以避免重复实现。
 */
final class Ae2OutputCommitter {

	private Ae2OutputCommitter() {
	}

	/**
	 * 收集单个输出槽的物品到缓冲区。
	 * <br/>
	 * 跳过空槽；通过 {@link AeItemKeyCache} 复用 AEItemKey；指纹通过
	 * {@link Ae2FingerprintCache} 缓存以减少重复计算。
	 *
	 * @param buffers    推送缓冲区
	 * @param process    所属进程索引
	 * @param slotIdx    槽位索引（0=主输出，1=副1，2=副2）
	 * @param slot       Mekanism 槽引用
	 * @param keyCache   AE 物品键缓存（可为 null，回退到 {@link AEItemKey#of}）
	 * @param registries 注册表访问器（用于指纹计算）
	 */
	static void collectSlot(Ae2PushBuffers buffers, int process, int slotIdx,
			IInventorySlot slot, AeItemKeyCache keyCache, HolderLookup.Provider registries) {
		if (slot == null) return;
		var stack = slot.getStack();
		if (stack.isEmpty()) return;
		AEItemKey key = keyCache != null
				? keyCache.get(process * AeItemKeyCache.SLOTS_PER_PROCESS + slotIdx, stack)
				: AEItemKey.of(stack);
		if (key == null) return;
		String fingerprint = buffers.fingerprintCache.get(key, registries);
		Ae2SlotEntry entry = borrowEntry(buffers);
		entry.set(slot, stack.copy(), key, stack.getCount(), process, slotIdx, fingerprint);
		buffers.entries.add(entry);
		buffers.keyToTotalCount.mergeLong(key, stack.getCount(), Long::sum);
	}

	/**
	 * 结算输出台账 — 按台账快照从对应槽位提取剩余物品。
	 *
	 * @param host       输出宿主（提供槽访问）
	 * @param ledger     输出台账
	 * @param registries 注册表访问器（保留供后续扩展，当前未使用）
	 * @return 实际结算的物品总数
	 */
	static int settleOutputLedger(IAe2OutputHostBase host, Ae2OutputLedger ledger,
			HolderLookup.Provider registries) {
		int totalSettled = 0;
		for (var settlement : ledger.snapshot()) {
			int remaining = settlement.remaining();
			if (remaining <= 0) continue;
			var slot = outputSlot(host, settlement.process(), settlement.slot());
			if (slot == null) continue;
			// 从槽位提取剩余物品
			var extracted = slot.extractItem(remaining, mekanism.api.Action.EXECUTE, mekanism.api.AutomationType.INTERNAL);
			if (!extracted.isEmpty()) {
				totalSettled += extracted.getCount();
			}
		}
		ledger.clear();
		return totalSettled;
	}

	/**
	 * 取指定进程的输出槽 — 委托给宿主的 {@code primaryOutputSlot/secondaryOutputSlot/tertiaryOutputSlot}。
	 * <br/>
	 * {@link IAe2OutputHostBase} 未直接提供 {@code getOutputSlot(int, int)} 方法，
	 * 此处按 slotIdx 路由到 {@link com.ayoshiko.productivebeesgenesis.mek.PbRecipeContext}
	 * 暴露的三个槽访问方法，与 {@link Ae2OutputPusher#outputSlot} 内部实现保持一致。
	 *
	 * @param host    输出宿主
	 * @param process 进程索引
	 * @param slotIdx 槽位索引（0/1/2）
	 * @return 对应槽位，无匹配索引时返回 null
	 */
	static IInventorySlot outputSlot(IAe2OutputHostBase host, int process, int slotIdx) {
		// IAe2OutputHostBase 未提供 getOutputSlot(int, int)；按 slotIdx 路由到 PbRecipeContext 的方法。
		switch (slotIdx) {
			case 0 -> { return host.primaryOutputSlot(process); }
			case 1 -> { return host.secondaryOutputSlot(process); }
			case 2 -> { return host.tertiaryOutputSlot(process); }
			default -> { return null; }
		}
	}

	/**
	 * 直接对单个槽位条目执行 AE2 插入。
	 *
	 * @param entry     槽位条目
	 * @param meStorage AE2 ME 存储
	 * @param source    AE2 操作源
	 * @param ledger    输出台账（可为 null）
	 * @return 实际插入的数量
	 */
	static int tryPushSlotDirect(Ae2SlotEntry entry, MEStorage meStorage,
			IActionSource source, Ae2OutputLedger ledger) {
		if (entry == null || entry.key == null || entry.count <= 0) return 0;
		long inserted = meStorage.insert(entry.key, entry.count,
				appeng.api.config.Actionable.MODULATE, source);
		int insertedInt = (int) Math.min(Integer.MAX_VALUE, inserted);
		if (insertedInt > 0 && ledger != null) {
			ledger.commitAccepted(entry.process, entry.slotIdx, insertedInt);
		}
		return insertedInt;
	}

	/**
	 * 按 key 批量推送 — 将同一 key 的多个槽位合并后一次插入。
	 *
	 * @param key        AE 物品键
	 * @param totalCount 待插入总数
	 * @param entries    同 key 的槽位条目列表
	 * @param meStorage  AE2 ME 存储
	 * @param source     AE2 操作源
	 * @param ledger     输出台账（可为 null）
	 * @return 实际插入的总数
	 */
	static int pushBatchKey(AEItemKey key, long totalCount, List<Ae2SlotEntry> entries,
			MEStorage meStorage, IActionSource source, Ae2OutputLedger ledger) {
		if (key == null || totalCount <= 0 || entries.isEmpty()) return 0;
		long inserted = meStorage.insert(key, totalCount,
				appeng.api.config.Actionable.MODULATE, source);
		int insertedInt = (int) Math.min(Integer.MAX_VALUE, inserted);
		// 将插入数量按顺序分摊到各槽位条目
		int remaining = insertedInt;
		for (Ae2SlotEntry entry : entries) {
			if (remaining <= 0) break;
			int take = Math.min(remaining, entry.count);
			if (ledger != null) {
				ledger.commitAccepted(entry.process, entry.slotIdx, take);
			}
			remaining -= take;
		}
		return insertedInt;
	}

	/**
	 * 从对象池借用一个 {@link Ae2SlotEntry}，池不足时扩容。
	 *
	 * @param buffers 推送缓冲区
	 * @return 可用的槽位条目
	 */
	private static Ae2SlotEntry borrowEntry(Ae2PushBuffers buffers) {
		if (buffers.entryPoolCursor < buffers.entryPool.size()) {
			return buffers.entryPool.get(buffers.entryPoolCursor++);
		}
		Ae2SlotEntry entry = new Ae2SlotEntry();
		buffers.entryPool.add(entry);
		buffers.entryPoolCursor++;
		return entry;
	}
}
