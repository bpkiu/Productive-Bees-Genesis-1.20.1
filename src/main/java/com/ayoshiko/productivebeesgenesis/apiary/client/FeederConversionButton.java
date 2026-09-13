package com.ayoshiko.productivebeesgenesis.apiary.client;

import com.ayoshiko.productivebeesgenesis.network.ModPayloads;
import com.ayoshiko.productivebeesgenesis.network.ToggleApiaryFeederConversionPayload;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.button.MekanismButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * 蜜蜂转化切换按钮 — 继承 MekanismButton 复用渲染管线
 * <br/>
 * DEFAULT 灰色背景，点击后向服务端发送 {@link ToggleApiaryFeederConversionPayload}，
 * 切换该蜂箱饲养板的蜜蜂转化功能。
 * <p>
 * 转化功能允许蜂箱将饲养板中的物品/方块作为转化原料，将蜜蜂转化为其他类型。
 * 该按钮携带蜂箱方块坐标，由服务端定位对应方块实体并执行切换。
 */
public class FeederConversionButton extends MekanismButton {

	/**
	 * 构造蜜蜂转化切换按钮
	 *
	 * @param gui    所属 GUI 包装器
	 * @param x      按钮 X 坐标（相对 GUI 左上角）
	 * @param y      按钮 Y 坐标（相对 GUI 左上角）
	 * @param width  按钮宽度
	 * @param height 按钮高度
	 * @param pos    蜂箱方块坐标，用于定位服务端方块实体
	 */
	public FeederConversionButton(IGuiWrapper gui, int x, int y, int width, int height, BlockPos pos) {
		super(gui, x, y, width, height, Component.literal("\u2295"), () ->
				ModPayloads.CHANNEL.sendToServer(new ToggleApiaryFeederConversionPayload(pos)),
				(element, graphics, mouseX, mouseY) -> {});
		setButtonBackground(GuiElement.ButtonBackground.DEFAULT);
		setTooltip(Tooltip.create(Component.literal("切换蜜蜂转化")));
	}
}
