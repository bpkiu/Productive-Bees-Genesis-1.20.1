package com.ayoshiko.productivebeesgenesis.client.screen;

import com.ayoshiko.productivebeesgenesis.mek.ae2.Ae2TagFilter;
import com.ayoshiko.productivebeesgenesis.network.ModPayloads;
import com.ayoshiko.productivebeesgenesis.network.SetAeInputTagFilterPayload;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.inventory.container.SelectedWindowData.WindowType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * AE2 输入标签过滤器配置窗口 — 编辑白名单/黑名单标签表达式。
 * <br/>
 * 继承 MEK {@link GuiWindow}，实现 {@link TagExpressionEditor} 供
 * {@link TagPickerWidget} 回调读写表达式。
 * <p>
 * 布局：白名单文本框 + 黑名单文本框 + 保存按钮。
 * 文本框通过字符过滤器限制只能输入标签表达式的合法字符
 * （字面量字符、运算符 {@code & | ^}、{@code !}、括号与空白）。
 * <p>
 * 保存时先本地 {@link Ae2TagFilter#apply} 应用，再通过
 * {@link SetAeInputTagFilterPayload} 发送到服务端持久化。
 */
public final class GuiAeInputTagFilterConfig extends GuiWindow implements TagExpressionEditor {

	private static final int WINDOW_WIDTH = 200;
	private static final int WINDOW_HEIGHT = 96;
	private static final int FIELD_X = 50;
	private static final int FIELD_W = 144;
	private static final int FIELD_H = 18;
	private static final int WL_FIELD_Y = 30;
	private static final int BL_FIELD_Y = 54;
	private static final int SAVE_BTN_X = FIELD_X;
	private static final int SAVE_BTN_Y = 78;
	private static final int BUTTON_WIDTH = 50;
	private static final int BUTTON_HEIGHT = 18;

	private final BlockPos pos;
	private final Ae2TagFilter tagFilter;
	private final GuiTextField whitelistField;
	private final GuiTextField blacklistField;

	/**
	 * 构造 AE2 输入标签过滤器配置窗口。
	 *
	 * @param gui       所属 GUI 包装器
	 * @param x         窗口 X 坐标
	 * @param y         窗口 Y 坐标
	 * @param pos       方块坐标
	 * @param tagFilter 标签过滤器实例
	 */
	public GuiAeInputTagFilterConfig(IGuiWrapper gui, int x, int y, BlockPos pos, Ae2TagFilter tagFilter) {
		super(gui, x, y, WINDOW_WIDTH, WINDOW_HEIGHT, new SelectedWindowData(WindowType.UNSPECIFIED));
		this.pos = pos;
		this.tagFilter = tagFilter;
		this.interactionStrategy = InteractionStrategy.ALL;

		whitelistField = addChild(new GuiTextField(gui(), relativeX + FIELD_X,
				relativeY + WL_FIELD_Y, FIELD_W, FIELD_H));
		configureField(whitelistField, tagFilter.getWhitelistSource());

		blacklistField = addChild(new GuiTextField(gui(), relativeX + FIELD_X,
				relativeY + BL_FIELD_Y, FIELD_W, FIELD_H));
		configureField(blacklistField, tagFilter.getBlacklistSource());

		// 保存按钮 — 构造即注册，无需保留引用
		addChild(new SaveButton(gui(), relativeX + SAVE_BTN_X,
				relativeY + SAVE_BTN_Y,
				Component.translatable("productivebeesgenesis.gui.ae_input_tag_filter.save"),
				this::save));
	}

	/**
	 * 配置文本字段：设置字符过滤器、最大长度、初始文本和回车保存。
	 *
	 * @param field      待配置的文本字段
	 * @param initialText 初始文本
	 */
	private void configureField(GuiTextField field, String initialText) {
		field.setInputValidator(GuiAeInputTagFilterConfig::isExpressionCharacter);
		field.setMaxLength(Ae2TagFilter.MAX_EXPRESSION_LENGTH);
		field.setText(initialText == null ? "" : initialText);
		field.setEnterHandler(this::save);
	}

	@Override
	public String getTagExpression(boolean whitelist) {
		return whitelist ? whitelistField.getText() : blacklistField.getText();
	}

	@Override
	public void setTagExpression(boolean whitelist, String expression) {
		GuiTextField field = whitelist ? whitelistField : blacklistField;
		field.setText(expression == null ? "" : expression);
	}

	@Override
	public void renderForeground(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
		super.renderForeground(guiGraphics, mouseX, mouseY);
		drawTitleText(guiGraphics,
				Component.translatable("productivebeesgenesis.gui.ae_input_tag_filter.title"), 5);
		drawLabel(guiGraphics,
				Component.translatable("productivebeesgenesis.gui.ae_input_tag_filter.whitelist"),
				5, WL_FIELD_Y + 5);
		drawLabel(guiGraphics,
				Component.translatable("productivebeesgenesis.gui.ae_input_tag_filter.blacklist"),
				5, BL_FIELD_Y + 5);
	}

	/**
	 * 绘制字段标签文本。
	 *
	 * @param guiGraphics 图形上下文
	 * @param label       标签组件
	 * @param x           相对窗口 X 坐标
	 * @param y           相对窗口 Y 坐标
	 */
	private void drawLabel(GuiGraphics guiGraphics, Component label, int x, int y) {
		drawString(guiGraphics, label, x, y, screenTextColor());
	}

	/**
	 * 校验当前输入的表达式长度是否合法。
	 *
	 * @return true 如果白名单和黑名单表达式长度均未超限
	 */
	boolean validate() {
		return whitelistField.getText().length() <= Ae2TagFilter.MAX_EXPRESSION_LENGTH
				&& blacklistField.getText().length() <= Ae2TagFilter.MAX_EXPRESSION_LENGTH;
	}

	/**
	 * 保存白名单/黑名单表达式到本地过滤器并发送到服务端。
	 * <br/>
	 * 先调用 {@link Ae2TagFilter#apply} 更新客户端副本，
	 * 再通过 {@link SetAeInputTagFilterPayload} 推送到服务端持久化。
	 */
	void save() {
		String wl = whitelistField.getText();
		String bl = blacklistField.getText();
		tagFilter.apply(wl, bl);
		ModPayloads.CHANNEL.sendToServer(new SetAeInputTagFilterPayload(pos, wl, bl));
		close();
	}

	/**
	 * 判断字符是否为标签表达式的合法字符。
	 * <br/>
	 * 合法字符包括：
	 * <ul>
	 *   <li>字面量字符 — 字母、数字、{@code : _ - . *}</li>
	 *   <li>二元运算符 — {@code & | ^}</li>
	 *   <li>一元运算符 — {@code !}（取反）</li>
	 *   <li>分组 — {@code ( )}</li>
	 *   <li>空白 — 会清理，但不阻止输入</li>
	 * </ul>
	 *
	 * @param character 待校验字符
	 * @return true 如果字符合法
	 */
	private static boolean isExpressionCharacter(char character) {
		if (Character.isWhitespace(character)) {
			return true;
		}
		if (Character.isLetterOrDigit(character)) {
			return true;
		}
		return character == ':' || character == '_' || character == '-'
				|| character == '.' || character == '*'
				|| character == '&' || character == '|' || character == '^'
				|| character == '!' || character == '(' || character == ')';
	}

	/**
	 * 保存按钮 — MEK 风格按钮，DEFAULT 灰色背景。
	 */
	private static final class SaveButton extends MekanismButton {

		SaveButton(IGuiWrapper gui, int x, int y, Component message, Runnable onClick) {
			// TODO 1.20.1: MekanismButton 1.20.1 构造器为 (gui, x, y, w, h, Component, Runnable, IHoverable)
			super(gui, x, y, BUTTON_WIDTH, BUTTON_HEIGHT, message, onClick,
					(element, graphics, mouseX, mouseY) -> {
					});
			setButtonBackground(GuiElement.ButtonBackground.DEFAULT);
		}
	}
}
