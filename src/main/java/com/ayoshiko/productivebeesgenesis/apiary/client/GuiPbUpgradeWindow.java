package com.ayoshiko.productivebeesgenesis.apiary.client;

import com.ayoshiko.productivebeesgenesis.apiary.IPbUpgradeProvider;
import com.ayoshiko.productivebeesgenesis.apiary.IPbUpgradeSlotContainer;
import com.ayoshiko.productivebeesgenesis.apiary.PbUpgradeType;
import com.ayoshiko.productivebeesgenesis.network.PbUpgradeExtractPayload;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.button.DigitalButton;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiVirtualSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.common.MekanismLang;
import mekanism.common.inventory.container.SelectedWindowData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import com.ayoshiko.productivebeesgenesis.network.ModPayloads;

import java.util.ArrayList;
import java.util.List;

/**
	 * PB 升级窗口
	 * <br/>
	 * 1:1 复刻 MEK {@code GuiUpgradeWindow} 的布局：左侧已安装列表 + 右侧信息屏 +
	 * 输入/输出虚拟槽 + 卸载按钮。
	 * <p>
	 * Bug 7：未选择时自动换行渲染"未选择"文字，与 MEK 原版行为一致，避免长文字溢出右屏。
	 * TODO 1.20.1: 原 {@link mekanism.client.render.IFancyFontRenderer} 的 WrappedTextRenderer
	 * 为 1.21 API，此处改用 {@link #drawWrappedScaledText} 本地实现。
	 * <p>
	 * 重构：将 {@code TileEntityMekApiary} 替换为 {@link IPbUpgradeProvider}，
	 * 使本组件可被蜂箱与离心机共用。{@link GuiPbSupportedUpgrades} 的升级类型集合
	 * 由 provider 的支持范围动态生成。
	 */
public class GuiPbUpgradeWindow extends GuiWindow {

	public static final int WINDOW_WIDTH = 198;

	private static final int LEFT_PANEL_X = 6;
	private static final int PANEL_Y = 18;
	private static final int LEFT_PANEL_WIDTH = 108;
	private static final int PANEL_HEIGHT = 50;
	private static final int RIGHT_PANEL_WIDTH = 59;
	private static final int BUTTON_Y = 54;
	private static final int BUTTON_HEIGHT = 12;

	private final IPbUpgradeProvider provider;
	private final PbUpgradeType[] supportedTypes;
	private final GuiPbUpgradeList scrollList;
	private final GuiInnerScreen rightScreen;
	private final DigitalButton removeButton;

	public GuiPbUpgradeWindow(IGuiWrapper gui, int x, int y, IPbUpgradeProvider provider, SelectedWindowData windowData) {
		super(gui, x, y, WINDOW_WIDTH, calculateHeight(gui, provider), windowData);
		this.provider = provider;
		this.supportedTypes = collectSupportedTypes(provider);
		interactionStrategy = InteractionStrategy.ALL;

		scrollList = addChild(new GuiPbUpgradeList(gui, relativeX + LEFT_PANEL_X, relativeY + PANEL_Y,
				LEFT_PANEL_WIDTH, PANEL_HEIGHT, provider, this::updateEnabledButtons));

		addChild(new GuiPbSupportedUpgrades(gui, relativeX + LEFT_PANEL_X, relativeY + 68, supportedTypes));

		// TODO 1.20.1: GuiElement 无 getRelativeRight()，用 getRelativeX()+getWidth() 等价计算
		rightScreen = addChild(new GuiInnerScreen(gui, scrollList.getRelativeX() + scrollList.getWidth(), relativeY + PANEL_Y,
				RIGHT_PANEL_WIDTH, PANEL_HEIGHT));

		addChild(new GuiProgress(provider::getClientInstallingProgress, ProgressType.INSTALLING, gui,
				rightScreen.getRelativeX() + rightScreen.getWidth() + 3, relativeY + 37));
		// Bug 3：移除卸载进度条，卸载为瞬时操作无动画（与MEK原版一致）

		// TODO 1.20.1: DigitalButton 1.20.1 构造器为 (IGuiWrapper, x, y, w, h, ILangEntry, Runnable, IHoverable)，
		// 不接受 IClickable；改为 Runnable 回调 + null 悬停处理器，点击逻辑移入 uninstallSelectedUpgrade()。
		removeButton = addChild(new DigitalButton(gui, scrollList.getRelativeX() + scrollList.getWidth() + 1, relativeY + BUTTON_Y,
				56, BUTTON_HEIGHT, MekanismLang.UPGRADE_UNINSTALL, this::uninstallSelectedUpgrade,
				(GuiElement.IHoverable) null));

		if (gui() instanceof AbstractContainerScreen<?> containerScreen) {
			if (containerScreen.getMenu() instanceof IPbUpgradeSlotContainer slotContainer) {
				var inputSlot = slotContainer.getPbUpgradeInputSlot();
				var outputSlot = slotContainer.getPbUpgradeOutputSlot();
				if (inputSlot != null) {
					addChild(new GuiVirtualSlot(this, SlotType.NORMAL, gui,
							rightScreen.getRelativeX() + rightScreen.getWidth() + 2, relativeY + 18, inputSlot));
				}
				if (outputSlot != null) {
					addChild(new GuiVirtualSlot(this, SlotType.NORMAL, gui,
							rightScreen.getRelativeX() + rightScreen.getWidth() + 2, relativeY + 72, outputSlot));
				}
			}
		}

		updateEnabledButtons();
	}

