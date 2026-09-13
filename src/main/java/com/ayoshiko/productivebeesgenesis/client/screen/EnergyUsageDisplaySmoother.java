package com.ayoshiko.productivebeesgenesis.client.screen;

import mekanism.api.math.FloatingLong;
import mekanism.api.math.FloatingLongSupplier;

import java.util.function.LongSupplier;

/**
 * Debounces Mekanism energy-tab usage values without changing server-side energy accounting.
 * <p>
 * Client packets and accelerated ticks can expose short-lived intermediate usage snapshots.
 * The value is updated only after a new sample remains stable for the hold window, preventing
 * the tooltip from alternating between two large FE/t values every rendered frame.
 */
public final class EnergyUsageDisplaySmoother implements FloatingLongSupplier {

	static final long DEFAULT_HOLD_NANOS = 750_000_000L;

	private final LongSupplier source;
	private final LongSupplier nanoTime;
	private final long holdNanos;
	private boolean initialized;
	private long displayed;
	private long candidate;
	private long candidateSince;

	public EnergyUsageDisplaySmoother(LongSupplier source) {
		this(source, System::nanoTime, DEFAULT_HOLD_NANOS);
	}

	/**
	 * FloatingLong 版能耗源（每 tick 能耗）— 供 {@code GuiEnergyTab} 三参构造器使用，
	 * 内部仍按 long 值做去抖动。
	 */
	public EnergyUsageDisplaySmoother(FloatingLongSupplier source) {
		this(() -> source.get().longValue(), System::nanoTime, DEFAULT_HOLD_NANOS);
	}

	EnergyUsageDisplaySmoother(LongSupplier source, LongSupplier nanoTime, long holdNanos) {
		this.source = source;
		this.nanoTime = nanoTime;
		this.holdNanos = Math.max(0L, holdNanos);
	}

	@Override
	public FloatingLong get() {
		return FloatingLong.create(getAsLong());
	}

	// TODO 1.20.1: 1.20.1 的 FloatingLongSupplier 仅声明 get()，无 getAsLong() — 本方法为自有扩展
	public long getAsLong() {
		long sampled = Math.max(0L, source == null ? 0L : source.getAsLong());
		long now = nanoTime == null ? System.nanoTime() : nanoTime.getAsLong();
		if (!initialized) {
			initialized = true;
			displayed = sampled;
			candidate = sampled;
			candidateSince = now;
			return displayed;
		}
		if (sampled == displayed) {
			candidate = displayed;
			candidateSince = now;
			return displayed;
		}
		if (sampled != candidate) {
			candidate = sampled;
			candidateSince = now;
			return displayed;
		}
		if (now - candidateSince >= holdNanos) {
			displayed = candidate;
		}
		return displayed;
	}
}