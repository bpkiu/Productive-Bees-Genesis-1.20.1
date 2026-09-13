package com.ayoshiko.productivebeesgenesis.util.tagfilter;

import com.ayoshiko.productivebeesgenesis.util.tagfilter.TagExpression.And;
import com.ayoshiko.productivebeesgenesis.util.tagfilter.TagExpression.Literal;
import com.ayoshiko.productivebeesgenesis.util.tagfilter.TagExpression.Not;
import com.ayoshiko.productivebeesgenesis.util.tagfilter.TagExpression.Or;
import com.ayoshiko.productivebeesgenesis.util.tagfilter.TagExpression.Xor;

/**
 * 标签表达式解析器。
 *
 * <p>采用递归下降方式解析形如 {@code forge:bees & !minecraft:overworld} 的表达式，
 * 运算符优先级为 {@code OR < XOR < AND < NOT < primary}，支持括号分组。
 * 解析结果以 {@link Result} 封装，错误时携带本地化键而非抛出异常，便于上层提示。</p>
 */
public final class TagExpressionParser {

	/** 表达式最大长度。 */
	public static final int MAX_EXPRESSION_LENGTH = 512;

	/** 表达式最大节点数。 */
	public static final int MAX_NODES = 64;

	/** 最大递归深度。 */
	public static final int MAX_DEPTH = 16;

	private TagExpressionParser() {
	}

	/** 解析输入表达式，返回结果或错误键。 */
	public static Result parse(String input) {
		if (input == null) return Result.empty();
		String normalized = normalizeOperators(input);
		if (normalized.isEmpty()) return Result.empty();
		if (normalized.length() > MAX_EXPRESSION_LENGTH) {
			return Result.error("tag.expression.too_long");
		}
		try {
			Parser parser = new Parser(normalized);
			TagExpression expression = parser.parseOr(1);
			parser.skipSpaces();
			if (!parser.atEnd()) return Result.error("tag.expression.unexpected_token");
			if (expression.nodeCount() > MAX_NODES) return Result.error("tag.expression.too_many_nodes");
			return Result.of(expression);
		} catch (ParseError error) {
			return Result.error(error.errorKey);
		}
	}

	/** 归一化运算符：将 {@code &&}/{@code ||} 合并为单字符并移除空白。 */
	private static String normalizeOperators(String s) {
		String result = s.trim().replace("&&", "&").replace("||", "|");
		StringBuilder builder = new StringBuilder(result.length());
		for (int i = 0; i < result.length(); i++) {
			char c = result.charAt(i);
			if (!Character.isWhitespace(c)) builder.append(c);
		}
		return builder.toString();
	}

	/** 解析结果，包含表达式或错误键。 */
	public record Result(TagExpression expression, String errorKey) {
		public static Result empty() {
			return new Result(null, null);
		}

		public static Result of(TagExpression expr) {
			return new Result(expr, null);
		}

		public static Result error(String key) {
			return new Result(null, key);
		}

		public boolean isPresent() {
			return expression != null;
		}

		public boolean isError() {
			return errorKey != null;
		}
	}

	/** 解析过程中抛出的错误，携带本地化键。 */
	static final class ParseError extends RuntimeException {
		final String errorKey;

		ParseError(String key) {
			super(key);
			this.errorKey = key;
		}
	}

	/** 递归下降解析器。 */
	static final class Parser {
		private final String text;
		private int index;

		Parser(String text) {
			this.text = text;
		}

		TagExpression parseOr(int depth) {
			if (depth > MAX_DEPTH) throw new ParseError("tag.expression.too_deep");
			TagExpression left = parseXor(depth);
			while (consumeOperator('|')) {
				TagExpression right = parseXor(depth);
				left = new Or(left, right);
			}
			return left;
		}

		private TagExpression parseXor(int depth) {
			TagExpression left = parseAnd(depth);
			while (consumeOperator('^')) {
				TagExpression right = parseAnd(depth);
				left = new Xor(left, right);
			}
			return left;
		}

		private TagExpression parseAnd(int depth) {
			TagExpression left = parseUnary(depth);
			while (consumeOperator('&')) {
				TagExpression right = parseUnary(depth);
				left = new And(left, right);
			}
			return left;
		}

		private TagExpression parseUnary(int depth) {
			if (consumeOperator('!')) {
				return new Not(parseUnary(depth));
			}
			return parsePrimary(depth);
		}

		private TagExpression parsePrimary(int depth) {
			skipSpaces();
			if (atEnd()) throw new ParseError("tag.expression.unexpected_end");
			char c = text.charAt(index);
			if (c == '(') {
				index++;
				TagExpression expr = parseOr(depth + 1);
				skipSpaces();
				if (atEnd() || text.charAt(index) != ')') {
					throw new ParseError("tag.expression.missing_paren");
				}
				index++;
				return expr;
			}
			return new Literal(parseLiteral());
		}

		private TagPattern parseLiteral() {
			skipSpaces();
			int start = index;
			while (index < text.length() && isLiteralChar(text.charAt(index))) {
				index++;
			}
			if (index == start) throw new ParseError("tag.expression.invalid_literal");
			String literal = text.substring(start, index);
			try {
				return TagPattern.compile(literal);
			} catch (IllegalArgumentException e) {
				throw new ParseError("tag.expression.invalid_literal");
			}
		}

		private static boolean isLiteralChar(char c) {
			return Character.isLetterOrDigit(c) || c == ':' || c == '_' || c == '-' || c == '.' || c == '*';
		}

		private boolean consumeOperator(char c) {
			skipSpaces();
			if (index < text.length() && text.charAt(index) == c) {
				index++;
				return true;
			}
			return false;
		}

		void skipSpaces() {
			while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
				index++;
			}
		}

		boolean atEnd() {
			return index >= text.length();
		}
	}
}
