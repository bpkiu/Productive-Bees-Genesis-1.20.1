package com.ayoshiko.productivebeesgenesis.apiary;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * Productive Bees 生产力基因的 NBT 解析与产量换算。
 * <p>
 * PB 1.20.1 将属性直接存为顶层 NBT 整数键：
 * {@code bee_productivity} (0=normal, 1=medium, 2=high, 3=very_high)。
 * 原版高级蜂箱在每个配方产物栈生成后应用基因加成，因此该公式按单个原始产物栈计算。
 */
public final class BeeProductivityGene {

	/** 普通生产力等级。 */
	public static final int NORMAL = 0;

	/** 最高生产力等级。 */
	public static final int VERY_HIGH = 3;

	/** PB 1.20.1 顶层 NBT 键：蜜蜂生产力等级（整数 0-3）。 */
	private static final String PRODUCTIVITY_KEY = "bee_productivity";

	private BeeProductivityGene() {
	}

	/**
	 * 从蜜蜂实体或 configurable_honeycomb ItemStack 的 NBT 读取 PB 生产力等级。
	 *
	 * @param beeData 蜜蜂实体 NBT；ItemStack 场景需传入 {@code ItemStack.getOrCreateTag()}
	 * @return 生产力等级 0 到 3；属性缺失或损坏时返回 0
	 */
	public static int readLevel(@Nullable CompoundTag beeData) {
		if (beeData == null) return NORMAL;
		// PB 1.20.1：直接从顶层 NBT 读取整数值
		if (!beeData.contains(PRODUCTIVITY_KEY)) return NORMAL;
		int level = beeData.getInt(PRODUCTIVITY_KEY);
		return Math.max(NORMAL, Math.min(VERY_HIGH, level));
	}

	/**
	 * 按 PB {@code AdvancedBeehiveBlockEntity#beeReleasePostAction} 的公式调整单个产物栈数量。
	 *
	 * @param baseCount         配方本次生成的原始栈数量
	 * @param productivityLevel 生产力等级 0 到 3
	 * @return 应用基因后的数量，溢出时截断到 {@link Integer#MAX_VALUE}
	 */
	public static int adjustStackCount(int baseCount, int productivityLevel) {
		if (baseCount <= 0) return 0;
		int level = Math.max(NORMAL, Math.min(VERY_HIGH, productivityLevel));
		if (level == NORMAL) return baseCount;

		long adjusted;
		if (baseCount == 1) {
			adjusted = 1L + level;
		} else {
			float modifier = (1.0F / (level + 2.0F) + (level + 1.0F) / 2.0F) * baseCount;
			adjusted = (long) baseCount + Math.round(modifier);
		}
		return adjusted >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) adjusted;
	}
}
