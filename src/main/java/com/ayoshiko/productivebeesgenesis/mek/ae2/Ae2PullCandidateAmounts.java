package com.ayoshiko.productivebeesgenesis.mek.ae2;

import appeng.api.stacks.AEItemKey;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

/**
 * AE2 拉取候选数量映射（按 AEItemKey 聚合）。
 * <br/>
 * 使用 fastutil 的 Object2IntOpenHashMap 存储每个 AE2 物品键的候选拉取数量，
 * 供输入拉取路径在扫描后批量查询。
 */
final class Ae2PullCandidateAmounts {

	/** 候选数量映射 */
	private final Object2IntOpenHashMap<AEItemKey> amounts;

	Ae2PullCandidateAmounts() {
		amounts = new Object2IntOpenHashMap<>();
	}

	/** 清空全部候选数量 */
	void clear() {
		amounts.clear();
	}

	/**
	 * 写入指定键的候选数量。
	 *
	 * @param key    AE2 物品键
	 * @param amount 候选数量
	 */
	void put(AEItemKey key, int amount) {
		amounts.put(key, amount);
	}

	/**
	 * 查询指定键的候选数量。
	 *
	 * @param key AE2 物品键
	 * @return 候选数量，不存在时返回 0
	 */
	int get(AEItemKey key) {
		return amounts.getOrDefault(key, 0);
	}
}
