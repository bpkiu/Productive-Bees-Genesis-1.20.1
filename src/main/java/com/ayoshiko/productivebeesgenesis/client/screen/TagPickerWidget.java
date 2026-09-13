package com.ayoshiko.productivebeesgenesis.client.screen;

import com.ayoshiko.productivebeesgenesis.util.tagfilter.TagExpressionText;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.button.MekanismButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 标签选择器组件 — 浏览物品标签并向表达式追加/移除字面量。
 * <br/>
 * 继承 MEK {@link GuiElement}，提供 4 个操作按钮：
 * <ul>
 *   <li><b>+</b>（plus）— 将当前候选标签追加到表达式</li>
 *   <li><b>-</b>（minus）— 从表达式中移除当前已选标签</li>
 *   <li><b>op</b>（match）— 在 {@code &} 和 {@code |} 之间切换连接运算符</li>
 *   <li><b>side</b>（side）— 在白名单与黑名单之间切换编辑目标</li>
 * </ul>
 * 滚轮可循环浏览添加候选标签列表。每 tick 检测表达式是否变化并自动刷新候选列表。
 * <p>
 * 通过 {@link TagExpressionEditor} 回调读写所属 GUI 的白/黑名单表达式文本，
 * 通过 {@link TagPickerState} 维护样本物品及其候选标签。
 */
public class TagPickerWidget extends GuiElement {

	/** 按钮尺寸（宽=高） */
	private static final int BUTTON_SIZE = 14;
	/** 按钮间距 */
	private static final int BUTTON_GAP = 2;
	/** 文本起始 Y 坐标（按钮行下方） */
	private static final int TEXT_Y = BUTTON_SIZE + 4;

	private final TagExpressionEditor editor;
	private final TagPickerState state = new TagPickerState();
	private final PickerButton plusBtn;
	private final PickerButton minusBtn;
	private final PickerButton matchBtn;
	private final PickerButton sideBtn;

	/** 当前连接运算符，在 {@code &} 和 {@code |} 之间切换 */
	private char currentOperator = '&';
	/** true 编辑白名单，false 编辑黑名单 */
	private boolean currentTarget = true;
	/** 上一次刷新时读取的表达式，用于检测变化 */
	private String lastExpression = "";

	/**
	 * 构造标签选择器组件。
	 *
	 * @param gui    所属 GUI 包装器
	 * @param x      相对 GUI 左上角 X 坐标
	 * @param y      相对 GUI 左上角 Y 坐标
	 * @param width  组件宽度
	 * @param height 组件高度
	 * @param editor 标签表达式编辑器回调
	 */
	public TagPickerWidget(IGuiWrapper gui, int x, int y, int width, int height,
			TagExpressionEditor editor) {
		super(gui, x, y, width, height);
		this.editor = editor;

		int bx = 0;
		plusBtn = addChild(new PickerButton(gui(), relativeX + bx, relativeY,
				Component.literal("+"), this::addSelected));
		bx += BUTTON_SIZE + BUTTON_GAP;
		minusBtn = addChild(new PickerButton(gui(), relativeX + bx, relativeY,
				Component.literal("-"), this::removeSelected));
		bx += BUTTON_SIZE + BUTTON_GAP;
		matchBtn = addChild(new PickerButton(gui(), relativeX + bx, relativeY,
				Component.literal(String.valueOf(currentOperator)), this::toggleOperator));
		bx += BUTTON_SIZE + BUTTON_GAP;
		sideBtn = addChild(new PickerButton(gui(), relativeX + bx, relativeY,
				Component.literal(currentTarget ? "WL" : "BL"), this::toggleTarget));

		refreshCandidates();
	}

	/**
	 * 设置样本物品并刷新候选列表。
	 *
	 * @param stack 样本物品
	 */
	public void setSample(ItemStack stack) {
		state.setStack(stack);
		refreshCandidates();
	}

	@Override
	public void tick() {
		super.tick();
		String expr = editor.getTagExpression(currentTarget);
		if (expr == null) {
			expr = "";
		}
		if (!expr.equals(lastExpression)) {
			refreshCandidates();
		}
	}

