package com.ayoshiko.productivebeesgenesis.mek;

import mekanism.api.Upgrade;
import mekanism.api.math.MathUtils;
import mekanism.common.config.MekanismConfig;
import mekanism.common.tile.interfaces.IUpgradeTile;
import mekanism.common.util.MekanismUtils;

/** Pure formulas that keep Mekanism Extras creative and stack effects separate. */
final class MekExtrasUpgradeSemantics {

	private MekExtrasUpgradeSemantics() {
	}

	static int processingTicks(boolean creativeInstalled, int baseTime, double timeMultiplier) {
		return creativeInstalled ? 0 : Math.max(1, (int) Math.floor(baseTime * timeMultiplier));
	}

	static long energyPerTick(boolean creativeInstalled, long normalEnergyPerTick) {
		return creativeInstalled ? 0L : Math.max(0L, normalEnergyPerTick);
	}

	static int operationsPerTick(boolean creativeInstalled, int stackOperations,
			int speedAdjustedOperations) {
		return creativeInstalled ? Math.max(1, stackOperations) : Math.max(1, speedAdjustedOperations);
	}

	/**
	 * 1.20.1 内联版 {@code MekanismUtils.getOperationsPerTick}（1.20.1 无此方法）。
	 * 公式对齐 Mekanism 1.21 源码：
	 * {@code ticksD = defTicks * maxUpgradeMultiplier^(-fractionUpgrades(tile, SPEED))}；
	 * {@code ticksD >= 1} 时返回 defaultOperations，否则返回
	 * {@code MathUtils.clampToInt(max(1, 1/ticksD) * defaultOperations)}。
	 */
	static int speedAdjustedOperations(IUpgradeTile tile, int defTicks, int defaultOperations) {
		double ticksD = defTicks * Math.pow(MekanismConfig.general.maxUpgradeMultiplier.get(),
				-MekanismUtils.fractionUpgrades(tile, Upgrade.SPEED));
		if (ticksD >= 1) {
			return defaultOperations;
		}
		return MathUtils.clampToInt(Math.max(1, 1 / ticksD) * defaultOperations);
	}
}
