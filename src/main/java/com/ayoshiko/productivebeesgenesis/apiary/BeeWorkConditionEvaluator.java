package com.ayoshiko.productivebeesgenesis.apiary;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * 解析资源蜜蜂的工作相关基因，并按当前昼夜与天气判断机械蜂箱是否应暂停生产。
 * <p>
 * PB 1.20.1 将行为和天气耐受直接存为顶层 NBT 整数键：
 * {@code bee_behavior} (0=diurnal, 1=nocturnal, 2=metaturnal)，
 * {@code bee_weather_tolerance} (0=none, 1=rain, 2=any)。
 * 蜜蜂 NBT 只应在槽位内容变化时交给本类解析；tick 热路径复用 {@link WorkTraits} 快照。
 */
final class BeeWorkConditionEvaluator {

	/** PB 1.20.1 顶层 NBT 键：蜜蜂昼夜行为（整数 0-2）。 */
	private static final String BEHAVIOR_KEY = "bee_behavior";
	/** PB 1.20.1 顶层 NBT 键：蜜蜂天气耐受（整数 0-2）。 */
	private static final String WEATHER_TOLERANCE_KEY = "bee_weather_tolerance";

	private BeeWorkConditionEvaluator() {
	}

	/**
	 * 从蜜蜂 NBT 读取工作相关基因。缺失或损坏的属性按 PB 默认基因处理。
	 *
	 * @param beeData 蜜蜂实体 NBT
	 * @return 可缓存的工作基因快照
	 */
	static WorkTraits readTraits(@Nullable CompoundTag beeData) {
		if (beeData == null) return WorkTraits.DEFAULT;
		// PB 1.20.1：直接从顶层 NBT 读取整数值
		int behaviorOrdinal = beeData.contains(BEHAVIOR_KEY) ? beeData.getInt(BEHAVIOR_KEY) : 0;
		int weatherOrdinal = beeData.contains(WEATHER_TOLERANCE_KEY) ? beeData.getInt(WEATHER_TOLERANCE_KEY) : 0;
		return new WorkTraits(
				Behavior.fromOrdinal(behaviorOrdinal),
				WeatherTolerance.fromOrdinal(weatherOrdinal));
	}

	/**
	 * 返回阻止蜜蜂工作的状态；返回 {@code null} 表示当前环境允许工作。
	 *
	 * @param traits          缓存的工作基因
	 * @param fixedTime       维度是否固定时间；与 PB 蜂箱一致，固定时间维度忽略环境限制
	 * @param night           当前是否为夜晚
	 * @param raining         当前是否下雨
	 * @param thundering      当前是否雷暴
	 * @return 对应停工原因，允许工作时返回 null
	 */
	@Nullable
	static BeeState blockingState(
			WorkTraits traits,
			boolean fixedTime,
			boolean night,
			boolean raining,
			boolean thundering) {
		if (fixedTime) return null;
		WorkTraits safeTraits = traits == null ? WorkTraits.DEFAULT : traits;
		if ((night && safeTraits.behavior() == Behavior.DIURNAL)
				|| (!night && safeTraits.behavior() == Behavior.NOCTURNAL)) {
			return BeeState.WAITING_DAY_CYCLE;
		}
		if (thundering && safeTraits.weatherTolerance() != WeatherTolerance.ANY) {
			return BeeState.WAITING_THUNDER;
		}
		if (raining && safeTraits.weatherTolerance() == WeatherTolerance.NONE) {
			return BeeState.WAITING_RAIN;
		}
		return null;
	}

	/** 可缓存的行为与天气耐受基因快照。 */
	record WorkTraits(Behavior behavior, WeatherTolerance weatherTolerance) {
		private static final WorkTraits DEFAULT =
				new WorkTraits(Behavior.DIURNAL, WeatherTolerance.NONE);
	}

	/** 资源蜜蜂昼夜行为基因（PB 1.20.1 整数值映射）。 */
	enum Behavior {
		DIURNAL,     // 0: 白天工作
		NOCTURNAL,   // 1: 夜晚工作
		METATURNAL;  // 2: 全天工作

		private static Behavior fromOrdinal(int ordinal) {
			return switch (ordinal) {
				case 1 -> NOCTURNAL;
				case 2 -> METATURNAL;
				default -> DIURNAL;
			};
		}
	}

	/** 资源蜜蜂天气耐受基因（PB 1.20.1 整数值映射）。 */
	enum WeatherTolerance {
		NONE,   // 0: 不能在雨天工作
		RAIN,   // 1: 可以在雨天工作
		ANY;    // 2: 任何天气都可工作

		private static WeatherTolerance fromOrdinal(int ordinal) {
			return switch (ordinal) {
				case 1 -> RAIN;
				case 2 -> ANY;
				default -> NONE;
			};
		}
	}
}
