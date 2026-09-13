package com.ayoshiko.productivebeesgenesis.mek.ae2;

/**
 * AE2 输出推送的硬性限制常量。
 * <br/>
 * 集中管理每游戏刻最大物品键数、插入时间预算以及连续零接收次数上限，
 * 避免单次推送在网络病态时占用过多主线程时间。
 */
final class Ae2PushLimits {

	/** 每游戏刻最多提交的不同物品键数 */
	static final int MAX_ITEM_KEYS_PER_TICK = 16;

	/** 单次推送的插入时间预算（纳秒），0.5 毫秒 */
	static final long INSERT_TIME_BUDGET_NANOS = 500_000L; // 0.5ms

	/** 连续零接收次数上限，超过则提前终止本轮推送 */
	static final int CONSECUTIVE_ZERO_ACCEPT_LIMIT = 8;

	private Ae2PushLimits() {
	}
}
