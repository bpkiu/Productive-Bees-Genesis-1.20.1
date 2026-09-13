package com.ayoshiko.productivebeesgenesis;

/**
 * PB 升级物品有效性注册事件处理器（Task C-5）
 * <br/>
 * <b>1.20.1 迁移说明</b>：PB 1.20.1 不包含 ProductiveLib 的
 * {@code CollectValidUpgradesEvent} 事件机制。升级验证在 PB 1.20.1 中通过
 * {@code UpgradeableBlockEntity} 和 {@code InventoryHandlerHelper.UpgradeHandler}
 * 的 {@code isValidUpgrade} 方法直接处理，不再需要事件总线注册。
 * <p>
 * 本模组机器（实现 {@link com.ayoshiko.productivebeesgenesis.apiary.IPbUpgradeProvider}）
 * 的升级验证已迁移到 {@link com.ayoshiko.productivebeesgenesis.apiary.PbUpgradeInventorySlot}
 * 中的 item 类型检查，不再依赖此事件处理器。
 * <p>
 * 原注册策略（按方块实体类型差异化，已废弃）：
 * <ul>
 *   <li>蜂箱：8种（产量×4 + 时间×2 + 蜜脾块 + 基因采样器）</li>
 *   <li>离心机（含工厂版）：7种（产量×4 + 时间×2 + 稳定性，不支持蜜脾块和基因采样器）</li>
 * </ul>
 * <p>
 * 注：PB 1.20.1 的 ModItems 不包含 UPGRADE_TIME_2 和 UPGRADE_STABILITY，
 * 这些升级类型在 1.20.1 中不可用。UPGRADE_BLOCK 重命名为 UPGRADE_COMB_BLOCK，
 * UPGRADE_GENE_SAMPLER 重命名为 UPGRADE_BEE_SAMPLER。
 */
public final class CollectValidUpgradesEventHandler {

	private CollectValidUpgradesEventHandler() {
		// 工具类禁止实例化
	}

	// PB 1.20.1 不包含 CollectValidUpgradesEvent 事件机制。
	// 升级验证通过 PbUpgradeInventorySlot 的 item 类型检查实现。
}
