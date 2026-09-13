package com.ayoshiko.productivebeesgenesis.util.tagfilter;

import java.util.Collection;
import java.util.List;

/**
 * 标签候选项，持有某个物品的物品 ID 与标签集合。
 *
 * <p>用于在表达式求值时判断该候选项是否匹配某个标签模式：只要任一标签
 * 命中模式即视为匹配。</p>
 */
public interface TagCandidate {

	/**
	 * 判断本候选项是否匹配给定模式。
	 *
	 * @param pattern 已编译的标签模式
	 * @return 任一标签命中模式时返回 {@code true}
	 */
	boolean matches(TagPattern pattern);

	/**
	 * 根据物品 ID 与标签集合构造候选项。
	 *
	 * @param itemId 物品 ID（仅用于标识，不参与匹配）
	 * @param tags   标签集合
	 * @return 不可变的候选项实例
	 */
	static TagCandidate of(String itemId, Collection<String> tags) {
		List<String> tagList = tags == null ? List.of() : List.copyOf(tags);
		return new Candidate(itemId, tagList);
	}

	/** 默认实现，以记录形式持有物品 ID 与不可变标签列表。 */
	record Candidate(String itemId, List<String> tags) implements TagCandidate {
		@Override
		public boolean matches(TagPattern pattern) {
			for (String tag : tags) {
				if (pattern.matches(tag)) return true;
			}
			return false;
		}
	}
}
