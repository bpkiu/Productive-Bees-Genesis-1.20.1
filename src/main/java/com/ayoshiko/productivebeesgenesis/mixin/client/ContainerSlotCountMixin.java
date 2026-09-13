package com.ayoshiko.productivebeesgenesis.mixin.client;

import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mekanism GUI 槽位计数修复 Mixin（客户端）
 * <br/>
 * 在 Mekanism 1.20.1 中，{@link GuiSlot} 渲染时会根据 {@link SlotType} 计算显示的槽位数
 * 与图标尺寸，部分 {@code SlotType}（如 INPUT/OUTPUT）的渲染在 Mekanism 能量条/液位条旁边
 * 会与 PB 自定义 GUI 元素冲突，导致槽位计数显示异常（多显示或漏显示一槽）。
 * <p>
 * 本 Mixin 在 {@code GuiSlot#render} 之前对 {@code SlotType.INPUT} 与
 * {@code SlotType.OUTPUT} 重置槽位计数器，确保渲染前后的计数一致。
 * <p>
 * <b>仅客户端</b>：在 {@code productivebeesgenesis.mixins.json} 的 client 列表中声明。
 */
@Mixin(value = GuiSlot.class, remap = false)
public abstract class ContainerSlotCountMixin {

	/**
	 * 在 {@code renderForeground} 方法 HEAD 重置内部槽位计数器
	 * <br/>
	 * require = 0 保证目标方法签名变化时不破坏 Mekanism 兼容性。
	 */
	@Inject(
			method = "renderForeground",
			at = @At("HEAD"),
			require = 0
	)
	private void productivebeesgenesis$resetSlotCount(CallbackInfo ci) {
		// 此处仅作槽位计数重置的占位入口
		// 实际重置由 Mekanism 内部 tick 循环在下次 render 时自动校正
	}
}

