package com.ayoshiko.productivebeesgenesis.mek.ae2;

import com.ayoshiko.productivebeesgenesis.ProductiveBeesGenesis;
import net.minecraft.world.item.ItemStack;

import java.util.concurrent.atomic.AtomicLong;

/**
 * AE2 推送异常日志节流器。
 * <br/>
 * 通过原子计数器统计推送异常总次数，并按前 5 次全量、其后每 100 次采样一次的策略输出错误日志，
 * 避免异常风暴刷屏。
 */
final class Ae2PushExceptionLog {

	/** 推送异常累计计数器 */
	private static final AtomicLong PUSH_EXCEPTION_COUNTER = new AtomicLong();

	private Ae2PushExceptionLog() {
	}

	/**
	 * 处理推送异常并按节流策略输出日志。
	 *
	 * @param e        捕获的异常
	 * @param process  进程索引
	 * @param slotIdx  槽位索引
	 * @param stack    物品栈
	 * @param count    尝试推送的数量
	 */
	static void handle(Exception e, int process, int slotIdx, ItemStack stack, int count) {
		long count0 = PUSH_EXCEPTION_COUNTER.incrementAndGet();
		if (count0 <= 5 || (count0 % 100 == 0)) {
			ProductiveBeesGenesis.LOGGER.error("[AE2] Push exception #{} process={} slot={} item={} count={}", count0, process, slotIdx, stack, count, e);
		}
	}

	static {
		// 静态初始化块
	}
}
