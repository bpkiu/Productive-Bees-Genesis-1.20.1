package com.ayoshiko.productivebeesgenesis.mek.ae2;

import appeng.api.stacks.AEItemKey;
import net.minecraft.core.HolderLookup;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * AE2 物品指纹缓存 — 缓存 {@link AEItemKey} 到指纹字符串的映射。
 * <br/>
 * 指纹用于在 NBT 中标识物品，避免存储完整的 ItemStack。
 */
final class Ae2FingerprintCache {
	private static final int MAX_ENTRIES = 512;
	private final Map<AEItemKey, String> cache = new java.util.LinkedHashMap<>(MAX_ENTRIES, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<AEItemKey, String> eldest) {
			return size() > MAX_ENTRIES;
		}
	};
	private HolderLookup.Provider registries;

	Ae2FingerprintCache() {}

	String get(AEItemKey key, HolderLookup.Provider registries) {
		if (key == null) return "";
		this.registries = registries;
		String cached = cache.get(key);
		if (cached != null) return cached;
		String fingerprint = computeFingerprint(key);
		cache.put(key, fingerprint);
		return fingerprint;
	}

	private String computeFingerprint(AEItemKey key) {
		// Use item registry name as fingerprint
		var item = key.getItem();
		var key2 = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
		return key2 != null ? key2.toString() : item.getDescriptionId();
	}
}
