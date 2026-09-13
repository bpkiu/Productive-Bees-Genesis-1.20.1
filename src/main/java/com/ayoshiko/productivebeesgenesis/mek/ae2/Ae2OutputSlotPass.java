package com.ayoshiko.productivebeesgenesis.mek.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.me.helpers.BaseActionSource;

import java.util.List;

/**
 * AE2 输出逐槽传递 — 逐个 slot 推送物品到 AE2 网络。
 * <br/>
 * 不合并相同 key 的 slot，适用于 slot 数量较少或需要精确控制推送顺序的场景。
 * 单次插入耗时由全服预算 {@link Ae2GlobalInsertBudget} 在外层钳制，本类额外按
 * {@code insertCostTracker} 检测疲劳以提前终止本轮，剩余槽位由下一 tick 轮转重扫。
 * <p>
 * <b>操作源</b>：使用 {@link BaseActionSource} 匿名子类，与
 * {@link Ae2OutputPusher.ActionSourceHolder} 及 {@link Ae2InputPuller.ActionSourceHolder}
 * 保持一致的 1.20.1 AE2 静态源模式（{@code IActionSource.empty()} 在 1.20.1 不可用）。
 */
final class Ae2OutputSlotPass {

	/**
	 * 懒加载 Holder — AE2 未安装时本类初始化不触发 {@link BaseActionSource} 类解析。
	 * <br/>
	 * 与现有 Ae2OutputPusher/Ae2InputPuller 的 ActionSourceHolder 模式一致，
	 * 避免静态字段在 &lt;clinit&gt; 阶段触发 AE2 类加载（Issue #8）。
	 */
	private static final class ActionSourceHolder {
		/** 全局共享的 AE2 操作源 — {@link BaseActionSource} 完全无状态，全局只需 1 个实例 */
		static final IActionSource INSTANCE = new BaseActionSource() {};
	}

	private Ae2OutputSlotPass() {
	}

	/**
	 * 执行逐槽传递。
	 *
	 * @param ctx     推送上下文（提供缓冲区与 ME 存储）
	 * @param entries 待推送的槽位条目列表
	 */
	static void run(Ae2OutputPushContext ctx, List<Ae2SlotEntry> entries) {
		if (ctx == null || entries == null || entries.isEmpty()) return;

		var meStorage = ctx.meStorage();
		IActionSource source = ActionSourceHolder.INSTANCE;
		long gameTick = ctx.gameTick();

		for (Ae2SlotEntry entry : entries) {
			if (entry == null || entry.key == null || entry.count <= 0) continue;

			long inserted = meStorage.insert(entry.key, entry.count,
					Actionable.MODULATE, source);
			int insertedInt = (int) Math.min(Integer.MAX_VALUE, inserted);

			if (insertedInt > 0) {
				entry.count -= insertedInt;
				if (entry.slot != null) {
					entry.stack.shrink(insertedInt);
					entry.slot.setStack(entry.stack);
				}
			}

			// 时间预算耗尽时提前终止，剩余槽位由下一 tick outputSlotScanCursor 轮转重扫
			// TODO Ae2InsertCostTracker 类尚未实现；落地后将下方全服预算替换为
			//      ctx.buffers().insertCostTracker.isExhausted(gameTick) 以获取 per-tile 精细预算。
			if (Ae2GlobalInsertBudget.isExhausted(gameTick)) break;
		}
	}
}
