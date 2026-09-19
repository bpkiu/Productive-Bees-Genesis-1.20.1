package com.ayoshiko.productivebeesgenesis.client.screen;

import com.ayoshiko.productivebeesgenesis.config.BalancePreset;
import com.ayoshiko.productivebeesgenesis.config.ModConfig;
import com.ayoshiko.productivebeesgenesis.config.ModConfig.FilterMode;
import com.ayoshiko.productivebeesgenesis.config.ServerConfig;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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
 * 服务端「其他配置」页（Forge 1.20.1 适配）
 * <br/>
 * 原 NeoForge 版本继承 {@code ConfigurationScreen.ConfigurationSectionScreen}，
 * 由框架自动为 SERVER spec 的每个值生成控件；该框架类在 Forge 1.20.1 中不存在，
 * 故本页面改为 {@link OptionsSubScreen}，显式提供两个核心可编辑项：
 * <ul>
 *   <li>全局平衡性配置（{@code balanceProfile}）— 使用列表选择器
 *       {@link BalancePresetSelectionScreen}，与原版交互一致</li>
 *   <li>万象创世过滤模式（{@code myriadCreationsFilterMode}）— 循环切换按钮；
 *       完整的蜜蜂类型黑/白名单编辑仍由 {@link FilterListScreen} 提供</li>
 * </ul>
 * 其余细粒度平衡参数请直接编辑 {@code productivebeesgenesis-server.toml}。
 */
public final class BalanceConfigurationScreen extends OptionsSubScreen {

	private static final String TITLE_KEY =
			"productivebeesgenesis.configuration.section.productivebeesgenesis.server.toml.title";
	private static final String PRESET_LABEL_KEY = "productivebeesgenesis.configuration.balance.profile";
	private static final String PRESET_TOOLTIP_KEY = "productivebeesgenesis.configuration.balance.profile.tooltip";
	private static final String FILTER_LABEL_KEY = "productivebeesgenesis.configuration.myriad_creations_filter";

	public BalanceConfigurationScreen(Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable(TITLE_KEY));
	}

	// 1.20.1 的 OptionsSubScreen 无 list/addOptions 框架（1.20.2+ 才引入），改为手动布局
	@Override
	protected void init() {
		int centerX = this.width / 2;
		int labelX = centerX - 155;
		int buttonX = centerX + 5;
		int rowWidth = 150;
		int rowY = this.height / 4;

		// 1. 全局平衡性配置（balanceProfile）— 打开列表选择器
		ServerConfig server = ModConfig.SERVER;
		BalancePreset current = server == null ? BalancePreset.BASIC : server.balancePreset.get();

		StringWidget presetLabel = new StringWidget(rowWidth, Button.DEFAULT_HEIGHT,
				Component.translatable(PRESET_LABEL_KEY), font).alignLeft();
		presetLabel.setTooltip(Tooltip.create(Component.translatable(PRESET_TOOLTIP_KEY)));
		presetLabel.active = false;
		presetLabel.setX(labelX);
		presetLabel.setY(rowY);

		Button presetButton = Button.builder(presetName(current), button -> openSelector())
				.tooltip(Tooltip.create(profileTooltip(current)))
				.width(rowWidth)
				.build();
		presetButton.setX(buttonX);
		presetButton.setY(rowY);
		this.addRenderableWidget(presetLabel);
		this.addRenderableWidget(presetButton);
		rowY += 24;

		// 2. 万象创世过滤模式 — 循环切换（禁用 → 白名单 → 黑名单 → 禁用）
		FilterMode mode = server == null ? FilterMode.DISABLED : server.myriadCreationsFilterMode.get();
		StringWidget filterLabel = new StringWidget(rowWidth, Button.DEFAULT_HEIGHT,
				Component.translatable(FILTER_LABEL_KEY), font).alignLeft();
		filterLabel.active = false;
		filterLabel.setX(labelX);
		filterLabel.setY(rowY);

		Button filterButton = Button.builder(filterModeName(mode), button -> cycleFilterMode(server))
				.tooltip(Tooltip.create(Component.translatable(FILTER_LABEL_KEY)))
				.width(rowWidth)
				.build();
		filterButton.setX(buttonX);
		filterButton.setY(rowY);
		this.addRenderableWidget(filterLabel);
		this.addRenderableWidget(filterButton);

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

	private void openSelector() {
		ServerConfig server = ModConfig.SERVER;
		BalancePreset selected = server == null ? BalancePreset.BASIC : server.balancePreset.get();
		List<BalancePreset> presets = Arrays.asList(BalancePreset.values());
		minecraft.setScreen(new BalancePresetSelectionScreen(this, presets, selected,
				this::applySelection));
	}

	/**
	 * 应用平衡性模式选择并持久化到 server.toml
	 * <br/>
	 * 与服务端过滤同步包处理逻辑一致：先 set 再 save()，确保重启后仍生效。
	 * 注意：此处仅允许单人/LAN 房主或离线修改场景（客户端屏幕本身只在本地保存）。
	 */
	private void applySelection(BalancePreset selected) {
		ServerConfig server = ModConfig.SERVER;
		if (server == null || selected == null || selected == server.balancePreset.get()) return;
		server.balancePreset.set(selected);
		saveServerSpec();
		rebuildWidgets();
	}

	private void cycleFilterMode(ServerConfig server) {
		if (server == null) return;
		FilterMode[] modes = FilterMode.values();
		FilterMode next = modes[(server.myriadCreationsFilterMode.get().ordinal() + 1) % modes.length];
		server.myriadCreationsFilterMode.set(next);
		saveServerSpec();
		rebuildWidgets();
	}

	private static void saveServerSpec() {
		// 平衡键位于 gameplay 服务端配置文件，仅保存该文件即可
		if (ModConfig.areServerSpecsLoaded()) {
			ModConfig.saveGameplayServerSpec();
		}
	}

	private static Component profileTooltip(BalancePreset preset) {
		return Component.translatable(preset.getTooltipKey());
	}

	private static Component presetName(BalancePreset preset) {
		return Component.translatable(preset.getTranslationKey());
	}

	private static Component filterModeName(FilterMode mode) {
		return Component.translatable(
				"productivebeesgenesis.configuration.filter_mode."
						+ mode.name().toLowerCase(Locale.ROOT));
	}
}
