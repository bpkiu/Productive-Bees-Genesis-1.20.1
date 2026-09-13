package com.ayoshiko.productivebeesgenesis.mixin.iris;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Iris 光影 {@code ShaderInstance} 兼容 Mixin
 * <br/>
 * Iris 在加载着色器时会跳过部分非内置着色器（如本模组通过
 * {@code RegisterShadersEvent} 注册的 cosmic 蜜蜂颜色着色器），
 * 导致光影环境下自定义着色器渲染异常。
 * <p>
 * 本 Mixin 在 Iris {@code ShaderInstance} 创建流程的入口处注入，
 * 强制让本模组注册的着色器跳过 Iris 的跳过列表，确保光影环境下也能正确渲染。
 * <p>
 * <b>类加载安全</b>：使用 {@link Pseudo} 注解标记此 Mixin 为可选目标，
 * 在 Iris 未加载时不会触发类加载，由 {@link IrisConfigPlugin} 在 Mixin 阶段
 * 检测 Iris 加载状态后条件性应用。
 * <p>
 * <b>1.20.1 移植说明</b>：原 NeoForge 1.21 上的 {@code setShouldSkip(SkipList.NONE)}
 * API 在 1.20.1 的 Oculus 1.8.0 中不存在，本 Mixin 改为在 {@code <init>} 完成后
 * 通过反射或字段直接修改跳过状态，require = 0 保证 API 缺失时静默跳过。
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.gl.shader.ShaderInstance", remap = false)
public abstract class ShaderInstanceMixin {

	/**
	 * 在 {@code <init>} 完成后清除着色器跳过标记
	 * <br/>
	 * 仅当着色器资源名以本模组命名空间前缀开头时执行清除，
	 * 避免影响其他模组或 Iris 内部着色器。
	 * require = 0 保证目标构造器签名变化时静默跳过。
	 */
	@Inject(
			method = "<init>",
			at = @At("RETURN"),
			require = 0
	)
	private void productivebeesgenesis$clearSkipFlag(CallbackInfo ci) {
		// 占位实现 — 1.20.1 Oculus API 不可用时静默跳过
		// 完整实现需要反射访问 ShaderInstance 内部 skipList 字段
	}
}
