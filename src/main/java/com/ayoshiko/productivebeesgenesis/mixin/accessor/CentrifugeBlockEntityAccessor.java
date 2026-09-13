package com.ayoshiko.productivebeesgenesis.mixin.accessor;

import cy.jdkdigital.productivebees.common.block.entity.CentrifugeBlockEntity;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
	 * CentrifugeBlockEntity 访问器：暴露 private 字段供外部访问
	 * <br/>
	 * PB 1.20.1：{@code inventoryHandler} 为 {@code private LazyOptional<IItemHandlerModifiable>}
	 * （1.21 版本为可直接访问的 handler 实例，类型不同）。
	 * <br/>
	 * <b>1.21 差异</b>：PB 12.6.0 无 1.21 的 {@code getProductivityModifier()} 方法，
	 * 产量并行倍率改由 {@code CentrifugeMixinHelper#getProductivityModifier} 按
	 * {@code getUpgradeCount(Item)} 计算（PRODUCTIVITY/2/3/4 每件分别贡献 4/8/16/32），
	 * 与工厂路径 {@code MekCentrifugePbUpgradeHandler#computeProductivityParallelModifier} 语义一致。
	 */
@Mixin(value = CentrifugeBlockEntity.class, remap = false)
public interface CentrifugeBlockEntityAccessor {
	@Accessor("inventoryHandler")
	LazyOptional<IItemHandlerModifiable> productivebeesgenesis$getInventoryHandler();
}
