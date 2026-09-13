package com.ayoshiko.productivebeesgenesis.util.tagfilter;

/**
 * 标签过滤器规格，封装白名单与黑名单表达式。
 *
 * <p>白名单决定允许哪些候选项通过：未命中白名单则拒绝；黑名单决定拒绝哪些
 * 候选项：命中黑名单则拒绝。两者均为空时表示不激活任何过滤。解析错误以
 * 本地化键形式保留，由上层决定如何提示用户。</p>
 */
public record TagFilterSpec(
		String whitelistSource,
		String blacklistSource,
		TagExpression whitelist,
		TagExpression blacklist,
		String whitelistErrorKey,
		String blacklistErrorKey) {

	/** 空规格，不激活任何过滤。 */
	public static final TagFilterSpec EMPTY = new TagFilterSpec("", "", null, null, null, null);

	/** 编译白名单与黑名单文本为规格，收集可能的错误键。 */
	public static TagFilterSpec compile(String whitelist, String blacklist) {
		TagExpressionParser.Result wl = TagExpressionParser.parse(whitelist);
		TagExpressionParser.Result bl = TagExpressionParser.parse(blacklist);
		return new TagFilterSpec(
				whitelist == null ? "" : whitelist,
				blacklist == null ? "" : blacklist,
				wl.expression(),
				bl.expression(),
				wl.errorKey(),
				bl.errorKey());
	}

	/** 任一表达式存在时视为激活。 */
	public boolean isActive() {
		return whitelist != null || blacklist != null;
	}

	/** 任一表达式解析出错时返回 {@code true}。 */
	public boolean hasError() {
		return whitelistErrorKey != null || blacklistErrorKey != null;
	}

	/**
	 * 判断候选项是否被允许。
	 *
	 * @param candidate 待判定的候选项
	 * @return 通过过滤返回 {@code true}
	 */
	public boolean allows(TagCandidate candidate) {
		if (whitelist != null && !whitelist.test(candidate)) return false;
		if (blacklist != null && blacklist.test(candidate)) return false;
		return true;
	}
}
