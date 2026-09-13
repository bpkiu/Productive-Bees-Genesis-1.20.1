package com.ayoshiko.productivebeesgenesis.mek.ae2;

/**
 * AE2 过滤拉取策略 — 决定从 AE2 网络拉取物品时的准入和限额。
 * <br/>
 * 配合 {@link Ae2InputFilter} 的 FilterMode 和标签过滤器，控制输入物品的准入逻辑。
 */
final class Ae2FilterPullPolicy {
	static final long PULL_DISALLOWED = 0L;

	private Ae2FilterPullPolicy() {}

	/**
	 * 判断物品是否被准入。
	 * @param mode 过滤模式 (WHITELIST/BLACKLIST/DISABLED)
	 * @param matchesFilter 物品是否匹配过滤器
	 * @return true 如果物品被准入
	 */
	static boolean isAdmitted(Ae2InputFilter.FilterMode mode, boolean matchesFilter) {
		if (mode == Ae2InputFilter.FilterMode.DISABLED) return true;
		if (mode == Ae2InputFilter.FilterMode.WHITELIST) return matchesFilter;
		if (mode == Ae2InputFilter.FilterMode.BLACKLIST) return !matchesFilter;
		return true;
	}

	static int reserveSafeRequest(int requested, long available, long reserved) {
		long effective = available - reserved;
		if (effective <= 0) return 0;
		return (int) Math.min(requested, effective);
	}

	static long effectiveReserveFloor(boolean hasFilter, long baseReserve, boolean isSmelting, long smeltingReserve) {
		if (isSmelting) return Math.max(baseReserve, smeltingReserve);
		return baseReserve;
	}

	static long effectiveLimit(boolean whitelist, boolean blacklist, long whitelistLimit, long blacklistLimit,
			boolean hasSmelting, long smeltingLimit, boolean tagFilterActive, boolean tagFilterMatched,
			boolean lowTps, long lowTpsFactor, long hardCap) {
		long limit = hardCap;
		if (whitelist && !tagFilterMatched) return 0;
		if (blacklist && tagFilterMatched) return 0;
		if (whitelist && tagFilterActive) limit = Math.min(limit, whitelistLimit);
		if (hasSmelting) limit = Math.min(limit, smeltingLimit);
		if (lowTps) limit = limit / Math.max(1, lowTpsFactor);
		return Math.max(0, limit);
	}
}
