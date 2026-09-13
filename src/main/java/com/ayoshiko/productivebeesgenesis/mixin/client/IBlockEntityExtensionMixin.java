package com.ayoshiko.productivebeesgenesis.mixin.client;

import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端 BlockEntity 扩展能力 Mixin
 * <br/>
 * 原设计为接口 Mixin（{@code @Mixin(targets = "...IBlockEntityExtension")}），
 * 但 Mixin 0.8.5 不支持在接口上 {@code @Inject}，故改为以类 Mixin 形式
 * 注入到 {@link BlockEntity} 基类，使用 {@link Unique} 字段附加扩展能力。
 * <p>
 * 扩展能力：为 PB 自定义方块实体提供客户端渲染缓存字段，避免每帧重复计算
 * （如蜜蜂颜色、模拟状态等）。
 * <p>
 * <b>仅客户端</b>：在 {@code productivebeesgenesis.mixins.json} 的 client 列表中声明。
 */
@Mixin(BlockEntity.class)
public abstract class IBlockEntityExtensionMixin {

	/** 客户端渲染缓存字段 — 上次缓存的渲染 tick，{@code -1L} 表示未初始化 */
	@Unique
	private long productivebeesgenesis$clientRenderCacheTick = -1L;

	/** 客户端渲染缓存字段 — 缓存的渲染值（颜色 / 状态等） */
	@Unique
	private int productivebeesgenesis$clientRenderCacheValue;

	/**
	 * 获取客户端渲染缓存的上次更新 tick
	 * <br/>
	 * 由 PB 客户端渲染器调用，判断是否需要重新计算缓存。
	 */
	@Unique
	public long productivebeesgenesis$getClientRenderCacheTick() {
		return productivebeesgenesis$clientRenderCacheTick;
	}

	/**
	 * 设置客户端渲染缓存的上次更新 tick 与缓存值
	 * <br/>
	 * 由 PB 客户端渲染器在重新计算后调用，写入缓存以供下次使用。
	 */
	@Unique
	public void productivebeesgenesis$setClientRenderCache(long tick, int value) {
		this.productivebeesgenesis$clientRenderCacheTick = tick;
		this.productivebeesgenesis$clientRenderCacheValue = value;
	}

	/**
	 * 获取客户端渲染缓存的值
	 * <br/>
	 * 由 PB 客户端渲染器调用，配合 {@code getClientRenderCacheTick} 判断缓存是否有效。
	 */
	@Unique
	public int productivebeesgenesis$getClientRenderCacheValue() {
		return productivebeesgenesis$clientRenderCacheValue;
	}

	/**
	 * 在 {@code setLevel} 时清除客户端渲染缓存
	 * <br/>
	 * 方块实体被移动到新的 level 时（如维度切换、区块重建），
	 * 缓存 tick 与 level 不再对应，必须清空避免误用旧值。
	 * require = 0 保证目标方法签名变化时不破坏兼容性。
	 */
	@Inject(
			method = "setLevel",
			at = @At("HEAD"),
			require = 0
	)
	private void productivebeesgenesis$clearRenderCacheOnLevelChange(CallbackInfo ci) {
		this.productivebeesgenesis$clientRenderCacheTick = -1L;
		this.productivebeesgenesis$clientRenderCacheValue = 0;
	}
}
