package com.ayoshiko.productivebeesgenesis.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
	 * 服务端配置中间页（Forge 1.20.1 适配）
	 * <br/>
	 * 与原 NeoForge 配置节屏幕（{@code ConfigurationSectionScreen}，Forge 中不存在）
	 * 保持一致的 {@link OptionsSubScreen} 风格：左侧标签、右侧操作按钮、底部“完成”按钮。
	 * <p>
	 * 该页面同时提供两个入口：
	 * <ul>
	 *   <li>“万象创世过滤” — 打开自定义 {@link FilterListScreen}，支持搜索、多选、滚动、全选</li>
	 *   <li>“其他服务端配置” — 打开基于 NeoForge 的服务端分组列表；平衡性预设使用列表选择器</li>
	 * </ul>
	 */
public final class ServerConfigScreen extends OptionsSubScreen {

	private static final String TITLE_KEY =
			"productivebeesgenesis.configuration.section.productivebeesgenesis.server.toml.title";

	public ServerConfigScreen(Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable(TITLE_KEY));
	}

	// NeoForge 原生配置节屏幕的按钮/标签后缀（如“彩虹特效...”）
	private static final String SECTION_SUFFIX_KEY = "neoforge.configuration.uitext.section";

	// 1.20.1 的 OptionsSubScreen 无 list/addOptions 框架（1.20.2+ 才引入），改为手动布局
	@Override
	protected void init() {
		int centerX = this.width / 2;
		int labelX = centerX - 155;
		int buttonX = centerX + 5;
		int rowWidth = 150;
		int rowY = this.height / 4;

		// 1. 万象创世过滤 — 自定义编辑器
		Component filterLabel = Component.translatable(SECTION_SUFFIX_KEY,
				Component.translatable("productivebeesgenesis.configuration.myriad_creations_filter"));
		StringWidget filterLabelWidget = new StringWidget(rowWidth, Button.DEFAULT_HEIGHT, filterLabel,
			font).alignLeft();
		filterLabelWidget.setX(labelX);
		filterLabelWidget.setY(rowY);
		filterLabelWidget.setTooltip(Tooltip.create(
				Component.translatable("productivebeesgenesis.configuration.myriad_creations_filter.tooltip")));

		Component filterButtonText = Component.translatable(SECTION_SUFFIX_KEY,
				Component.translatable("productivebeesgenesis.configuration.myriad_creations_filter.button"));
		Button filterButton = Button.builder(filterButtonText, button -> minecraft.setScreen(new FilterListScreen(this)))
				.tooltip(Tooltip.create(
						Component.translatable("productivebeesgenesis.configuration.myriad_creations_filter.tooltip")))
				.width(rowWidth)
				.build();
		filterButton.setX(buttonX);
		filterButton.setY(rowY);
		this.addRenderableWidget(filterLabelWidget);
		this.addRenderableWidget(filterButton);
		rowY += 24;

		// 2. 其他服务端配置 — 基于 NeoForge，并为平衡性预设提供列表选择器
		Component otherLabel = Component.translatable(SECTION_SUFFIX_KEY,
				Component.translatable("productivebeesgenesis.configuration.server.other"));
		StringWidget otherLabelWidget = new StringWidget(rowWidth, Button.DEFAULT_HEIGHT, otherLabel,
			font).alignLeft();
		otherLabelWidget.setX(labelX);
		otherLabelWidget.setY(rowY);
		otherLabelWidget.setTooltip(Tooltip.create(
				Component.translatable("productivebeesgenesis.configuration.server.other.tooltip")));

		Component otherButtonText = Component.translatable(SECTION_SUFFIX_KEY,
				Component.translatable("productivebeesgenesis.configuration.server.other.button"));
		Button otherButton = Button.builder(otherButtonText, button -> minecraft.setScreen(
						new BalanceConfigurationScreen(this)))
				.tooltip(Tooltip.create(Component.translatable("productivebeesgenesis.configuration.server.other.tooltip")))
				.width(rowWidth)
				.build();
		otherButton.setX(buttonX);
		otherButton.setY(rowY);
		this.addRenderableWidget(otherLabelWidget);
		this.addRenderableWidget(otherButton);

		// 底部“完成”按钮
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(centerX - 100, this.height - 27, 200, 20).build());
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(guiGraphics);
		guiGraphics.drawCenteredString(font, title, this.width / 2, 15, 0xFFFFFF);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
	}
}
