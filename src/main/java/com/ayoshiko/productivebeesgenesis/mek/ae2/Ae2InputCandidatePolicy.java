package com.ayoshiko.productivebeesgenesis.mek.ae2;

import appeng.api.stacks.AEItemKey;
import net.minecraft.world.level.Level;

/**
 * AE2 输入候选策略 — 分类 AE2 网络中的物品是否为有效的输入候选。
 * <br/>
 * 根据物品是否为熔炼配方输入、是否被标签过滤器允许来分类，为输入拉取路径提供
 * 一致的候选判定逻辑，避免分散在拉取器中的条件分支。
 * <p>
 * <b>分类规则</b>：
 * <ul>
 *   <li>key 为 null → {@link CandidateKind#NONE}</li>
 *   <li>标签过滤器激活时仅放行通过门控的熔炼输入 → {@link CandidateKind#SMELTING}</li>
 *   <li>无标签过滤器时熔炼输入 → {@link CandidateKind#SMELTING}，其他 → {@link CandidateKind#NORMAL}</li>
 * </ul>
 */
final class Ae2InputCandidatePolicy {

	/** 候选种类枚举 */
	enum CandidateKind {
		/** 非候选 */
		NONE,
		/** 普通候选（非熔炼输入） */
		NORMAL,
		/** 熔炼输入候选 */
		SMELTING
	}

	/**
	 * 熔炼标签门控函数式接口 — 用于判定 key 是否通过当前标签过滤器。
	 * <br/>
	 * 抽象为接口便于测试替身与不同的标签过滤实现注入。
	 */
	@FunctionalInterface
	interface SmeltingTagGate {
		boolean allows(AEItemKey key);
	}

	private Ae2InputCandidatePolicy() {
	}

	/**
	 * 不带门控的分类重载 — 标签过滤器激活时一律拒绝非熔炼输入。
	 *
	 * @param level          世界实例
	 * @param key            待分类的 AE 物品键
	 * @param tagFilterActive 标签过滤器是否激活
	 * @param smeltingCache  熔炼输入缓存
	 * @return 候选种类
	 */
	static CandidateKind classify(Level level, AEItemKey key, boolean tagFilterActive,
			Ae2SmeltingInputCache smeltingCache) {
		return classify(level, key, tagFilterActive, smeltingCache, null);
	}

	/**
	 * 完整分类 — 标签过滤器激活时通过门控判定熔炼输入是否可放行。
	 *
	 * @param level          世界实例
	 * @param key            待分类的 AE 物品键
	 * @param tagFilterActive 标签过滤器是否激活
	 * @param smeltingCache  熔炼输入缓存
	 * @param smeltingGate   熔炼标签门控（可为 null，等同于无门控）
	 * @return 候选种类
	 */
	static CandidateKind classify(Level level, AEItemKey key, boolean tagFilterActive,
			Ae2SmeltingInputCache smeltingCache, SmeltingTagGate smeltingGate) {
		if (key == null) return CandidateKind.NONE;
		if (tagFilterActive) {
			// 标签过滤器激活时仅放行通过门控的熔炼输入
			if (smeltingCache.contains(level, key)) {
				if (smeltingGate != null && !smeltingGate.allows(key)) return CandidateKind.NONE;
				return CandidateKind.SMELTING;
			}
			return CandidateKind.NONE;
		}
		// 无标签过滤器时所有物品均为候选；熔炼输入单独标记以便排序优先
		if (smeltingCache.contains(level, key)) {
			return CandidateKind.SMELTING;
		}
		return CandidateKind.NORMAL;
	}
}
