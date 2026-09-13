package com.ayoshiko.productivebeesgenesis.mek.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.me.helpers.BaseActionSource;

import java.util.List;

/**
 * AE2 输出合并传递 — 按 key 合并多个 slot 的物品后批量推送。
 * <br/>
 * 先将相同 key 的 slot 合并为一组，然后批量推送以减少 AE2 网络请求次数。
 * 适用于同 key 槽位较多的场景，能将 N 次 ME 插入合并为 1 次。
 * <p>
 * <b>操作源</b>：使用 {@link BaseActionSource} 匿名子类，与
 * {@link Ae2OutputPusher.ActionSourceHolder} 及 {@link Ae2InputPuller.ActionSourceHolder}
 * 保持一致的 1.20.1 AE2 静态源模式（{@code IActionSource.empty()} 在 1.20.1 不可用）。
 */
final class Ae2OutputMergedPass {

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

	private Ae2OutputMergedPass() {
	}

	/**
	 * 执行合并传递。
	 *
	 * @param ctx     推送上下文（提供缓冲区与 ME 存储）
	 * @param entries 待推送的槽位条目列表
	 */
	static void run(Ae2OutputPushContext ctx, List<Ae2SlotEntry> entries) {
		if (ctx == null || entries == null || entries.isEmpty()) return;

		// 按 key 分组条目
		var keyToEntries = ctx.buffers().keyToEntries;
		keyToEntries.clear();
		var keyToTotalCount = ctx.buffers().keyToTotalCount;
		keyToTotalCount.clear();

		for (Ae2SlotEntry entry : entries) {
			if (entry == null || entry.key == null || entry.count <= 0) continue;
			var list = keyToEntries.computeIfAbsent(entry.key, k -> new java.util.ArrayList<>());
			list.add(entry);
			keyToTotalCount.mergeLong(entry.key, entry.count, Long::sum);
		}

		// 按 key 批量推送
		var meStorage = ctx.meStorage();
		IActionSource source = ActionSourceHolder.INSTANCE;

		for (var entry : keyToEntries.entrySet()) {
			AEItemKey key = entry.getKey();
			long totalCount = keyToTotalCount.getLong(key);
			List<Ae2SlotEntry> slotEntries = entry.getValue();

			long inserted = meStorage.insert(key, totalCount,
					Actionable.MODULATE, source);
			int insertedInt = (int) Math.min(Integer.MAX_VALUE, inserted);

			// 将插入数量按顺序分摊到各槽位条目
			int remaining = insertedInt;
			for (Ae2SlotEntry se : slotEntries) {
				if (remaining <= 0) break;
				int take = Math.min(remaining, se.count);
				se.count -= take;
				remaining -= take;
				// 同步更新槽内物品栈
				if (se.slot != null && take > 0) {
					se.stack.shrink(take);
					se.slot.setStack(se.stack);
				}
			}
		}
	}
}
