package com.ayoshiko.productivebeesgenesis.util;

import com.ayoshiko.productivebeesgenesis.config.ModConfig;
import cy.jdkdigital.productivebees.compat.jei.ingredients.BeeIngredient;
import cy.jdkdigital.productivebees.compat.jei.ingredients.BeeIngredientFactory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * PB 配方 Ingredient 解析工具（1.20.1）
 * <br/>
 * 从 {@link BeeRecipeReloader} 抽离，负责根据配置解析：
 * <ul>
 *   <li>物品 ID → {@link Ingredient}（蜂巢、转化物品使用）</li>
 *   <li>蜜蜂类型名 → {@link BeeIngredient} 供应商（繁殖、转化亲代使用）</li>
 *   <li>产出配置 → PB 1.20.1 的 {@code Map<Ingredient, IntArrayTag>} 产出表
 *       （IntArrayTag 格式 [min, max, chance%]，见 {@link CentrifugeRecipeCompat#toTag}）</li>
 * </ul>
 * 无状态工具类。
 * <p>
 * 注：PB 1.20.1 的 BeeIngredient/BeeIngredientFactory 位于 {@code compat.jei.ingredients}
 * 包（1.21 为 {@code common.crafting.ingredient}），且其配方构造器需要显式
 * ResourceLocation id 与 Lazy 包装（见 {@link MyriadRecipeProcessor}）。
 */
public final class RecipeIngredientFactory {

	/**
	 * 根据物品 ID 创建 Ingredient，找不到则返回 EMPTY
	 */
	public static Ingredient createIngredient(String itemId) {
		try {
			ResourceLocation rl = ResourceLocation.parse(itemId);
			Optional<Item> item = BuiltInRegistries.ITEM.getOptional(rl);
			return item.<Ingredient>map(i -> Ingredient.of(i)).orElse(Ingredient.EMPTY);
		} catch (Exception e) {
			// DevLog 节流日志便于排查（数据重载路径，统一门面）
			DevLog.warn("recipe_reload", "解析蜂巢物品 '{}' 失败，使用空 Ingredient: {}", itemId, e.toString());
			return Ingredient.EMPTY;
		}
	}

	/**
	 * 根据蜜蜂类型名获取 BeeIngredient 供应商
	 * <br/>
	 * 使用 {@link BeeIngredientFactory#getIngredient(String)} 获取 lazy supplier。
	 * 调用前会先校验 BeeIngredientFactory 已包含该类型，避免序列化时返回 null。
	 * 若类型不存在，回退到 minecraft:bee 防止 NPE。
	 */
	public static Supplier<BeeIngredient> getBeeIngredient(String name) {
		if (name == null || name.isBlank()) {
			return BeeIngredientFactory.getIngredient("minecraft:bee");
		}
		if (!BeeIngredientFactory.getOrCreateList().containsKey(name)) {
			// DevLog 节流日志便于排查（数据重载路径，统一门面）
			DevLog.warn("recipe_reload", "蜜蜂类型 '{}' 未在 BeeIngredientFactory 中找到，回退到 minecraft:bee", name);
			return BeeIngredientFactory.getIngredient("minecraft:bee");
		}
		return BeeIngredientFactory.getIngredient(name);
	}

	/**
	 * 根据配置构建万象创世蜜脾的产出表（PB 1.20.1 格式）
	 * <br/>
	 * 返回单个条目的 {@code Map<Ingredient, IntArrayTag>}，物品、数量、概率均来自配置。
	 * IntArrayTag 格式为 [min, max, chance%]（chance 为 0-100 整数百分比）。
	 * 防御性处理：当配置出现 min > max 时自动纠正。
	 */
	public static Map<net.minecraft.world.item.crafting.Ingredient, IntArrayTag> createProduceOutputs() {
		Ingredient ingredient = createIngredient(ModConfig.SERVER.produceOutputItem.get());
		int min = ModConfig.SERVER.produceOutputMin.get();
		int max = ModConfig.SERVER.produceOutputMax.get();
		// 防御性处理：当配置出现 min > max 时自动纠正，避免产出行为异常
		// 注：min > max 的告警由 ModConfig 交叉校验统一输出，此处不重复记录
		int finalMin = Math.min(min, max);
		int finalMax = Math.max(min, max);
		float chance = ModConfig.SERVER.produceOutputChance.get().floatValue();
		Map<Ingredient, IntArrayTag> outputs = new LinkedHashMap<>(1);
		outputs.put(ingredient, CentrifugeRecipeCompat.toTag(finalMin, finalMax, chance));
		return outputs;
	}
}
