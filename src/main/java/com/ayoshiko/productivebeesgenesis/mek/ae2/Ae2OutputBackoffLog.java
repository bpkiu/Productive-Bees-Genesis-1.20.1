package com.ayoshiko.productivebeesgenesis.mek.ae2;

/**
 * AE2 输出退避日志 — 记录推送失败和退避事件的日志。
 * <br/>
 * 集中管理 AE2 输出推送路径中的失败计数与诊断日志，避免日志逻辑散落在多个推送器中。
 * 计数使用 {@link java.util.concurrent.atomic.AtomicLong} 保证线程安全，
 * 节流策略参考 {@link com.ayoshiko.productivebeesgenesis.util.LogThrottle}。
 */
final class Ae2OutputBackoffLog {

	/** 物品推送失败累计计数器（节流由首次/百次策略控制） */
	private static final java.util.concurrent.atomic.AtomicLong itemPushFailureCount =
			new java.util.concurrent.atomic.AtomicLong();

	private Ae2OutputBackoffLog() {
	}

	/**
	 * 处理一次完整推送失败 — 递增计数并触发退避窗口。
	 * <br/>
	 * 计数策略：前 5 次每次输出 WARN，之后每 100 次输出一次，避免 256× 加速下刷屏。
	 *
	 * @param backoff  per-tile 物品推送退避状态
	 * @param key      触发失败的 AE 物品键（用于诊断）
	 * @param gameTick 当前游戏刻（用于日志）
	 */
	static void handleCompleteFailure(Ae2PushBackoff backoff, appeng.api.stacks.AEItemKey key, long gameTick) {
		long count = itemPushFailureCount.incrementAndGet();
		if (count <= 5 || count % 100 == 0) {
			com.ayoshiko.productivebeesgenesis.ProductiveBeesGenesis.LOGGER.warn(
					"[AE2] Item push complete failure #{} key={} tick={}", count, key, gameTick);
		}
		// TODO Ae2PushBackoff 无 blockAll(long) 方法；当前实现使用 nanoTime 的 recordFailure，
		//      与现有退避序列对齐（50ms→100ms→…→1s）。如需基于 gameTick 的阻塞语义需扩展 Ae2PushBackoff。
		backoff.recordFailure(System.nanoTime());
	}

	/**
	 * 记录慢插入退避事件。
	 *
	 * @param backoff per-tile 物品推送退避状态
	 */
	static void logSlowInsertBackoff(Ae2PushBackoff backoff) {
		com.ayoshiko.productivebeesgenesis.ProductiveBeesGenesis.LOGGER.warn(
				"[AE2] Slow insert backoff triggered");
	}

	/**
	 * 记录自适应按 key 配额的调试信息。
	 *
	 * @param tracker 插入成本追踪器
	 * @param quota   当前自适应配额
	 */
	static void logAdaptiveKeyQuota(Ae2InsertCostTracker tracker, int quota) {
		// TODO Ae2InsertCostTracker 类尚未实现；待其落地后启用 averageCostNanos() 调用。
		if (tracker == null) return;
		com.ayoshiko.productivebeesgenesis.ProductiveBeesGenesis.LOGGER.debug(
				"[AE2] Adaptive key quota: {}", quota);
	}
}
