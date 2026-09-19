package com.ayoshiko.productivebeesgenesis.inventory;

import com.ayoshiko.productivebeesgenesis.config.FactoryTierConfigService;
import com.ayoshiko.productivebeesgenesis.config.FactoryTierKey;

import java.util.function.IntSupplier;

/**
 * 按工厂等级读取离心机流体罐容量倍率。
 * <p>
 * <b>1.0.7 重构</b>：逐等级配置字段改为 {@link FactoryTierConfigService} 会话级快照读取，
 * 配置热重载不影响本会话已构建的流体罐。
 */
public final class CentrifugeFluidTankMultipliers {

	private CentrifugeFluidTankMultipliers() {
	}

	private static IntSupplier forTier(FactoryTierKey tier) {
		return () -> FactoryTierConfigService.current().centrifugeFluidTank(tier);
	}

	public static IntSupplier forVanillaFactory(int ordinal) {
		return forTier(FactoryTierKey.vanillaFactory(ordinal));
	}

	public static IntSupplier forMEFactory(int ordinal) {
		return forTier(FactoryTierKey.mekanismExtrasFactory(ordinal));
	}

	public static IntSupplier forEMEFactory(int ordinal) {
		return forTier(FactoryTierKey.evolvedMekanismExtrasFactory(ordinal));
	}

	/** ordinal 0-4 对应 EM 的 OVERCLOCKED 到 CREATIVE。 */
	public static IntSupplier forEMFactory(int ordinal) {
		return forTier(FactoryTierKey.evolvedMekanismFactory(ordinal));
	}
}
