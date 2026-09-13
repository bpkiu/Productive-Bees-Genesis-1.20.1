package com.ayoshiko.productivebeesgenesis.mek.ae2;

/**
 * 低 TPS 门控策略。
 * <br/>
 * 当服务器 TPS 低于阈值时，按调用计数稀疏放行推送操作，
 * 避免在卡服期间加剧主线程负载。
 */
final class Ae2LowTpsGate {

	/** 低 TPS 阈值，低于此值触发稀疏放行 */
	static final double LOW_TPS_THRESHOLD = 15.0;

	/** 低 TPS 期间每 N 次调用放行一次 */
	static final long LOW_TPS_ALLOW_EVERY_N_CALLS = 10L;

	private Ae2LowTpsGate() {
	}

	/**
	 * 判断本次调用是否应跳过。
	 *
	 * @param currentTps  当前服务器 TPS
	 * @param callCounter 调用计数（单调递增）
	 * @return true 表示应跳过本次推送；false 表示可以执行
	 */
	static boolean shouldSkip(double currentTps, long callCounter) {
		if (currentTps >= LOW_TPS_THRESHOLD) return false;
		return (callCounter % LOW_TPS_ALLOW_EVERY_N_CALLS) != 0;
	}
}
