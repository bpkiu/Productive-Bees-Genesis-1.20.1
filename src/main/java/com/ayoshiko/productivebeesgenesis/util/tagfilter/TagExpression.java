package com.ayoshiko.productivebeesgenesis.util.tagfilter;

/**
 * 标签表达式，由字面量与逻辑运算符组合而成的可求值树。
 *
 * <p>支持逻辑与 {@code &}、或 {@code |}、异或 {@code ^}、非 {@code !}，
 * 以记录形式实现访问者友好的不可变结构。每个节点可通过 {@link #nodeCount()}
 * 报告其规模，便于在解析后限制表达式总规模。</p>
 */
public interface TagExpression {

	/**
	 * 对给定候选项求值。
	 *
	 * @param candidate 待求值的候选项
	 * @return 表达式结果为真时返回 {@code true}
	 */
	boolean test(TagCandidate candidate);

	/** 返回本表达式树的节点总数。 */
	int nodeCount();

	/** 字面量节点，匹配单个标签模式。 */
	record Literal(TagPattern pattern) implements TagExpression {
		@Override
		public boolean test(TagCandidate c) {
			return c.matches(pattern);
		}

		@Override
		public int nodeCount() {
			return 1;
		}
	}

	/** 逻辑与节点。 */
	record And(TagExpression left, TagExpression right) implements TagExpression {
		@Override
		public boolean test(TagCandidate c) {
			return left.test(c) && right.test(c);
		}

		@Override
		public int nodeCount() {
			return left.nodeCount() + right.nodeCount() + 1;
		}
	}

	/** 逻辑或节点。 */
	record Or(TagExpression left, TagExpression right) implements TagExpression {
		@Override
		public boolean test(TagCandidate c) {
			return left.test(c) || right.test(c);
		}

		@Override
		public int nodeCount() {
			return left.nodeCount() + right.nodeCount() + 1;
		}
	}

	/** 逻辑异或节点。 */
	record Xor(TagExpression left, TagExpression right) implements TagExpression {
		@Override
		public boolean test(TagCandidate c) {
			return left.test(c) ^ right.test(c);
		}

		@Override
		public int nodeCount() {
			return left.nodeCount() + right.nodeCount() + 1;
		}
	}

	/** 逻辑非节点。 */
	record Not(TagExpression operand) implements TagExpression {
		@Override
		public boolean test(TagCandidate c) {
			return !operand.test(c);
		}

		@Override
		public int nodeCount() {
			return operand.nodeCount() + 1;
		}
	}
}
