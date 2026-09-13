package com.ayoshiko.productivebeesgenesis.mek.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.me.helpers.BaseActionSource;
import net.minecraft.world.item.ItemStack;
import java.util.function.ToIntFunction;

/**
 * AE2 直接物品推送会话 — 批量推送物品到 AE2 网络。
 * <br/>
 * 实现 {@link ToIntFunction} 接口，接受 {@link ItemStack} 返回成功插入的数量。
 * 内部维护插入计数、延迟计数和退避状态。
 */
public final class Ae2DirectItemPushSession implements ToIntFunction<ItemStack> {
	/** 单 tick 内推送的最大 item key 种类数 */
	private static final int MAX_ITEM_KEYS_PER_TICK = 64;
	/** 连续零接受次数阈值，超过后触发退避 */
	private static final int CONSECUTIVE_ZERO_ACCEPT_LIMIT = 3;
	/** 病态操作耗时阈值（纳秒）— 超过说明网络含昂贵外部存储 */
	private static final long PATHOLOGICAL_OPERATION_NANOS = 1_000_000L;

	private MEStorage meStorage;
	private Ae2KeyBackoffRegistry<AEItemKey> keyBackoff;
	private long nowNanos;
	private long gameTick;
	private int attemptedCount;
	private int deferredCount;
	private long spentInsertNanos;
	private int zeroAcceptStreak;
	private boolean slowInsertDetected;
	private Ae2InsertCostTracker costTracker;
	private int insertQuota;

	Ae2DirectItemPushSession(MEStorage meStorage, Ae2KeyBackoffRegistry<AEItemKey> keyBackoff,
			long gameTick, Ae2InsertCostTracker costTracker) {
		reset(meStorage, keyBackoff, gameTick, costTracker);
	}

	void reset(MEStorage meStorage, Ae2KeyBackoffRegistry<AEItemKey> keyBackoff,
			long gameTick, Ae2InsertCostTracker costTracker) {
		this.meStorage = meStorage;
		this.keyBackoff = keyBackoff;
		this.gameTick = gameTick;
		this.costTracker = costTracker;
		this.nowNanos = System.nanoTime();
		this.attemptedCount = 0;
		this.deferredCount = 0;
		this.spentInsertNanos = 0;
		this.zeroAcceptStreak = 0;
		this.slowInsertDetected = false;
		this.insertQuota = MAX_ITEM_KEYS_PER_TICK;
		if (costTracker != null) {
			insertQuota = costTracker.keyQuota(insertQuota);
		}
	}

	public int attemptedCount() { return attemptedCount; }
	public int deferredCount() { return deferredCount; }

	public boolean shouldTriggerBackoff() {
		return slowInsertDetected || zeroAcceptStreak >= CONSECUTIVE_ZERO_ACCEPT_LIMIT;
	}

	private void recordInsertCost(long nanos) {
		spentInsertNanos += nanos;
		if (costTracker != null) {
			costTracker.record(gameTick, nanos);
		}
		if (nanos > PATHOLOGICAL_OPERATION_NANOS) {
			slowInsertDetected = true;
		}
	}

	@Override
	public int applyAsInt(ItemStack stack) {
		if (meStorage == null || stack.isEmpty() || insertQuota <= 0) {
			deferredCount += stack.getCount();
			return 0;
		}
		if (costTracker != null && !costTracker.canInsertNow(gameTick, 1)) {
			deferredCount += stack.getCount();
			return 0;
		}

		AEItemKey key = AEItemKey.of(stack);
		if (keyBackoff != null && keyBackoff.shouldSkip(key, System.nanoTime())) {
			deferredCount += stack.getCount();
			return 0;
		}

		attemptedCount++;
		long start = System.nanoTime();
		long inserted = meStorage.insert(key, stack.getCount(), Actionable.MODULATE, ActionSourceHolder.INSTANCE);
		long elapsed = System.nanoTime() - start;
		recordInsertCost(elapsed);

		int insertedInt = (int) Math.min(Integer.MAX_VALUE, inserted);
		if (insertedInt == 0) {
			zeroAcceptStreak++;
			if (shouldTriggerBackoff() && keyBackoff != null) {
				keyBackoff.recordFailure(key, System.nanoTime());
			}
			deferredCount += stack.getCount();
		} else {
			zeroAcceptStreak = 0;
		}
		insertQuota--;
		return insertedInt;
	}

	/**
	 * 懒加载 Holder — AE2 未安装时本类初始化不触发 {@link BaseActionSource} 类解析
	 * <br/>
	 * 与 Ae2InputPuller/Ae2OutputPusher 的 ActionSourceHolder 模式一致（Issue #8 防御深度）。
	 */
	private static final class ActionSourceHolder {
		/** 全局共享的 AE2 操作源 — {@link BaseActionSource} 完全无状态，全局只需 1 个实例 */
		static final IActionSource INSTANCE = new BaseActionSource() {};
	}
}
