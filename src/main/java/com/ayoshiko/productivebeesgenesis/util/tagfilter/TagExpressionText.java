package com.ayoshiko.productivebeesgenesis.util.tagfilter;

import java.util.ArrayList;
import java.util.List;

/**
 * 标签表达式文本工具。
 *
 * <p>提供在表达式字符串层面追加、移除、列举字面量的便捷方法，并通过
 * 分词与清理保证结果表达式始终结构合法。所有方法均为静态且不依赖任何
 * 游戏运行时类型。</p>
 */
public final class TagExpressionText {

	private static final int MAX_CLEANUP_ROUNDS = 8;

	private TagExpressionText() {
	}

	/** 判断表达式中是否包含指定字面量。 */
	public static boolean containsLiteral(String expr, String literal) {
		return indexOfLiteral(expr, literal) >= 0;
	}

	/**
	 * 向表达式追加字面量。
	 *
	 * @param expr        原表达式
	 * @param literal     待追加的字面量
	 * @param operator    连接运算符，如 {@code &} 或 {@code |}
	 * @param maxLiterals 字面量数量上限
	 * @return 追加后的表达式；若已存在或超限则原样返回
	 */
	public static String appendLiteral(String expr, String literal, char operator, int maxLiterals) {
		if (literal == null || literal.isBlank()) return expr == null ? "" : expr.trim();
		String base = expr == null ? "" : expr.trim();
		if (containsLiteral(base, literal)) return base;
		if (listLiterals(base).size() >= maxLiterals) return base;
		String combined = base.isEmpty() ? literal : base + operator + literal;
		List<String> tokens = cleanup(tokenize(combined));
		return join(tokens);
	}

	/** 列出表达式中所有字面量，保留出现顺序。 */
	public static List<String> listLiterals(String expr) {
		List<String> literals = new ArrayList<>();
		for (String token : tokenize(expr)) {
			if (isLiteralToken(token)) literals.add(token);
		}
		return literals;
	}

	/** 从表达式中移除所有匹配的字面量并清理残余运算符。 */
	public static String removeLiteral(String expr, String literal) {
		if (expr == null || literal == null) return expr == null ? "" : expr;
		List<String> tokens = tokenize(expr);
		boolean removed = tokens.removeIf(t -> isLiteralToken(t) && t.equals(literal));
		if (!removed) return expr;
		return join(cleanup(tokens));
	}

	/** 返回字面量在分词结果中的索引，不存在返回 -1。 */
	private static int indexOfLiteral(String expr, String literal) {
		if (expr == null || literal == null) return -1;
		List<String> tokens = tokenize(expr);
		for (int i = 0; i < tokens.size(); i++) {
			if (isLiteralToken(tokens.get(i)) && tokens.get(i).equals(literal)) {
				return i;
			}
		}
		return -1;
	}

	/** 将表达式拆分为字面量与单字符运算符分词。 */
	private static List<String> tokenize(String expr) {
		List<String> tokens = new ArrayList<>();
		if (expr == null) return tokens;
		int i = 0;
		while (i < expr.length()) {
			char c = expr.charAt(i);
			if (isLiteralChar(c)) {
				int start = i;
				while (i < expr.length() && isLiteralChar(expr.charAt(i))) i++;
				tokens.add(expr.substring(start, i));
			} else if (Character.isWhitespace(c)) {
				i++;
			} else {
				tokens.add(String.valueOf(c));
				i++;
			}
		}
		return tokens;
	}

	/** 反复清理直到稳定或达到轮次上限。 */
	private static List<String> cleanup(List<String> tokens) {
		List<String> copy = new ArrayList<>(tokens);
		for (int round = 0; round < MAX_CLEANUP_ROUNDS; round++) {
			if (!cleanupOnce(copy)) break;
		}
		return copy;
	}

	/** 执行一轮清理，移除悬空运算符与空括号，返回是否发生过变更。 */
	private static boolean cleanupOnce(List<String> tokens) {
		boolean changed = false;
		for (int i = tokens.size() - 1; i >= 0; i--) {
			String token = tokens.get(i);
			if (token.equals("(") && i + 1 < tokens.size() && tokens.get(i + 1).equals(")")) {
				tokens.remove(i);
				tokens.remove(i);
				changed = true;
				continue;
			}
			if (isBinaryOperator(token)) {
				String prev = previousOf(tokens, i);
				boolean leftOk = isLiteralToken(prev) || prev.equals(")");
				boolean rightOk = i + 1 < tokens.size()
						&& (isLiteralToken(tokens.get(i + 1)) || tokens.get(i + 1).equals("(") || tokens.get(i + 1).equals("!"));
				if (!leftOk || !rightOk) {
					tokens.remove(i);
					changed = true;
					continue;
				}
			}
			if (token.equals("!")) {
				boolean rightOk = i + 1 < tokens.size()
						&& (isLiteralToken(tokens.get(i + 1)) || tokens.get(i + 1).equals("(") || tokens.get(i + 1).equals("!"));
				if (!rightOk) {
					tokens.remove(i);
					changed = true;
				}
			}
		}
		return changed;
	}

	/** 拼接分词为表达式字符串。 */
	private static String join(List<String> tokens) {
		StringBuilder builder = new StringBuilder();
		for (String token : tokens) builder.append(token);
		return builder.toString();
	}

	/** 返回指定索引前一个分词，越界返回空串。 */
	private static String previousOf(List<String> tokens, int index) {
		if (index <= 0 || index > tokens.size()) return "";
		return tokens.get(index - 1);
	}

	/** 判断分词是否为字面量（非运算符）。 */
	private static boolean isLiteralToken(String token) {
		return token != null && !token.isEmpty() && isLiteralChar(token.charAt(0));
	}

	/** 判断分词是否为二元运算符 {@code &} {@code |} {@code ^}。 */
	private static boolean isBinaryOperator(String token) {
		return "&".equals(token) || "|".equals(token) || "^".equals(token);
	}

	private static boolean isLiteralChar(char c) {
		return Character.isLetterOrDigit(c) || c == ':' || c == '_' || c == '-' || c == '.' || c == '*';
	}
}
