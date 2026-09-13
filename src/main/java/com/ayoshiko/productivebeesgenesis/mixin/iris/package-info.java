/**
	 * Iris 兼容 Mixin 包
	 * <br/>
	 * Iris 光影兼容相关的 Mixin 和配置插件：
	 * <ol>
	 *   <li>{@code IrisConfigPlugin} — Iris Mixin 条件加载插件</li>
	 * </ol>
	 * <p>
	 * Forge 1.20.1 移植说明：原 {@code ShaderInstanceMixin}（调用 {@code setShouldSkip(SkipList.NONE)}
	 * 强制 Iris 不跳过本模组着色器）已移除 — 该 API 来自 NeoForge 1.21 上的 Iris 1.8.8 内部接口，
	 * 在 1.20.1 的 Oculus 1.8.0 中不存在（其 ShaderInstanceInterface 仅含 iris$createExtraShaders，
	 * 且无 SkipList）。1.20.1 下自定义核心着色器经 RegisterShadersEvent 注册即为受支持路径，
	 * 无需对抗跳过列表；光影包下的 cosmic 渲染兼容性作为已知限制记录。
	 */
@ParametersAreNonnullByDefault
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
package com.ayoshiko.productivebeesgenesis.mixin.iris;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;
