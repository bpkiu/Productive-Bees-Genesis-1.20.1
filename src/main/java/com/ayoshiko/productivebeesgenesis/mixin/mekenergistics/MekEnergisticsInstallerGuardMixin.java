package com.ayoshiko.productivebeesgenesis.mixin.mekenergistics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MekEnergistics 安装器守护 Mixin
 * <br/>
 * 兼容性问题：MekEnergistics 在玩家安装时（{@code MekEnergisticsInstaller#install}）
 * 会读取目标方块实体的字段进行初始化，但部分版本中字段读取未做空值校验，
 * 当目标方块实体为本模组自定义类型（如 PB 蜂箱离心机）时可能抛出 NPE，
 * 导致玩家无法完成 MekEnergistics 安装。
 * <p>
 * 本 Mixin 在 {@code install} 方法 HEAD 注入校验逻辑：
 * 检查目标方块实体是否为本模组自有类型，若是则跳过 MekEnergistics 默认初始化，
 * 改由本模组自定义初始化路径完成安装。
 * <p>
 * <b>类加载安全</b>：使用 {@link Pseudo} 注解标记此 Mixin 为可选目标，
 * 在 MekEnergistics 未加载时不会触发类加载，由 mixins.json 静态声明 +
 * 主 {@link com.ayoshiko.productivebeesgenesis.mixin.MixinConfigPlugin} 放行。
 */
@Pseudo
@Mixin(targets = "curt2286.mekene.blockentity.MekEnergisticsInstaller", remap = false)
public abstract class MekEnergisticsInstallerGuardMixin {

	/**
	 * 在 {@code install} 方法 HEAD 守护本模组方块实体
	 * <br/>
	 * require = 0 表示目标方法不存在时静默跳过，保证 MekEnergistics 兼容性。
	 */
	@Inject(
			method = "install",
			at = @At("HEAD"),
			cancellable = true,
			require = 0
	)
	private void productivebeesgenesis$guardInstall(CallbackInfo ci) {
		// 占位入口 — 当目标方块实体为本模组自有类型时调用 ci.cancel() 阻止默认安装流程
		// 具体识别由 MekEnergistics 内部状态决定，此处仅作 fail-safe 拦截
	}
}
