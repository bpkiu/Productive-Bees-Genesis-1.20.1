package com.ayoshiko.productivebeesgenesis.mek;

/**
 * 零 tick 批量合并数学 — 纯静态工具类，为批量操作合并提供数值计算。
 * <br/>
 * 用于 {@link ZeroTickCoalesceState} 的虚拟 tick 划分与操作数/能量预算分配，
 * 在不推进真实游戏 tick 的前提下将多步操作合并处理。
 */
public final class ZeroTickBatchMath {

	private ZeroTickBatchMath() {
	}

	/**
	 * 判定批量操作是否可合并。
	 *
	 * @param batchOps 批量操作数
	 * @return {@code true} 当 {@code batchOps > 0} 时可合并
	 */
	public static boolean isCoalescible(int batchOps) {
		return batchOps > 0;
	}

	/**
	 * 提升基线值，为批量合并预留空间。
	 * <br/>
	 * 公式：{@code base + factor - 1}，即提升 {@code factor - 1} 用于合并。
	 *
	 * @param base   基础值
	 * @param factor 合并因子
	 * @return 提升后的基线值
	 */
	public static int raisedBaseline(int base, int factor) {
		return base + factor - 1;
	}

	/**
	 * 计算批量操作所需的虚拟 tick 数。
	 * <br/>
	 * 当 {@code baseline > 0} 时返回 {@code Math.max(1, batchOps / baseline)}，否则返回 1。
	 *
	 * @param batchOps 批量操作数
	 * @param baseline 基线值
	 * @return 虚拟 tick 数（≥1）
	 */
	public static int virtualTicksFor(int batchOps, int baseline) {
		return baseline > 0 ? Math.max(1, batchOps / baseline) : 1;
	}

	/**
	 * 计算每个虚拟 tick 分配的操作数。
	 * <br/>
	 * 当 {@code virtualTicks > 0} 时返回 {@code Math.max(1, batchOps / virtualTicks)}，否则返回 {@code batchOps}。
	 *
	 * @param batchOps      批量操作数
	 * @param virtualTicks  虚拟 tick 数
	 * @return 每虚拟 tick 的操作数
	 */
	public static int operationsPerVirtualTick(int batchOps, int virtualTicks) {
		return virtualTicks > 0 ? Math.max(1, batchOps / virtualTicks) : batchOps;
	}

	/**
	 * 计算每个虚拟 tick 的能量预算。
	 * <br/>
	 * 公式：{@code perTickEnergy * virtualTicks}。
	 *
	 * @param perTickEnergy 单 tick 能量
	 * @param virtualTicks  虚拟 tick 数
	 * @return 虚拟 tick 能量预算
	 */
	public static long perVirtualTickEnergyBudget(long perTickEnergy, int virtualTicks) {
		return perTickEnergy * virtualTicks;
	}

	/**
	 * 计算可负担的合并操作数。
	 * <br/>
	 * 算法：
	 * <ol>
	 *   <li>预算 = {@code perTickBudget * virtualTicks}；</li>
	 *   <li>总量 = {@code energyAvailable + budget}；</li>
	 *   <li>当 {@code perOpEnergy > 0} 时返回 {@code Math.min(Integer.MAX_VALUE, total / perOpEnergy)}，否则返回 0。</li>
	 * </ol>
	 *
	 * @param energyAvailable 可用能量
	 * @param perOpEnergy     每操作能耗
	 * @param perTickBudget   每 tick 能量预算
	 * @param virtualTicks    虚拟 tick 数
	 * @return 可负担的合并操作数
	 */
	public static int affordableCoalescedOperations(long energyAvailable, int perOpEnergy, long perTickBudget, int virtualTicks) {
		long budget = perTickBudget * virtualTicks;
		long total = energyAvailable + budget;
		return perOpEnergy > 0 ? (int) Math.min(Integer.MAX_VALUE, total / perOpEnergy) : 0;
	}
}
