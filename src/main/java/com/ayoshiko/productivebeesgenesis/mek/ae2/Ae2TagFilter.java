package com.ayoshiko.productivebeesgenesis.mek.ae2;

import com.ayoshiko.productivebeesgenesis.util.tagfilter.TagFilterSpec;
import net.minecraft.nbt.CompoundTag;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AE2 输入标签过滤器 — 封装白名单/黑名单标签表达式。
 * <br/>
 * 使用 {@link TagFilterSpec} 编译标签表达式，支持 NBT 持久化。
 * generation 字段用于失效关联缓存（{@link Ae2TagFilterCache}）。
 */
public final class Ae2TagFilter {
	public static final int MAX_EXPRESSION_LENGTH = 512;
	private static final String KEY_WHITELIST = "whitelist";
	private static final String KEY_BLACKLIST = "blacklist";

	private volatile TagFilterSpec spec = TagFilterSpec.EMPTY;
	private final AtomicInteger generation = new AtomicInteger(0);

	public Ae2TagFilter() {}

	public TagFilterSpec getSpec() { return spec; }
	public String getWhitelistSource() { return spec.whitelistSource(); }
	public String getBlacklistSource() { return spec.blacklistSource(); }
	public boolean isActive() { return spec.isActive(); }
	public boolean hasError() { return spec.hasError(); }
	public int getGeneration() { return generation.get(); }

	public synchronized boolean apply(String whitelist, String blacklist) {
		String wl = clamp(whitelist);
		String bl = clamp(blacklist);
		TagFilterSpec newSpec = TagFilterSpec.compile(wl, bl);
		if (newSpec.whitelistSource().equals(spec.whitelistSource())
				&& newSpec.blacklistSource().equals(spec.blacklistSource())) {
			return false;
		}
		spec = newSpec;
		generation.incrementAndGet();
		return true;
	}

	public synchronized void reset() {
		if (spec == TagFilterSpec.EMPTY) return;
		spec = TagFilterSpec.EMPTY;
		generation.incrementAndGet();
	}

	public void save(CompoundTag nbt) {
		nbt.putString(KEY_WHITELIST, spec.whitelistSource());
		nbt.putString(KEY_BLACKLIST, spec.blacklistSource());
	}

	public synchronized void load(CompoundTag nbt) {
		String wl = nbt.contains(KEY_WHITELIST) ? nbt.getString(KEY_WHITELIST) : "";
		String bl = nbt.contains(KEY_BLACKLIST) ? nbt.getString(KEY_BLACKLIST) : "";
		spec = TagFilterSpec.compile(wl, bl);
		generation.incrementAndGet();
	}

	private static String clamp(String s) {
		if (s == null) return "";
		return s.length() > MAX_EXPRESSION_LENGTH ? s.substring(0, MAX_EXPRESSION_LENGTH) : s;
	}
}
