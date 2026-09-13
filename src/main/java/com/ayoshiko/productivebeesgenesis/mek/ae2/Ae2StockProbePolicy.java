package com.ayoshiko.productivebeesgenesis.mek.ae2;

/**
 * AE2 库存探测策略 — 决定是否需要探测 AE2 网络中某 key 的库存。
 * <br/>
 * 使用 EWMA 平均耗时和重学习窗口来优化探测频率。
 */
final class Ae2StockProbePolicy<K> {
	private static final long HEALTHY_PROBE_NANOS = 5_000L;     // 5us
	private static final long PROBE_TICK_BUDGET_NANOS = 200_000L; // 0.2ms
	private static final long RELEARN_INTERVAL_TICKS = 200L;
	private static final int MAX_TRACKED_KEYS = 128;
	private static final int EWMA_SHIFT = 3;

	private long averageNanos = HEALTHY_PROBE_NANOS;
	private long probeTick = -1;
	private long spentNanos = 0;
	private long nextRelearnTick = 0;
	private boolean relearnWindow = false;
	private final it.unimi.dsi.fastutil.objects.ObjectOpenHashSet<K> knownKeys = new it.unimi.dsi.fastutil.objects.ObjectOpenHashSet<>();
	private final it.unimi.dsi.fastutil.objects.ObjectOpenHashSet<K> probeWorthyKeys = new it.unimi.dsi.fastutil.objects.ObjectOpenHashSet<>();

	Ae2StockProbePolicy() {}

	boolean shouldProbe(long currentTick, K key) {
		refreshTick(currentTick);
		if (relearnWindow) return true;
		if (!knownKeys.contains(key)) return true;
		if (averageNanos <= HEALTHY_PROBE_NANOS) return true;
		return probeWorthyKeys.contains(key);
	}

	void record(long currentTick, K key, long elapsedNanos, long available, long total) {
		refreshTick(currentTick);
		spentNanos += elapsedNanos;
		averageNanos = averageNanos - (averageNanos >> EWMA_SHIFT) + (elapsedNanos >> EWMA_SHIFT);
		knownKeys.add(key);
		if (available < total || available == 0) {
			probeWorthyKeys.add(key);
		} else {
			probeWorthyKeys.remove(key);
		}
		if (knownKeys.size() > MAX_TRACKED_KEYS) {
			knownKeys.clear();
			probeWorthyKeys.clear();
		}
	}

	void reset() {
		averageNanos = HEALTHY_PROBE_NANOS;
		spentNanos = 0;
		knownKeys.clear();
		probeWorthyKeys.clear();
	}

	long averageCostNanos() { return averageNanos; }
	boolean isExpensiveNetwork() { return averageNanos > HEALTHY_PROBE_NANOS * 10; }

	private void refreshTick(long currentTick) {
		if (probeTick != currentTick) {
			probeTick = currentTick;
			spentNanos = 0;
		}
		if (currentTick >= nextRelearnTick) {
			relearnWindow = true;
			nextRelearnTick = currentTick + RELEARN_INTERVAL_TICKS;
		} else {
			relearnWindow = false;
		}
	}
}
