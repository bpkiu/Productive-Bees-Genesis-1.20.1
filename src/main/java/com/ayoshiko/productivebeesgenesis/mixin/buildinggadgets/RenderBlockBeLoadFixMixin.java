package com.ayoshiko.productivebeesgenesis.mixin.buildinggadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Building Gadgets 模组 RenderBlock 方块实体加载修复 Mixin
 * <br/>
 * 兼容性问题：Building Gadgets 在渲染假方块（{@code RenderBlock}）时会创建
 * 临时的方块实体，但部分版本中加载流程未充分校验目标 {@link BlockEntity} 类型，
 * 在与 Productive Bees 自定义方块实体（如 AdvancedBeehiveBlockEntity）共同存在时
 * 可能抛出 ClassCastException 或 NPE，导致玩家放置/删除 Building Gadgets 假方块时崩溃。
 * <p>
 * 本 Mixin 在 {@code RenderBlockEntity#load}（或同等加载入口）HEAD 注入：
 * 检查目标 BlockState 对应的 BlockEntity 类型是否为 PB 自有类型，
 * 若是则跳过 Building Gadgets 自带的假方块实体加载逻辑，避免类型不匹配崩溃。
 * <p>
 * <b>类加载安全</b>：使用 {@link Pseudo} 注解标记此 Mixin 为可选目标，
 * 在 Building Gadgets 未加载时不会触发类加载，由 mixins.json 静态声明 +
 * 主 {@link com.ayoshiko.productivebeesgenesis.mixin.MixinConfigPlugin} 放行。
 */
@Pseudo
@Mixin(targets = "com.direwolf40.buildinggadgets.common.blocks.RenderBlockEntity", remap = false)
public abstract class RenderBlockBeLoadFixMixin {

	/**
	 * 在 {@code load} 方法 HEAD 注入校验逻辑
	 * <br/>
	 * require = 0 表示目标方法/目标类不存在时静默跳过，保证兼容性。
	 */
	@Inject(
			method = "load",
			at = @At("HEAD"),
			cancellable = true,
			require = 0
	)
	private void productivebeesgenesis$skipPbBlockEntityLoad(
			Level level, BlockPos pos, BlockState state, CallbackInfo ci) {
		// 守卫：仅当目标 BlockState 对应方块实体类型为本模组自有类型时跳过加载
		// 具体识别逻辑由 Building Gadgets 内部状态决定，此处仅作 fail-safe 拦截
		BlockEntity existing = level.getBlockEntity(pos);
		if (existing != null && existing.getClass().getName()
				.startsWith("cy.jdkdigital.productivebees.")) {
			ci.cancel();
		}
	}
}
