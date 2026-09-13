package com.ayoshiko.productivebeesgenesis.client.screen;

/**
 * 标签表达式编辑器 — GUI 回调接口。
 * <br/>
 * 由包含标签表达式输入框的 GUI 实现，供 {@link TagPickerWidget} 回调读写表达式。
 */
public interface TagExpressionEditor {

	/**
	 * 获取当前标签表达式。
	 *
	 * @param whitelist true 获取白名单表达式，false 获取黑名单表达式
	 * @return 表达式字符串
	 */
	String getTagExpression(boolean whitelist);

	/**
	 * 设置标签表达式。
	 *
	 * @param whitelist true 设置白名单表达式，false 设置黑名单表达式
	 * @param expression 表达式字符串
	 */
	void setTagExpression(boolean whitelist, String expression);
}