	/**
	 * 卸载当前选中的 PB 升级（Shift+点击时卸载全部同类升级）
	 * <br/>
	 * TODO 1.20.1: 由原 DigitalButton 的 IClickable 回调迁移而来；
	 * 按钮仅在 {@link #updateEnabledButtons()} 中有选中项时激活。
	 */
	private void uninstallSelectedUpgrade() {
		if (!scrollList.hasSelection()) {
			return;
		}
		PbUpgradeType selected = scrollList.getSelection();
		if (selected != null) {
			boolean removeAll = Screen.hasShiftDown();
			ModPayloads.CHANNEL.sendToServer(new PbUpgradeExtractPayload(
					provider.getBlockPos(), selected.getId(), removeAll));
		}
	}

	/**
	 * 收集 provider 支持的升级类型（用于 {@link GuiPbSupportedUpgrades} 显示）
	 */
	private static PbUpgradeType[] collectSupportedTypes(IPbUpgradeProvider provider) {
		List<PbUpgradeType> types = new ArrayList<>();
		for (PbUpgradeType type : PbUpgradeType.values()) {
			if (type.isBuiltin()) continue;
			if (provider.isPbUpgradeSupported(type)) {
				types.add(type);
			}
		}
		return types.toArray(new PbUpgradeType[0]);
	}

	/**
	 * 计算窗口高度 — 根据 provider 支持的升级类型数量动态计算
	 */
	private static int calculateHeight(IGuiWrapper gui, IPbUpgradeProvider provider) {
		PbUpgradeType[] types = collectSupportedTypes(provider);
		// TODO 1.20.1: IGuiWrapper 不继承 IFancyFontRenderer，改传 Font
		return 76 + Math.max(18, 12 * GuiPbSupportedUpgrades.calculateNeededRows(gui.getFont(), types));
	}

	private void updateEnabledButtons() {
		removeButton.active = scrollList.hasSelection();
	}

