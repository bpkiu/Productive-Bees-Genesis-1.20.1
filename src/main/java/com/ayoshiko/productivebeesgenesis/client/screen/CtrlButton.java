package com.ayoshiko.productivebeesgenesis.client.screen;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.button.MekanismButton;
import net.minecraft.network.chat.Component;

/** Compact text button used for the AE2 input control row. */
final class CtrlButton extends MekanismButton {

	CtrlButton(IGuiWrapper gui, int x, int y, int width, int height, String initialText,
			Runnable onClick) {
		super(gui, x, y, width, height, Component.literal(initialText), onClick,
				(element, graphics, mouseX, mouseY) -> {});
		setButtonBackground(GuiElement.ButtonBackground.DEFAULT);
	}

	@Override
	protected int getButtonTextColor(int mouseX, int mouseY) {
		return 0x232323;
	}
}
