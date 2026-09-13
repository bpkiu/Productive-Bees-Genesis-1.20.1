package com.ayoshiko.productivebeesgenesis.client.screen;

import com.ayoshiko.productivebeesgenesis.config.BalancePreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/** List-style selector that exposes every balance profile and its description at once. */
final class BalancePresetSelectionScreen extends OptionsSubScreen {

	private static final String SELECTOR_PREFIX =
			"productivebeesgenesis.configuration.balance.profile.selector.";

	private final Screen parent;
	private final List<BalancePreset> presets;
	private final BalancePreset current;
	private final Consumer<BalancePreset> onSelected;

	BalancePresetSelectionScreen(
			Screen parent,
			List<BalancePreset> presets,
			BalancePreset current,
			Consumer<BalancePreset> onSelected) {
		super(parent, Minecraft.getInstance().options,
				Component.translatable(SELECTOR_PREFIX + "title"));
		this.parent = parent;
		this.presets = List.copyOf(presets);
		this.current = current;
		this.onSelected = onSelected;
	}

	// 1.20.1 的 OptionsSubScreen 无 list/addOptions 框架（1.20.2+ 才引入），改为手动布局
	@Override
	protected void init() {
		int centerX = this.width / 2;
		int labelX = centerX - 155;
		int buttonX = centerX + 5;
		int rowWidth = 150;
		int rowY = this.height / 4;

		for (BalancePreset preset : presets) {
			Component tooltipText = Component.empty()
					.append(presetName(preset))
					.append(Component.literal("\n\n"))
					.append(Component.translatable(preset.getTooltipKey()));
			Tooltip tooltip = Tooltip.create(tooltipText);
			StringWidget label = new StringWidget(
					rowWidth,
					Button.DEFAULT_HEIGHT,
					presetName(preset),
					font).alignLeft();
			label.setX(labelX);
			label.setY(rowY);
			label.setTooltip(tooltip);

			Button selectButton = Button.builder(
					Component.translatable(SELECTOR_PREFIX
							+ (preset == current ? "selected" : "select")),
					button -> select(preset))
					.tooltip(tooltip)
					.width(rowWidth)
					.build();
			selectButton.active = preset != current;
			selectButton.setX(buttonX);
			selectButton.setY(rowY);
			this.addRenderableWidget(label);
			this.addRenderableWidget(selectButton);
			rowY += 24;
		}

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

	private void select(BalancePreset preset) {
		onSelected.accept(preset);
		minecraft.setScreen(parent);
	}

	private static Component presetName(BalancePreset preset) {
		return Component.translatable(preset.getTranslationKey());
	}
}
