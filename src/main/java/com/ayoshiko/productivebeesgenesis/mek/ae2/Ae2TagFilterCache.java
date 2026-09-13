package com.ayoshiko.productivebeesgenesis.mek.ae2;

import appeng.api.stacks.AEItemKey;
import java.util.LinkedHashMap;

/**
 * AE2 标签过滤器结果缓存 — 缓存 {@link Ae2TagFilter} 对 {@link AEItemKey} 的判定结果。
 * <br/>
 * 当过滤器 generation 变化时自动失效。使用 LRU 淘汰策略。
 */
final class Ae2TagFilterCache {
	static final int MAX_ENTRIES = 256;
	private final LinkedHashMap<AEItemKey, Boolean> results;
	private int observedGeneration = -1;
	private long observedRecipeVersion = -1;

	Ae2TagFilterCache() {
		results = new LinkedHashMap<>(MAX_ENTRIES, 0.75f, true);
	}

	synchronized boolean allows(Ae2TagFilter filter, AEItemKey key) {
		refresh(filter);
		Boolean cached = results.get(key);
		if (cached != null) return cached;
		boolean allowed = computeAllows(filter, key);
		results.put(key, allowed);
		evictIfFull();
		return allowed;
	}

	private boolean computeAllows(Ae2TagFilter filter, AEItemKey key) {
		if (!filter.isActive()) return true;
		return filter.getSpec().allows(com.ayoshiko.productivebeesgenesis.mek.ae2.Ae2ItemTagView.candidateOf(key.getItem()));
	}

	private void refresh(Ae2TagFilter filter) {
		int gen = filter.getGeneration();
		if (gen != observedGeneration) {
			results.clear();
			observedGeneration = gen;
		}
	}

	private void evictIfFull() {
		while (results.size() > MAX_ENTRIES) {
			var it = results.entrySet().iterator();
			if (it.hasNext()) { it.next(); it.remove(); }
			else break;
		}
	}

	synchronized void clear() { results.clear(); observedGeneration = -1; }
	synchronized int size() { return results.size(); }
}
