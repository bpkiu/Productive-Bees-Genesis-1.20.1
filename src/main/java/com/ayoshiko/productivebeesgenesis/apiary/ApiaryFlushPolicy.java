package com.ayoshiko.productivebeesgenesis.apiary;

/**
 * 蜂箱产出刷新策略 — 纯静态工具类，决定何时将累积产物刷入输出缓冲。
 * <br/>
 * 依据累积数量、升级等级、可用槽位与 tick 间隔综合判定刷新时机，
 * 避免每 tick 频繁刷新造成性能损耗，同时防止缓冲溢出。
 */
final class ApiaryFlushPolicy {

	private ApiaryFlushPolicy() {
	}

	/** 基础累积阈值：累积物品数达到此值前不刷新（4 个）。 */
	static final int BASE_ACCUMULATION_THRESHOLD = 4;

	/** 累积阈值上限：升级等级再高也不超过此值（64 个）。 */
	static final int MAX_ACCUMULATION_THRESHOLD = 64;

	/**
	 * 计算指定升级等级下的累积阈值。
	 * <br/>
	 * 公式：{@code Math.min(BASE_ACCUMULATION_THRESHOLD << upgradeLevel, MAX_ACCUMULATION_THRESHOLD)}，
	 * 即每升一级阈值翻倍，但封顶于 {@link #MAX_ACCUMULATION_THRESHOLD}。
	 *
	 * @param upgradeLevel 升级等级（≥0）
	 * @return 该等级下的累积阈值
	 */
	static int accumulationThreshold(int upgradeLevel) {
		return Math.min(BASE_ACCUMULATION_THRESHOLD << upgradeLevel, MAX_ACCUMULATION_THRESHOLD);
	}

	/**
	 * 判定是否应触发刷新。
	 * <br/>
	 * 满足以下任一条件即刷新：
	 * <ol>
	 *   <li>累积数量 ≥ 当前升级等级的累积阈值；</li>
	 *   <li>累积数量 ≥ 可用输出槽位数（缓冲即将占满）；</li>
	 *   <li>tick 间隔 ≥ 1200（每完整周期强制刷新一次）。</li>
	 * </ol>
	 *
	 * @param accumulatedCount 已累积的物品数量
	 * @param upgradeLevel     当前升级等级
	 * @param availableSlots   可用输出槽位数
	 * @param tickInterval     距上次刷新的 tick 间隔
	 * @return {@code true} 应刷新；{@code false} 继续累积
	 */
	static boolean shouldFlush(int accumulatedCount, int upgradeLevel, int availableSlots, int tickInterval) {
		return accumulatedCount >= accumulationThreshold(upgradeLevel)
				|| accumulatedCount >= availableSlots
				|| tickInterval >= 1200;
	}
}
