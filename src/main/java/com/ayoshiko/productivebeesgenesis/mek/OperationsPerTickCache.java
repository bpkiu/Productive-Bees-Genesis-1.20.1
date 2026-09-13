package com.ayoshiko.productivebeesgenesis.mek;

import java.util.function.IntSupplier;

/**
 * 每 tick 操作数缓存 — 按 tick 维度缓存计算结果，避免同一 tick 内重复计算。
 * <br/>
 * 当 tick 不同时重新调用供应商获取最新值，相同 tick 直接返回缓存，
 * 适用于 Mekanism 离心机工厂中操作数随 tick 变化但同 tick 内恒定的场景。
 */
public final class OperationsPerTickCache {

	private volatile long cachedTick;

	private volatile int cachedOperations;

	/**
	 * 构造缓存，初始 tick 标记为无效（-1），确保首次 {@link #get} 触发计算。
	 */
	public OperationsPerTickCache() {
		this.cachedTick = -1;
		this.cachedOperations = 0;
	}

	/**
	 * 获取指定 tick 的操作数。
	 * <br/>
	 * 若 {@code currentTick} 与缓存 tick 不同，则调用 {@code supplier} 重新计算并更新缓存；
	 * 否则直接返回缓存的操作数。
	 *
	 * @param currentTick 当前游戏 tick
	 * @param supplier    操作数供应商（仅在新 tick 时调用）
	 * @return 当前 tick 的操作数
	 */
	public int get(long currentTick, IntSupplier supplier) {
		if (currentTick != this.cachedTick) {
			this.cachedTick = currentTick;
			this.cachedOperations = supplier.getAsInt();
		}
		return this.cachedOperations;
	}

	/**
	 * 使缓存失效，下次 {@link #get} 将强制重新计算。
	 */
	public void invalidate() {
		this.cachedTick = -1;
	}
}
