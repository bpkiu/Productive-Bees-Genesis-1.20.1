package com.ayoshiko.productivebeesgenesis.apiary.client;

import com.ayoshiko.productivebeesgenesis.ProductiveBeesGenesis;
import com.ayoshiko.productivebeesgenesis.apiary.IPbUpgradeProvider;
import com.ayoshiko.productivebeesgenesis.apiary.PbUpgradeInventorySlot;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.tab.window.GuiWindowCreatorTab;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.inventory.container.SelectedWindowData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
	 * PB 升级 TAB 按钮
	 * <br/>
	 * 重构：泛型化 {@code TILE extends IPbUpgradeProvider}，使蜂箱与离心机可共用本组件。
	 * 蜂箱传入 {@code GuiPbUpgradeTab<TileEntityMekApiary>}，离心机传入
	 * {@code GuiPbUpgradeTab<TileEntityMekCentrifuge>}，两者均实现 {@link IPbUpgradeProvider}。
	 *
	 * @param <TILE> 方块实体类型，必须实现 IPbUpgradeProvider
	 */
public class GuiPbUpgradeTab<TILE extends IPbUpgradeProvider> extends GuiWindowCreatorTab<TILE, GuiPbUpgradeTab<TILE>> {

	private static final int TAB_COLOR = 0xFFF57F17;

	private static final ResourceLocation ICON =
		ResourceLocation.fromNamespaceAndPath(ProductiveBeesGenesis.MOD_ID, "textures/gui/pb_upgrade_tab.png");

	public GuiPbUpgradeTab(IGuiWrapper gui, TILE tile, Supplier<GuiPbUpgradeTab<TILE>> elementSupplier) {
		// TODO 1.20.1: IGuiWrapper 无 getXSize()（GuiMekanism 自有方法），IGuiWrapper.getWidth() 与其等价
		super(ICON, gui, tile, gui.getWidth(), 98, 26, 18, false, elementSupplier);
		setTooltip(Tooltip.create(Component.translatable("gui.productivebeesgenesis.pb_upgrade_tab.tooltip")));
	}

	@Override
	protected void colorTab(GuiGraphics guiGraphics) {
		MekanismRenderer.color(guiGraphics, TAB_COLOR);
	}

	// TODO 1.20.1: MEK 10.4.x 生产 jar 以 SRG 名（m_87963_）实现 AbstractWidget#renderWidget，
	// 对 official 映射编译期不可见；此处以官方名补齐抽象实现并委托父类，保持 MEK 原有渲染管线
	@Override
	protected void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.m_87963_(guiGraphics, mouseX, mouseY, partialTick);
	}

	// TODO 1.20.1: MEK 10.4.x 的 GuiWindowCreatorTab#createWindow 为无参抽象方法，
	// 窗口数据改由内部直接以 PB_UPGRADE_WINDOW_DATA 传入窗口构造器
	@Override
	protected GuiWindow createWindow() {
		int windowWidth = GuiPbUpgradeWindow.WINDOW_WIDTH;
		int x = Math.max(0, (getGuiWidth() - windowWidth) / 2);
		return new GuiPbUpgradeWindow(gui(), x, 15, dataSource, PbUpgradeInventorySlot.PB_UPGRADE_WINDOW_DATA);
	}

	/* TODO 1.20.1: MEK 10.4.x 的 GuiWindowCreatorTab 无 getNextWindowData 钩子，
	   本方法降级为不被父类回调的辅助方法，窗口数据已内联至 createWindow() */
	protected SelectedWindowData getNextWindowData() {
		return PbUpgradeInventorySlot.PB_UPGRADE_WINDOW_DATA;
	}

	// TODO 1.20.1: 1.20.1 的 AbstractWidget#updateWidgetNarration 为抽象方法且 MEK GuiElement 实现对 javac 不可见
	@Override
	public void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
	}
}
