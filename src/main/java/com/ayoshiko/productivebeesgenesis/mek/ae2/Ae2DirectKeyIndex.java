package com.ayoshiko.productivebeesgenesis.mek.ae2;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

/**
 * AE2 直接 Key 索引 — 维护 slot 名称到 key 的映射，支持快速查找。
 * <br/>
 * 用于批量推送时按 key 分组 slot，减少 AE2 网络请求次数。
 */
final class Ae2DirectKeyIndex<K> {
	private final String[] slots;
	private final K[] keys;
	private final boolean complete;
	private final Object2ObjectOpenHashMap<K, int[]> keyToSlots;

	private Ae2DirectKeyIndex(String[] slots, K[] keys, boolean complete, Object2ObjectOpenHashMap<K, int[]> keyToSlots) {
		this.slots = slots;
		this.keys = keys;
		this.complete = complete;
		this.keyToSlots = keyToSlots;
	}

	@SuppressWarnings("unchecked")
	static <K> Ae2DirectKeyIndex<K> of(String[] slotNames, K[] sourceKeys, Ae2DirectKeyIndex<K> previous) {
		boolean allResolved = true;
		Object2ObjectOpenHashMap<K, int[]> map = new Object2ObjectOpenHashMap<>();
		java.util.List<Integer> indices = new java.util.ArrayList<>();

		for (int i = 0; i < sourceKeys.length; i++) {
			K key = sourceKeys[i];
			if (key == null) { allResolved = false; continue; }
			indices.add(i);
			map.computeIfAbsent(key, k -> new int[0]);
		}

		// Build slot arrays for each key
		for (var entry : map.object2ObjectEntrySet()) {
			K key = entry.getKey();
			java.util.List<Integer> slotIndices = new java.util.ArrayList<>();
			for (int i = 0; i < sourceKeys.length; i++) {
				if (key.equals(sourceKeys[i])) slotIndices.add(i);
			}
			int[] arr = new int[slotIndices.size()];
			for (int i = 0; i < slotIndices.size(); i++) arr[i] = slotIndices.get(i);
			map.put(key, arr);
		}

		return new Ae2DirectKeyIndex<>(slotNames, sourceKeys, allResolved, map);
	}

	boolean isComplete() { return complete; }

	int[] slotsFor(K key) {
		int[] slots = keyToSlots.get(key);
		return slots != null ? slots : new int[0];
	}
}
