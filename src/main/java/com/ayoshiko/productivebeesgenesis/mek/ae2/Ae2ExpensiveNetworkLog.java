package com.ayoshiko.productivebeesgenesis.mek.ae2;

import com.ayoshiko.productivebeesgenesis.ProductiveBeesGenesis;

import java.util.concurrent.atomic.AtomicLong;

/**
 * AE2 高开销网络操作的冷却日志器。
 * <br/>
 * 对探测降级与配额收缩等异常网络行为按 10 秒冷却输出告警日志，
 * 避免同类事件高频刷屏，同时保留可观测性。
 */
final class Ae2ExpensiveNetworkLog {

	/** 功能特性标识 */
	static final String FEATURE = "ae2_output_push";

	/** 日志冷却时间（纳秒），10 秒 */
	private static final long COOLDOWN_NANOS = 10_000_000_000L; // 10s

	/** 纳秒到微秒的换算系数 */
	private static final long NANOS_PER_MICRO = 1_000L;

	/** 上次探测降级日志时间戳 */
	private static final AtomicLong lastProbeLogNanos = new AtomicLong();

	/** 上次配额收缩日志时间戳 */
	private static final AtomicLong lastQuotaLogNanos = new AtomicLong();

	private Ae2ExpensiveNetworkLog() {
	}

	/**
	 * 探测降级为候选扫描时输出告警日志。
	 *
	 * @param elapsedNanos 探测耗时（纳秒）
	 */
	static void probeDowngraded(long elapsedNanos) {
		if (tryPass(lastProbeLogNanos)) {
			ProductiveBeesGenesis.LOGGER.warn("[AE2] {} probe downgraded to candidate scan, elapsed={}us", FEATURE, elapsedNanos / NANOS_PER_MICRO);
		}
	}

	/**
	 * 插入配额收缩时输出告警日志。
	 *
	 * @param originalQuota  原始配额
	 * @param acceptedItems  实际接收数量
	 * @param attemptedItems 尝试插入数量
	 */
	static void insertQuotaShrunk(long originalQuota, int acceptedItems, int attemptedItems) {
		if (tryPass(lastQuotaLogNanos)) {
			ProductiveBeesGenesis.LOGGER.warn("[AE2] {} insert quota shrunk: original={} accepted={} attempted={}", FEATURE, originalQuota, acceptedItems, attemptedItems);
		}
	}

	/**
	 * 尝试通过冷却门控。
	 *
	 * @param lastLogNanos 上次日志时间戳持有者
	 * @return 超过冷却时间返回 true，表示可以输出日志
	 */
	private static boolean tryPass(AtomicLong lastLogNanos) {
		long now = System.nanoTime();
		long last = lastLogNanos.get();
		if (now - last < COOLDOWN_NANOS) return false;
		return lastLogNanos.compareAndSet(last, now);
	}

	static {
		// 静态初始化块
	}
}
