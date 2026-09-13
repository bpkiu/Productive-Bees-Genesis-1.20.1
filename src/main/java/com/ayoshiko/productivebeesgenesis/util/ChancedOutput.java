package com.ayoshiko.productivebeesgenesis.util;

import net.minecraft.world.item.crafting.Ingredient;

/**
 * 本地 ChancedOutput 记录类 — 替代 PB 1.21.1 中被移除的 TagOutputRecipe.ChancedOutput。
 * <br/>
 * PB 1.20.1 的 TagOutputRecipe.getRecipeOutputs() 返回 Map&lt;ItemStack, IntArrayTag&gt;，
 * 其中 IntArrayTag 存储 [min, max, chance]（chance 为 0-100 整数百分比）。
 * 本记录将 IntArrayTag 数据包装为类型安全的结构，使迁移后的业务逻辑保持 1.21.1 语义。
 * <p>
 * 字段语义（与 1.21.1 ChancedOutput 一致）：
 * <ul>
 *   <li>{@link #ingredient} — 产物原料（Ingredient）</li>
 *   <li>{@link #min} — 最小产出数量</li>
 *   <li>{@link #max} — 最大产出数量</li>
 *   <li>{@link #chance} — 产出概率（0.0-1.0 浮点数，由百分比 / 100 转换）</li>
 * </ul>
 *
 * @see RecipeOutputAdapter#fromIntArrayTag(Map) IntArrayTag → ChancedOutput 转换
 */
public record ChancedOutput(
		Ingredient ingredient,
		int min,
		int max,
		float chance
) {
}
