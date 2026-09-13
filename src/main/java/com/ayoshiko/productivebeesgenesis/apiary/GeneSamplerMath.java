package com.ayoshiko.productivebeesgenesis.apiary;

/**
 * 基因采样器命中分配数学 — 纯静态工具类，确保按比例公平分配采样命中数。
 * <br/>
 * 用于 {@link GeneSampler} 的累积命中分配计算，保证不同基因型在总命中数
 * 一定时按既定比例推进，避免某基因型抢占过多命中。
 */
final class GeneSamplerMath {

	private GeneSamplerMath() {
	}

	/**
	 * 计算累积分布下当前应分配的命中数。
	 * <br/>
	 * 算法：
	 * <ol>
	 *   <li>按比例计算期望命中数 {@code expected = (long)(totalHits * ratio)}；</li>
	 *   <li>计算目标分配缺口 {@code deficit = targetAllocation - currentHits}；</li>
	 *   <li>返回 {@code Math.max(0, Math.min(expected - currentHits, deficit))}。</li>
	 * </ol>
	 * 既不会超过期望的累积进度，也不会超过目标分配缺口，保证命中分配公平。
	 *
	 * @param totalHits        总命中数
	 * @param currentHits      当前已分配命中数
	 * @param targetAllocation 目标分配数量
	 * @param ratio            分配比例（0.0~1.0）
	 * @return 本次应分配的命中数（≥0）
	 */
	static long cumulativeHitAllocation(long totalHits, long currentHits, long targetAllocation, double ratio) {
		long expected = (long) (totalHits * ratio);
		long deficit = targetAllocation - currentHits;
		return Math.max(0, Math.min(expected - currentHits, deficit));
	}
}
