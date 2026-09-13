package com.ayoshiko.productivebeesgenesis.util.tagfilter;

/**
 * 标签模式，支持通配符匹配。
 *
 * <p>模式使用 {@code *} 作为通配符。例如 {@code minecraft:*} 匹配所有以
 * {@code minecraft:} 开头的标签；不带通配符的模式（如 {@code forge:bees}）
 * 表示精确匹配。模式在构造时编译为分段数组，匹配时按段顺序查找。</p>
 */
public final class TagPattern {

	/** 字面量最大长度。 */
	public static final int MAX_LITERAL_LENGTH = 128;

	/** 单个模式允许的最大通配符数量。 */
	public static final int MAX_WILDCARDS = 8;

	private final String literal;
	private final String[] segments;
	private final boolean anchoredStart;
	private final boolean anchoredEnd;

	private TagPattern(String literal, String[] segments, boolean anchoredStart, boolean anchoredEnd) {
		this.literal = literal;
		this.segments = segments;
		this.anchoredStart = anchoredStart;
		this.anchoredEnd = anchoredEnd;
	}

	/**
	 * 编译输入字符串为标签模式。
	 *
	 * @param input 模式文本，例如 {@code minecraft:*}
	 * @return 编译后的模式
	 * @throws IllegalArgumentException 输入为空、过长或通配符过多时抛出
	 */
	public static TagPattern compile(String input) {
		if (input == null) throw new IllegalArgumentException("input is null");
		String trimmed = input.trim();
		if (trimmed.isEmpty()) throw new IllegalArgumentException("input is empty");
		if (trimmed.length() > MAX_LITERAL_LENGTH) throw new IllegalArgumentException("input too long");
		int wildcardCount = 0;
		for (int i = 0; i < trimmed.length(); i++) {
			if (trimmed.charAt(i) == '*') wildcardCount++;
		}
		if (wildcardCount > MAX_WILDCARDS) throw new IllegalArgumentException("too many wildcards");
		String[] segments = splitOnWildcard(trimmed);
		boolean anchoredStart = !trimmed.startsWith("*");
		boolean anchoredEnd = !trimmed.endsWith("*");
		return new TagPattern(trimmed, segments, anchoredStart, anchoredEnd);
	}

	/** 按 {@code *} 拆分字面量，并丢弃首尾因通配符产生的空段。 */
	private static String[] splitOnWildcard(String s) {
		String[] parts = s.split("\\*", -1);
		int start = s.startsWith("*") ? 1 : 0;
		int end = s.endsWith("*") ? parts.length - 1 : parts.length;
		if (end <= start) return new String[0];
		String[] result = new String[end - start];
		System.arraycopy(parts, start, result, 0, end - start);
		return result;
	}

	/** 返回模式原始字面量。 */
	public String literal() {
		return literal;
	}

	/** 当模式不含通配符时返回 {@code true}。 */
	public boolean isExact() {
		return anchoredStart && anchoredEnd && segments.length == 1;
	}

	/**
	 * 判断给定标签是否匹配本模式。
	 *
	 * @param tag 待匹配的标签
	 * @return 匹配返回 {@code true}
	 */
	public boolean matches(String tag) {
		if (tag == null) return false;
		if (isExact()) return tag.equals(literal);
		if (segments.length == 0) return true;
		int pos = 0;
		for (int i = 0; i < segments.length; i++) {
			String segment = segments[i];
			if (i == 0 && anchoredStart) {
				if (!tag.startsWith(segment, 0)) return false;
				pos = segment.length();
			} else {
				int found = tag.indexOf(segment, pos);
				if (found < 0) return false;
				pos = found + segment.length();
			}
		}
		if (anchoredEnd) {
			String last = segments[segments.length - 1];
			if (!tag.endsWith(last)) return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return "TagPattern{literal='" + literal + "'}";
	}
}
