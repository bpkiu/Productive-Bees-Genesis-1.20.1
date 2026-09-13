package com.ayoshiko.productivebeesgenesis.mek.ae2;

/**
 * AE2 插入成本追踪器 — 跟踪 AE2 插入操作的纳秒级耗时，用于动态限流。
 * <br/>
 * 维护单 tile 级别和全局级别的 EWMA 平均耗时，防止 AE2 网络操作过度消耗 tick 时间。
 */
final class Ae2InsertCostTracker {
	private static final long HEALTHY_INSERT_NANOS = 2_000L;     // 2us per insert
	private static final long QUOTA_BUDGET_NANOS = 500_000L;     // 0.5ms per tick budget
	private static final long TILE_TICK_BUDGET_NANOS = 500_000L;  // 0.5ms per tile per tick
	private static final long GLOBAL_TICK_BUDGET_NANOS = 2_000_000L; // 2ms global per tick
	private static final int EWMA_SHIFT = 3; // EWMA weight = 1/8

	private static long globalTick = -1;
	private static long globalSpentNanos = 0;

	private long averageNanos = HEALTHY_INSERT_NANOS;
	private long tileTick = -1;
	private long tileSpentNanos = 0;
	private int tileInsertsThisTick = 0;

	Ae2InsertCostTracker() {}

	void record(long currentTick, long spentNanos) {
		refreshTile(currentTick);
		tileSpentNanos += spentNanos;
		tileInsertsThisTick++;
		averageNanos = averageNanos - (averageNanos >> EWMA_SHIFT) + (spentNanos >> EWMA_SHIFT);
	}

	boolean canInsertNow(long currentTick, int batchSize) {
		refreshTile(currentTick);
		if (tileSpentNanos >= TILE_TICK_BUDGET_NANOS) return false;
		if (globalSpentNanos >= GLOBAL_TICK_BUDGET_NANOS) return false;
		long projectedCost = averageNanos * batchSize;
		if (tileSpentNanos + projectedCost > TILE_TICK_BUDGET_NANOS) return false;
		return tileSpentNanos + projectedCost <= GLOBAL_TICK_BUDGET_NANOS;
	}

	boolean isExhausted(long currentTick) {
		refreshTile(currentTick);
		return tileSpentNanos >= TILE_TICK_BUDGET_NANOS || globalSpentNanos >= GLOBAL_TICK_BUDGET_NANOS;
	}

	int keyQuota(int requestedQuota) {
		if (averageNanos <= HEALTHY_INSERT_NANOS) return requestedQuota;
		long budgetForKeys = TILE_TICK_BUDGET_NANOS - tileSpentNanos;
		int affordable = (int) (budgetForKeys / Math.max(1, averageNanos));
		return Math.max(1, Math.min(requestedQuota, affordable));
	}

	boolean isExpensiveNetwork() { return averageNanos > HEALTHY_INSERT_NANOS * 10; }
	long averageCostNanos() { return averageNanos; }

	void reset() {
		averageNanos = HEALTHY_INSERT_NANOS;
		tileSpentNanos = 0;
		tileInsertsThisTick = 0;
	}

	private void refreshTile(long currentTick) {
		if (tileTick != currentTick) {
			tileTick = currentTick;
			tileSpentNanos = 0;
			tileInsertsThisTick = 0;
			refreshGlobal(currentTick);
		}
	}

	private static void refreshGlobal(long currentTick) {
		if (globalTick != currentTick) {
			globalTick = currentTick;
			globalSpentNanos = 0;
		}
	}

	static {}
}
