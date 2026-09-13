package com.ayoshiko.productivebeesgenesis.client.screen;

import com.ayoshiko.productivebeesgenesis.network.CycleAeOutputPayload;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.button.MekanismButton;
import net.minecraft.network.chat.Component;
import com.ayoshiko.productivebeesgenesis.network.ModPayloads;

/** Toggles whether freshly generated apiary products try AE before local output slots. */
final class ApiaryDirectAeOutputButton extends MekanismButton {

	private static final int SIZE = 14;
	ApiaryDirectEjectOverlay.OverlayTarget target;

	ApiaryDirectAeOutputButton(GuiMekanism<?> gui, int x, int y,
			ApiaryDirectEjectOverlay.OverlayTarget target) {
		super(gui, x, y, SIZE, SIZE, Component.literal("M"), () -> {
			if (target != null && (target.apiary().productivebeesgenesis$isAeItemOutputEnabled()
					|| target.apiary().productivebeesgenesis$isAeFluidOutputEnabled())) {
				ModPayloads.CHANNEL.sendToServer(new CycleAeOutputPayload(
						target.apiary().getBlockPos(), CycleAeOutputPayload.OutputType.APIARY_DIRECT));
			}
		}, (element, graphics, mouseX, mouseY) -> {});
		setButtonBackground(GuiElement.ButtonBackground.DEFAULT);
		this.target = target;
	}

	@Override
	protected int getButtonTextColor(int mouseX, int mouseY) {
		return active && target != null && target.apiary().isDirectAeOutputEnabled() ? 0x009E45 : 0x232323;
	}
}
