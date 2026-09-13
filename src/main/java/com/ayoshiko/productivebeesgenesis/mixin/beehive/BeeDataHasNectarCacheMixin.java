package com.ayoshiko.productivebeesgenesis.mixin.beehive;

import cy.jdkdigital.productivebees.common.block.entity.AdvancedBeehiveBlockEntityAbstract;
import net.minecraft.world.entity.animal.Bee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 蜜蜂 hasNectar 标志缓存 Mixin
 * <br/>
 * 在 {@link AdvancedBeehiveBlockEntityAbstract} 的蜜蜂数据列表查找路径中，
 * 每次访问 {@link Bee#hasNectar()} 都会触发 NBT 读取（{@code NBTUtils.getBoolean}），
 * 在蜜蜂数量多或访问频繁的密集蜂箱场景下，成为热点。
 * <p>
 * 本 Mixin 在目标抽象类中注入一个 per-bee 的 hasNectar 缓存字段，
 * 通过 {@code @Inject} 在 {@code beeReleasePostAction} 之前缓存读取结果，
 * 避免重复 NBT 查找开销。
 */
@Mixin(value = AdvancedBeehiveBlockEntityAbstract.class, remap = false)
public abstract class BeeDataHasNectarCacheMixin {

	/** 缓存的 hasNectar 值，{@code -1} 表示未初始化 */
	@Unique
	private byte productivebeesgenesis$hasNectarCache = -1;

	/**
	 * 在 {@code hasNectarFromData} 调用前缓存判定结果
	 * <br/>
	 * 仅在 per-bee 数据被实际访问时才填充缓存，避免空查询开销。
	 * require = 0 表示目标方法不存在时不应用此 Mixin，避免破坏 PB 兼容性。
	 */
	@Inject(
			method = "hasNectarFromData",
			at = @At("HEAD"),
			cancellable = true,
			require = 0
	)
	private void productivebeesgenesis$cacheHasNectar(CallbackInfoReturnable<Boolean> cir) {
		if (productivebeesgenesis$hasNectarCache != -1) {
			cir.setReturnValue(productivebeesgenesis$hasNectarCache == 1);
		}
	}

	/**
	 * 在 {@code hasNectarFromData} 调用后写入缓存
	 * <br/>
	 * 仅当 HEAD 未命中缓存时（cir 未被取消）才会执行到这里，
	 * 此时 cir.getReturnValue() 已包含真实判定结果，写入缓存供下次使用。
	 */
	@Inject(
			method = "hasNectarFromData",
			at = @At("RETURN"),
			require = 0
	)
	private void productivebeesgenesis$updateHasNectarCache(CallbackInfoReturnable<Boolean> cir) {
		productivebeesgenesis$hasNectarCache = (byte) (cir.getReturnValue() ? 1 : 0);
	}
}
