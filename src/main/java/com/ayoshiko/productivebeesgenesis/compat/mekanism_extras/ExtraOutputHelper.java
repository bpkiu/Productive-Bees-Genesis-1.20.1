package com.ayoshiko.productivebeesgenesis.compat.mekanism_extras;

import mekanism.api.inventory.IInventorySlot;
import mekanism.api.recipes.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.api.recipes.outputs.IOutputHandler;
import mekanism.api.recipes.outputs.OutputHelper;
import net.minecraft.world.item.ItemStack;

/**
 * ExtraOutputHelper 本地 stub（1.20.1 迁移适配）
 * <br/>
 * <b>背景</b>：源码原本针对 NeoForge 1.21.1，Mekanism Extras 1.21.1 提供
 * {@code com.jerry.mekanism_extras.api.recipes.outputs.ExtraOutputHelper}，
 * 用于从输出槽创建 IOutputHandler。
 * <p>
 * <b>1.20.1 适配</b>：ME 1.20.1 没有此类，但 Mekanism 1.20.1 原版的
 * {@link OutputHelper#getOutputHandler} 提供等价功能。本 stub 直接委托给原版。
 * <br/>
 * 注意：Mekanism 10.4.x 的 {@code OutputHelper} 只有
 * {@code getOutputHandler(IInventorySlot, RecipeError)} 两参重载；
 * 1.21 版的三参 {@code IntSupplier} 工厂不存在，每 tick 操作数由
 * {@code CachedRecipe} 在处理阶段传入，构造时无需提供，故本 stub 删除该参数。
 *
 * @since 2.0.0
 * @author Ayoshiko
 */
public final class ExtraOutputHelper {

	private ExtraOutputHelper() {}

	/**
	 * 从输出槽创建 IOutputHandler
	 *
	 * @param slot  输出槽
	 * @param error 空间不足时的配方错误类型
	 * @return IOutputHandler 实例
	 */
	public static IOutputHandler<ItemStack> getOutputHandler(IInventorySlot slot, RecipeError error) {
		return OutputHelper.getOutputHandler(slot, error);
	}
}
