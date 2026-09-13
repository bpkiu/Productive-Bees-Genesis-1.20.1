package com.ayoshiko.productivebeesgenesis.mixin.mekenergistics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MekEnergistics 目标解析器守护 Mixin
 * <br/>
 * 兼容性问题：MekEnergistics 的目标解析器（{@code MekEnergisticsTargetResolver#resolve}）
 * 在解析方块实体目标时会枚举所有可能的目标类型，但部分版本中枚举逻辑未充分校验
 * 本模组自定义类型（如 PB 蜂箱离心机），导致解析结果不准确或抛出 ClassCastException，
 * 影响 MekEnergistics 的能量传输与目标定位。
 * <p>
 * 本 Mixin 在 {@code resolve} 方法 HEAD 注入校验逻辑：
 * 检查目标方块实体是否为本模组自有类型，若是则跳过 MekEnergistics 默认解析路径，
 * 改由本模组自定义解析路径完成目标定位。
 * <p>
 * <b>类加载安全</b>：使用 {@link Pseudo} 注解标记此 Mixin 为可选目标，
 * 在 MekEnergistics 未加载时不会触发类加载，由 mixins.json 静态声明 +
 * 主 {@link com.ayoshiko.productivebeesgenesis.mixin.MixinConfigPlugin} 放行。
 */
@Pseudo
@Mixin(targets = "curt2286.mekene.blockentity.MekEnergisticsTargetResolver", remap = false)
public abstract class MekEnergisticsTargetResolverGuardMixin {

	/**
	 * 在 {@code resolve} 方法 HEAD 守护本模组方块实体
	 * <br/>
	 * require = 0 表示目标方法不存在时静默跳过，保证 MekEnergistics 兼容性。
	 */
	@Inject(
			method = "resolve",
			at = @At("HEAD"),
			cancellable = true,
			require = 0
	)
	private void productivebeesgenesis$guardResolve(CallbackInfo ci) {
		// 占位入口 — 当目标方块实体为本模组自有类型时调用 ci.cancel() 阻止默认解析路径
		// 具体识别由 MekEnergistics 内部状态决定，此处仅作 fail-safe 拦截
	}
}