	/**
	 * 滚轮循环浏览添加候选标签列表。
	 * <br/>
	 * TODO 1.20.1: MC 1.20.1 的 mouseScrolled 只有 3 参版本（无独立水平增量）。
	 */
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (!isMouseOver(mouseX, mouseY) || delta == 0) {
			return false;
		}
		return state.addList().cycle(delta);
	}

	@Override
	public void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		// 委托给 drawBackground 渲染按钮等背景元素
		drawBackground(guiGraphics, mouseX, mouseY, partialTicks);
	}

	@Override
	public void renderForeground(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
		// 不调用 super.renderForeground 以避免绘制按钮默认文字
		String addTag = state.addList().current();
		String removeTag = state.removeList().current();
		if (!addTag.isEmpty()) {
			drawString(guiGraphics, Component.literal("+ " + addTag), 2, TEXT_Y, screenTextColor());
		}
		if (!removeTag.isEmpty()) {
			drawString(guiGraphics, Component.literal("- " + removeTag), 2, TEXT_Y + 10, screenTextColor());
		}
		if (addTag.isEmpty() && removeTag.isEmpty()) {
			drawString(guiGraphics, Component.literal("(no tags)"), 2, TEXT_Y, screenTextColor());
		}
	}

	/**
	 * 刷新候选标签列表并更新按钮标签。
	 */
	void refreshCandidates() {
		String expr = editor.getTagExpression(currentTarget);
		if (expr == null) {
			expr = "";
		}
		state.refresh(expr);
		lastExpression = expr;
		updateButtonLabels();
	}

	/** 将当前添加候选标签追加到表达式中 */
	void addSelected() {
		String tag = state.addList().current();
		if (tag.isEmpty()) {
			return;
		}
		String expr = editor.getTagExpression(currentTarget);
		String updated = TagExpressionText.appendLiteral(expr, tag, currentOperator, TagPickerState.MAX_CANDIDATES);
		editor.setTagExpression(currentTarget, updated);
		refreshCandidates();
	}

	/** 从表达式中移除当前已选标签 */
	void removeSelected() {
		String tag = state.removeList().current();
		if (tag.isEmpty()) {
			return;
		}
		String expr = editor.getTagExpression(currentTarget);
		String updated = TagExpressionText.removeLiteral(expr, tag);
		editor.setTagExpression(currentTarget, updated);
		refreshCandidates();
	}

	/** 在 {@code &} 和 {@code |} 之间切换连接运算符 */
	void toggleOperator() {
		currentOperator = currentOperator == '&' ? '|' : '&';
		updateButtonLabels();
	}

	/** 在白名单与黑名单之间切换编辑目标 */
	void toggleTarget() {
		currentTarget = !currentTarget;
		refreshCandidates();
	}

	/** 同步按钮显示文字与当前状态 */
	private void updateButtonLabels() {
		matchBtn.setMessage(Component.literal(String.valueOf(currentOperator)));
		sideBtn.setMessage(Component.literal(currentTarget ? "WL" : "BL"));
	}

	// TODO 1.20.1: 1.20.1 的 AbstractWidget#updateWidgetNarration 为抽象方法且 MEK GuiElement 实现对 javac 不可见
	@Override
	public void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
	}

	/**
	 * 紧凑操作按钮 — 14×14 方形按钮，复用 MEK 按钮渲染管线。
	 * <br/>
	 * 使用 {@link GuiElement.ButtonBackground#DEFAULT} 灰色背景，
	 * 与 {@link CtrlButton} 视觉一致。
	 */
	private static final class PickerButton extends MekanismButton {

		PickerButton(IGuiWrapper gui, int x, int y, Component message, Runnable onClick) {
			// TODO 1.20.1: MekanismButton 1.20.1 构造器为 (gui, x, y, w, h, Component, Runnable, IHoverable)
			super(gui, x, y, BUTTON_SIZE, BUTTON_SIZE, message, onClick,
					(element, graphics, mouseX, mouseY) -> {
					});
			setButtonBackground(GuiElement.ButtonBackground.DEFAULT);
		}

		@Override
		protected int getButtonTextColor(int mouseX, int mouseY) {
			return 0x232323;
		}
	}
}
