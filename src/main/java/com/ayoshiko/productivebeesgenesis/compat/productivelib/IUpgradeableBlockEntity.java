package com.ayoshiko.productivebeesgenesis.compat.productivelib;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * IUpgradeableBlockEntity 本地 stub（1.20.1 迁移适配）
 * <br/>
 * <b>背景</b>：源码原本针对 NeoForge 1.21.1，PB 1.21.1 通过 ProductiveLib 提供此接口。
 * 在 Forge 1.20.1 中，PB 与 ProductiveLib 均不提供此接口。
 * <p>
 * <b>适配策略</b>：创建本地 stub 接口，被 PBG 的方块实体类 implements。
 * PB 原版 {@code UpgradeItem.useOn} 通过 instanceof 检查此接口后调用
 * {@link #getUpgradeHandler()} 获取升级处理器。
 * <p>
 * <b>契约</b>：
 * <ul>
 *   <li>{@link #getUpgradeHandler()} 返回 {@link IItemHandlerModifiable}（通常为
 *       {@link InventoryHandlerHelper.UpgradeHandler} 子类）</li>
 *   <li>{@link #getUpgradeCount(Item)} 返回指定升级物品的已安装数量</li>
 * </ul>
 *
 * @since 2.0.0
 * @author Ayoshiko
 */
public interface IUpgradeableBlockEntity {

	/**
	 * 获取升级处理器 — PB 原版 useOn 调用此方法获取处理器后调用 insertItem 安装升级
	 *
	 * @return 升级处理器（通常为 {@link InventoryHandlerHelper.UpgradeHandler} 子类）
	 */
	IItemHandlerModifiable getUpgradeHandler();

	/**
	 * 获取已安装的指定升级物品数量
	 *
	 * @param upgradeItem 升级物品
	 * @return 已安装数量
	 */
	default int getUpgradeCount(Item upgradeItem) {
		IItemHandlerModifiable handler = getUpgradeHandler();
		if (handler instanceof InventoryHandlerHelper.UpgradeHandler upgradeHandler) {
			return upgradeHandler.getUpgradeCount(upgradeItem);
		}
		int count = 0;
		for (int i = 0; i < handler.getSlots(); i++) {
			ItemStack stack = handler.getStackInSlot(i);
			if (stack.getItem() == upgradeItem) {
				count += stack.getCount();
			}
		}
		return count;
	}
}
