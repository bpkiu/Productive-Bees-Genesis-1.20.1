package com.ayoshiko.productivebeesgenesis.mek.ae2;

/**
 * AE2 输入通道公平性策略。
 * <br/>
 * 提供空通道预算、类型配额均分与是否运行本轮推送的纯函数判定，
 * 保证多通道、多类型输入在配额受限时按公平份额调度。
 */
final class Ae2InputLaneFairness {

	/** 表示通道数不受限制的哨兵值 */
	static final int UNLIMITED_LANES = -1;

	private Ae2InputLaneFairness() {
	}

	/**
	 * 计算空通道的预算份额。
	 *
	 * @param totalLanes 通道总数
	 * @param emptyLanes 空闲通道数
	 * @return 单通道可分得的预算，通道数非正时返回最大值
	 */
	static int emptyLaneBudget(int totalLanes, int emptyLanes) {
		if (totalLanes <= 0) return Integer.MAX_VALUE;
		return Math.max(1, emptyLanes / Math.max(1, totalLanes));
	}

	/**
	 * 计算每个活跃类型可分得的配额份额。
	 *
	 * @param totalQuota  总配额
	 * @param activeTypes 活跃类型数
	 * @return 单类型配额，活跃类型数非正时返回 0
	 */
	static long typeQuotaShare(long totalQuota, int activeTypes) {
		if (activeTypes <= 0) return 0;
		return totalQuota / activeTypes;
	}

	/**
	 * 判断是否应运行本轮推送。
	 *
	 * @param activeTypes 活跃类型数
	 * @param hasQuota    是否仍有可用配额
	 * @return 存在活跃类型且有配额时返回 true
	 */
	static boolean shouldRunPass(int activeTypes, boolean hasQuota) {
		return activeTypes > 0 && hasQuota;
	}
}
