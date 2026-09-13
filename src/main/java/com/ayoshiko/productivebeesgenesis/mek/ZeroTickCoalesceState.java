package com.ayoshiko.productivebeesgenesis.mek;

import java.util.function.IntSupplier;

/**
 * 零 tick 批量合并状态 — 持有批量合并期间的基线与因子状态。
 * <br/>
 * 实现 {@link IntSupplier}，在合并激活（{@code factor > 0}）时返回按虚拟 tick 分配的操作数，
 * 未激活时返回基础操作供应商的值。配合 {@link ZeroTickBatchMath} 完成零 tick 合并计算。
 */
public final class ZeroTickCoalesceState implements IntSupplier {

	private final IntSupplier baseOperations;

	private volatile int factor;

	private volatile int base;

	private volatile int raised;

	private volatile int batchBase;

	/**
	 * 构造合并状态。
	 *
	 * @param baseOperations 基础操作数供应商（未激活时使用）
	 */
	public ZeroTickCoalesceState(IntSupplier baseOperations) {
		this.baseOperations = baseOperations;
	}

	/**
	 * 获取当前操作数。
	 * <br/>
	 * 当 {@code factor > 0}（合并激活）时，返回按虚拟 tick 分配的操作数
	 * {@code operationsPerVirtualTick(raised, virtualTicksFor(base, batchBase))}；
	 * 否则返回基础操作供应商的值。
	 *
	 * @return 当前操作数
	 */
	@Override
	public int getAsInt() {
		if (this.factor > 0) {
			return ZeroTickBatchMath.operationsPerVirtualTick(this.raised,
					ZeroTickBatchMath.virtualTicksFor(this.base, this.batchBase));
		}
		return this.baseOperations.getAsInt();
	}

	/**
	 * 开始批量合并：读取基础操作数快照并提升基线。
	 */
	public void beginBatch() {
		this.base = this.baseOperations.getAsInt();
		this.raised = ZeroTickBatchMath.raisedBaseline(this.base, this.factor);
		this.batchBase = this.base;
	}

	/**
	 * 结束批量合并：重置所有状态为零。
	 */
	public void endBatch() {
		this.factor = 0;
		this.base = 0;
		this.raised = 0;
		this.batchBase = 0;
	}

	/**
	 * 以指定因子开始合并。
	 * <br/>
	 * 设置因子后立即 {@link #beginBatch()} 读取基线。
	 *
	 * @param factor 合并因子
	 */
	public void begin(int factor) {
		this.factor = factor;
		this.beginBatch();
	}

	/**
	 * 结束合并：清除因子，使 {@link #getAsInt} 回退到基础供应商。
	 */
	public void end() {
		this.factor = 0;
	}

	/**
	 * 获取当前合并因子。
	 *
	 * @return 合并因子
	 */
	public int factor() {
		return this.factor;
	}

	/**
	 * 获取基础操作数（合并开始时读取的快照）。
	 *
	 * @return 基础操作数
	 */
	public int base() {
		return this.base;
	}

	/**
	 * 判定合并是否激活。
	 *
	 * @return {@code true} 当 {@code factor > 0} 时激活
	 */
	public boolean isActive() {
		return this.factor > 0;
	}
}