	@Override
	public void renderForeground(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		super.renderForeground(guiGraphics, mouseX, mouseY);
		drawTitleText(guiGraphics, Component.translatable("gui.productivebeesgenesis.pb_upgrade_window.title"), 5);

		int screenWidth = rightScreen.getWidth() - 2;

		if (scrollList.hasSelection()) {
			PbUpgradeType selectedType = scrollList.getSelection();
			if (selectedType != null) {
				int amount = provider.getPbUpgradeInstalledCount(selectedType);
				int max = provider.getPbUpgradeLimit(selectedType);

				// Bug 7：标题自动换行（长名称如 "Productivity Ω" 可正常显示）
				// TODO 1.20.1: 用本地 drawWrappedScaledText 替代 1.21 的 WrappedTextRenderer.renderWithScale
				Component name = Component.translatable(selectedType.getNameKey());
				int lines = drawWrappedScaledText(guiGraphics, name,
						rightScreen.getRelativeX() + 2, rightScreen.getRelativeY() + 2,
						screenWidth - 2, 0.6F, selectedType.getColor());

				int textY = rightScreen.getRelativeY() + 2 + 6 * lines;
				Component countText = Component.translatable("gui.productivebeesgenesis.pb_upgrade_window.count",
						amount, max);
				// TODO 1.20.1: 原 drawScaledScrollingString(…, screenWidth, 2, true, 0.6F, msSelected)
				// 含横向滚动动画，1.20.1 无对应 API，改为裁剪+缩放静态绘制
				drawScaledScreenText(guiGraphics, countText,
						rightScreen.getRelativeX() + 2, textY, 0.6F, screenWidth, true, screenTextColor());

				textY += 8;
				Component desc = PbUpgradeTooltipHelper.descriptionComponent(selectedType);
				drawScaledScreenText(guiGraphics, desc,
						rightScreen.getRelativeX() + 2, textY + 2, 0.6F, screenWidth, true, screenTextColor());
			}
		} else {
			// Bug 7：未选择时自动换行渲染（替代 WrappedTextRenderer.renderWithScale）
			drawWrappedScaledText(guiGraphics,
					Component.translatable("gui.productivebeesgenesis.pb_upgrade_window.no_selection"),
					rightScreen.getRelativeX() + 2, rightScreen.getRelativeY() + 2,
					screenWidth - 2, 0.8F, screenTextColor());
		}

		// 卸载按钮 tooltip：Mekanism 1.20.1 的 GuiElement 不读 MC 原版 tooltip 字段（renderToolTip 只递归 children），
		// 不能用 setTooltip(Tooltip)，需在悬停时手动 displayTooltips
		if (removeButton.active && removeButton.isMouseOver(mouseX, mouseY)) {
			displayTooltips(guiGraphics, mouseX, mouseY, MekanismLang.UPGRADE_UNINSTALL_TOOLTIP.translate());
		}
	}

	/**
	 * 绘制自动换行并按 scale 缩放的文本 — TODO 1.20.1 替代 1.21 的 IFancyFontRenderer.WrappedTextRenderer
	 * <br/>
	 * 以 (x, y) 为左上角锚点，先除以 scale 计算实际可用像素宽度后 {@link Font#split} 换行，
	 * 再在 scale 变换下逐行绘制。
	 *
	 * @return 渲染的行数
	 */
	private int drawWrappedScaledText(GuiGraphics guiGraphics, Component text, float x, float y,
			float maxWidth, float scale, int color) {
		Font font = getFont();
		int splitWidth = Math.max(8, (int) (maxWidth / scale));
		// TODO 1.20.1: Font.split 接收 FormattedText（Component 本身即 FormattedText），
		// 不能传 getVisualOrderText()（FormattedCharSequence 无法转换为 FormattedText）
		List<FormattedCharSequence> lines = font.split(text, splitWidth);
		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(x, y, 0);
		guiGraphics.pose().scale(scale, scale, scale);
		for (int i = 0; i < lines.size(); i++) {
			guiGraphics.drawString(font, lines.get(i), 0, i * 9, color, false);
		}
		guiGraphics.pose().popPose();
		return lines.size();
	}

	/**
	 * 在右侧信息屏范围内绘制按 scale 缩放的文本 — TODO 1.20.1 替代 1.21 的
	 * {@code GuiInnerScreen.drawScaledScrollingString}（无横向滚动动画）。
	 * <br/>
	 * 先 enableScissor 限制在信息屏矩形内，再 translate+scale 绘制（超出宽度部分被裁剪）。
	 */
	private void drawScaledScreenText(GuiGraphics guiGraphics, Component text, float x, float y,
			float scale, float maxWidth, boolean shadow, int color) {
		// TODO 1.20.1: GuiElement 无 getRelativeBottom()，用 getRelativeY()+getHeight() 等价计算
		guiGraphics.enableScissor(rightScreen.getRelativeX(), rightScreen.getRelativeY(),
				rightScreen.getRelativeX() + rightScreen.getWidth(),
				rightScreen.getRelativeY() + rightScreen.getHeight());
		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(x, y, 0);
		guiGraphics.pose().scale(scale, scale, scale);
		guiGraphics.drawString(getFont(), text, 0, 0, color, shadow);
		guiGraphics.pose().popPose();
		guiGraphics.disableScissor();
	}

	@Override
	public void drawBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.drawBackground(guiGraphics, mouseX, mouseY, partialTick);
		guiGraphics.setColor(1, 1, 1, 1);
	}
}
