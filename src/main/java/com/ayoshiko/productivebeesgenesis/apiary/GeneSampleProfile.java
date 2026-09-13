package com.ayoshiko.productivebeesgenesis.apiary;

import cy.jdkdigital.productivebees.util.BeeAttribute;
import cy.jdkdigital.productivebees.util.BeeAttributes;
import net.minecraft.nbt.CompoundTag;

import java.util.Objects;

/**
 * 蜜蜂基因样本快照 — 一次性读取五项属性的整数值。
 * <p>
 * PB 1.20.1 将属性存为顶层 NBT 整数键（{@code bee_productivity} 等），
 * 本类在构造时一次性读取全部五项，后续查询无需重复访问 NBT。
 * <p>
 * 对应 1.21+ 版本的 {@code GeneSampleProfile}（使用 GeneValue / GeneAttribute），
 * 1.20.1 版本使用 {@link BeeAttribute}（值为 Integer 序数）。
 *
 * @param productivity      生产力等级 (0=normal, 1=medium, 2=high, 3=very_high)
 * @param endurance         耐力等级 (0=weak, 1=normal, 2=medium, 3=strong)
 * @param temper            性情等级 (0=passive, 1=normal, 2=hostile, 3=aggressive)
 * @param behavior          行为等级 (0=diurnal, 1=nocturnal, 2=metaturnal)
 * @param weatherTolerance  气候耐受力 (0=none, 1=rain, 2=any)
 */
public record GeneSampleProfile(
		int productivity,
		int endurance,
		int temper,
		int behavior,
		int weatherTolerance) {

	// ========== NBT 键名（PB 1.20.1 顶层整数键） ==========

	private static final String KEY_PRODUCTIVITY = "bee_productivity";
	private static final String KEY_ENDURANCE = "bee_endurance";
	private static final String KEY_TEMPER = "bee_temper";
	private static final String KEY_BEHAVIOR = "bee_behavior";
	private static final String KEY_WEATHER_TOLERANCE = "bee_weather_tolerance";

	/** 默认样本：所有属性均为最低等级。 */
	public static final GeneSampleProfile DEFAULT = new GeneSampleProfile(0, 1, 0, 0, 0);

	/**
	 * 从蜜蜂实体 NBT 一次性读取五项属性。
	 *
	 * @param beeData 蜜蜂实体 NBT；可为 null
	 * @return 属性快照；输入为 null 或无属性时返回 {@link #DEFAULT}
	 */
	public static GeneSampleProfile fromBeeData(CompoundTag beeData) {
		if (beeData == null) return DEFAULT;
		return new GeneSampleProfile(
				readValue(beeData, KEY_PRODUCTIVITY, BeeAttributes.PRODUCTIVITY, 0),
				readValue(beeData, KEY_ENDURANCE, BeeAttributes.ENDURANCE, 1),
				readValue(beeData, KEY_TEMPER, BeeAttributes.TEMPER, 0),
				readValue(beeData, KEY_BEHAVIOR, BeeAttributes.BEHAVIOR, 0),
				readValue(beeData, KEY_WEATHER_TOLERANCE, BeeAttributes.WEATHER_TOLERANCE, 0));
	}

	/**
	 * 按 PB {@link BeeAttribute} 查询对应属性值。
	 *
	 * @param attribute PB 属性常量（PRODUCTIVITY / ENDURANCE / TEMPER / BEHAVIOR / WEATHER_TOLERANCE）
	 * @return 属性整数值；不匹配时返回 0
	 */
	public int value(BeeAttribute<Integer> attribute) {
		if (attribute == BeeAttributes.PRODUCTIVITY) return productivity;
		if (attribute == BeeAttributes.ENDURANCE) return endurance;
		if (attribute == BeeAttributes.TEMPER) return temper;
		if (attribute == BeeAttributes.BEHAVIOR) return behavior;
		if (attribute == BeeAttributes.WEATHER_TOLERANCE) return weatherTolerance;
		return 0;
	}

	private static int readValue(CompoundTag beeData, String key, BeeAttribute<Integer> attribute, int defaultValue) {
		if (!beeData.contains(key)) return defaultValue;
		return beeData.getInt(key);
	}
}
