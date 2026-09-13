package com.ayoshiko.productivebeesgenesis.apiary.client;

import com.ayoshiko.productivebeesgenesis.apiary.TileEntityMekApiaryFactory;
import com.ayoshiko.productivebeesgenesis.network.ApiaryToggleSortingPayload;
import mekanism.client.SpecialColors;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement.ButtonBackground;
import mekanism.client.gui.element.GuiInsetElement;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.MekanismLang;
import mekanism.common.util.MekanismUtils.ResourceType;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.text.BooleanStateDisplay.OnOff;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.BlockPos;
import com.ayoshiko.productivebeesgenesis.network.ModPayloads;
import org.jetbrains.annotations.NotNull;

/**
	 * 通用机械蜂箱工厂排序切换 Tab
	 * <br>
	 * 1:1复刻 MEK 原版 GuiSortingTab：
	 * <ul>
	 *   <li>尺寸：宽35×高18（左侧伸出26px）</li>
	 *   <li>位置：x=-26, y=62</li>
	 *   <li>颜色：TAB_FACTORY_SORT（灰色）</li>
	 *   <li>On/Off 状态文本居中显示</li>
	 * </ul>
	 * <p>
	 * 不直接使用 MEK 原版 PacketGuiInteract.AUTO_SORT_BUTTON（该包检查 tile instanceof TileEntityFactory，
	 * 蜂箱工厂不继承 TileEntityFactory），改用自定义 {@link ApiaryToggleSortingPayload}。
	 */
public class GuiApiarySortingTab extends GuiInsetElement<TileEntityMekApiaryFactory> {

	private static final int TAB_X = -26;
	private static final int TAB_Y = 62;
	private static final int TAB_WIDTH = 35;
	private static final int TAB_HEIGHT = 18;

	public GuiApiarySortingTab(IGuiWrapper gui, TileEntityMekApiaryFactory tile) {
		super(MekanismUtils.getResource(ResourceType.GUI, "sorting.png"), gui, tile,
				TAB_X, TAB_Y, TAB_WIDTH, TAB_HEIGHT, true);
		setButtonBackground(ButtonBackground.DEFAULT);
	}

	@Override
	public void renderForeground(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
		super.renderForeground(guiGraphics, mouseX, mouseY);
		// Mekanism 1.20.1 的 GuiElement#renderToolTip 只递归 children，不读原版 tooltip 字段，
		// 因此不能用 MC 的 setTooltip(Tooltip)，需在悬停时手动 displayTooltips（与 MEK 原版 Tab 一致）
		if (isMouseOver(mouseX, mouseY)) {
			displayTooltips(guiGraphics, mouseX, mouseY, MekanismLang.AUTO_SORT.translate());
		}
	}

	@Override
	public void drawBackground(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		super.drawBackground(guiGraphics, mouseX, mouseY, partialTicks);
		// TODO 1.20.1: Mekanism 10.4.x 无 drawScrollingString/TextAlignment —— 手动水平居中绘制
		net.minecraft.network.chat.Component label = OnOff.of(dataSource.isSorting()).getTextComponent();
		int labelWidth = getStringWidth(label);
		drawString(guiGraphics, label, (width - labelWidth) / 2, (height - 8) / 2, titleTextColor());
	}

	@Override
	protected void colorTab(GuiGraphics guiGraphics) {
		MekanismRenderer.color(guiGraphics, SpecialColors.TAB_FACTORY_SORT);
	}

	// TODO 1.20.1: MEK 10.4.x 生产 jar 以 SRG 名（m_87963_）实现 AbstractWidget#renderWidget，
	// 对 official 映射编译期不可见；此处以官方名补齐抽象实现并委托父类，保持 MEK 原有渲染管线
	@Override
	protected void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.m_87963_(guiGraphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void onClick(double mouseX, double mouseY, int button) {
		BlockPos pos = dataSource.getBlockPos();
		ModPayloads.CHANNEL.sendToServer(new ApiaryToggleSortingPayload(pos));
	}

	// TODO 1.20.1: 1.20.1 的 AbstractWidget#updateWidgetNarration 为抽象方法且 MEK GuiElement 实现对 javac 不可见
	@Override
	public void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
	}
}
