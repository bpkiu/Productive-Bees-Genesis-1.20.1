package com.ayoshiko.productivebeesgenesis.util;

import com.ayoshiko.productivebeesgenesis.MyriadCreationsEventHandler;
import com.ayoshiko.productivebeesgenesis.ProductiveBeesGenesis;
import com.ayoshiko.productivebeesgenesis.config.ModConfig;
import cy.jdkdigital.productivebees.compat.jei.ingredients.BeeIngredient;
import cy.jdkdigital.productivebees.common.recipe.AdvancedBeehiveRecipe;
import cy.jdkdigital.productivebees.common.recipe.BeeBreedingRecipe;
import cy.jdkdigital.productivebees.common.recipe.BeeConversionRecipe;
import cy.jdkdigital.productivebees.common.recipe.BeeFishingRecipe;
import cy.jdkdigital.productivebees.common.recipe.BeeSpawningRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.common.util.Lazy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
	 * 万象创世单配方处理器
	 * <br/>
	 * 从 {@link BeeRecipeReloader} 抽离，负责处理单个配方的修改/移除决策。
	 * <p>
	 * 处理 5 种 PB 配方类型：
	 * <ul>
	 *   <li>{@link BeeFishingRecipe} — 钓鱼配方：修改概率与群系，或禁用</li>
	 *   <li>{@link BeeBreedingRecipe} — 繁殖配方：修改亲代，或禁用</li>
	 *   <li>{@link BeeSpawningRecipe} — 蜂巢生成配方：修改蜂巢物品与群系，或禁用</li>
	 *   <li>{@link BeeConversionRecipe} — 蜜蜂转化配方：用其他物品转化获得万象创世，或禁用</li>
	 *   <li>{@link AdvancedBeehiveRecipe} — 蜜蜂产出配方：万象创世蜜脾产出参数，或禁用</li>
	 * </ul>
	 * 返回值约定：
	 * <ul>
	 *   <li>null — 移除该配方</li>
	 *   <li>原 recipe — 保留该配方不变</li>
	 *   <li>新 recipe — 替换为新配方</li>
	 * </ul>
	 * <p>
	 * 1.20.1 构造器差异：所有配方均需显式 ResourceLocation id；BeeIngredient 以
	 * {@link Lazy} 包装；钓鱼群系为 {@code List<String>}、生成群系为 {@code String}
	 * （PB 自行解析，支持 "#tag" 前缀）；转化 chance 为 0-100 整数百分比；
	 * 繁殖配方无 parentDeathChance（亲代为 {@code ingredients} 列表）。
	 */
public final class MyriadRecipeProcessor {

	private final RecipeIngredientFactory ingredientFactory;

	/**
	 * @param ingredientFactory 配方 Ingredient 工厂（持有 registryAccess）
	 */
	public MyriadRecipeProcessor(RecipeIngredientFactory ingredientFactory) {
		this.ingredientFactory = ingredientFactory;
	}

	/**
	 * 处理单个配方，返回 null 表示移除，返回原 recipe 表示保留，返回新 recipe 表示替换
	 */
	@SuppressWarnings("unchecked")
	public Recipe<?> processRecipe(Recipe<?> recipe) {

		// 钓鱼配方：修改概率与群系，或禁用
		if (recipe instanceof BeeFishingRecipe fishing) {
			return processFishingRecipe(recipe, fishing);
		}

		// 繁殖配方：修改亲代，或禁用
		if (recipe instanceof BeeBreedingRecipe breeding) {
			return processBreedingRecipe(recipe, breeding);
		}

		// 蜂巢生成配方：修改蜂巢物品与群系，或禁用
		if (recipe instanceof BeeSpawningRecipe spawning) {
			return processSpawningRecipe(recipe, spawning);
		}

		// 蜜蜂转化配方：用其他物品转化获得万象创世，或禁用
		if (recipe instanceof BeeConversionRecipe conversion) {
			return processConversionRecipe(recipe, conversion);
		}

		// 蜜蜂产出配方：万象创世蜜脾产出参数，或禁用
		if (recipe instanceof AdvancedBeehiveRecipe produce) {
			return processProduceRecipe(recipe, produce);
		}

		return recipe;
	}

	/** 处理钓鱼配方 */
	private Recipe<?> processFishingRecipe(Recipe<?> recipe, BeeFishingRecipe fishing) {
		if (!isMyriadcreations(fishing.output)) {
			return recipe;
		}
		// 总开关禁用时移除所有万象创世配方
		if (!MyriadCreationsEventHandler.isMyriadCreationsEnabled()) {
			return null;
		}
		if (!ModConfig.SERVER.fishingEnabled.get()) {
			return null;
		}
		// 1.20.1：群系为 List<String>（PB 自行解析，支持 "#tag" 前缀），chance 为 0-1 double
		// Forge defineList 返回 ConfigValue<List<? extends String>>，需拷贝为 List<String>
		List<String> biomes = new ArrayList<>(ModConfig.SERVER.fishingBiomes.get());
		double chance = ModConfig.SERVER.fishingChance.get().doubleValue();
		// 1.20.1：BeeFishingRecipe 无 id 字段，id 由 Recipe#getId() 提供
		BeeFishingRecipe newRecipe = new BeeFishingRecipe(fishing.getId(), fishing.output, biomes, chance);
		return newRecipe;
	}

	/** 处理繁殖配方 */
	private Recipe<?> processBreedingRecipe(Recipe<?> recipe, BeeBreedingRecipe breeding) {
		// 检查 offspring 或任一亲代是否涉及万象创世蜜蜂
		// 1.20.1：亲代为 ingredients 列表（无 parent1/parent2 字段，无 parentDeathChance）
		boolean involvesMyriadCreations = isMyriadcreations(breeding.offspring)
				|| containsMyriadcreations(breeding.ingredients);
		if (!involvesMyriadCreations) {
			return recipe;
		}
		// 总开关禁用时移除所有涉及万象创世的繁殖配方
		if (!MyriadCreationsEventHandler.isMyriadCreationsEnabled()) {
			return null;
		}
		// 只有 offspring 是万象创世时才修改亲代配置
		if (!isMyriadcreations(breeding.offspring)) {
			return recipe;
		}
		if (!ModConfig.SERVER.breedingEnabled.get()) {
			return null;
		}
		Lazy<BeeIngredient> parent1 = Lazy.of(
				RecipeIngredientFactory.getBeeIngredient(ModConfig.SERVER.breedingParent1.get()));
		Lazy<BeeIngredient> parent2 = Lazy.of(
				RecipeIngredientFactory.getBeeIngredient(ModConfig.SERVER.breedingParent2.get()));
		BeeBreedingRecipe newRecipe = new BeeBreedingRecipe(
				breeding.id, List.of(parent1, parent2), breeding.offspring);
		return newRecipe;
	}

	/** 处理蜂巢生成配方 */
	private Recipe<?> processSpawningRecipe(Recipe<?> recipe, BeeSpawningRecipe spawning) {
		if (!containsMyriadcreations(spawning.output)) {
			return recipe;
		}
		// 总开关禁用时移除所有万象创世配方
		if (!MyriadCreationsEventHandler.isMyriadCreationsEnabled()) {
			return null;
		}
		if (!ModConfig.SERVER.spawningEnabled.get()) {
			return null;
		}
		Ingredient ingredient = RecipeIngredientFactory.createIngredient(ModConfig.SERVER.spawningNest.get());
		// 1.20.1：群系为 String（PB 自行解析，支持 "#tag" 前缀）
		String biomes = ModConfig.SERVER.spawningBiomes.get();
		BeeSpawningRecipe newRecipe = new BeeSpawningRecipe(
				spawning.id, ingredient, spawning.spawnItem, spawning.output, biomes);
		return newRecipe;
	}

	/** 处理蜜蜂转化配方 */
	private Recipe<?> processConversionRecipe(Recipe<?> recipe, BeeConversionRecipe conversion) {
		if (!isMyriadcreations(conversion.result)) {
			return recipe;
		}
		// 总开关禁用时移除所有万象创世配方
		if (!MyriadCreationsEventHandler.isMyriadCreationsEnabled()) {
			return null;
		}
		if (!ModConfig.SERVER.conversionEnabled.get()) {
			return null;
		}
		Supplier<BeeIngredient> source = RecipeIngredientFactory.getBeeIngredient(ModConfig.SERVER.conversionSource.get());
		Supplier<BeeIngredient> result = RecipeIngredientFactory.getBeeIngredient(ModConfig.SERVER.conversionResult.get());
		Ingredient item = RecipeIngredientFactory.createIngredient(ModConfig.SERVER.conversionItem.get());
		// 1.20.1：chance 为 0-100 整数百分比（1.21 为 0-1 浮点数）
		float chance = ModConfig.SERVER.conversionChance.get().floatValue();
		BeeConversionRecipe newRecipe = new BeeConversionRecipe(
				conversion.id, Lazy.of(source), Lazy.of(result), item, Math.round(chance * 100));
		return newRecipe;
	}

	/** 处理蜜蜂产出配方 */
	private Recipe<?> processProduceRecipe(Recipe<?> recipe, AdvancedBeehiveRecipe produce) {
		if (!isMyriadcreations(produce.ingredient)) {
			return recipe;
		}
		// 总开关禁用时移除所有万象创世配方
		if (!MyriadCreationsEventHandler.isMyriadCreationsEnabled()) {
			return null;
		}
		if (!ModConfig.SERVER.produceEnabled.get()) {
			return null;
		}
		// 1.20.1：产出为 Map<Ingredient, IntArrayTag>（IntArrayTag 格式 [min, max, chance%]）
		AdvancedBeehiveRecipe newRecipe = new AdvancedBeehiveRecipe(
				produce.id, produce.ingredient, RecipeIngredientFactory.createProduceOutputs());
		return newRecipe;
	}

	/**
	 * 判断 BeeIngredient 供应商是否对应万象创世蜜蜂
	 */
	private static boolean isMyriadcreations(Supplier<BeeIngredient> supplier) {
		try {
			if (supplier == null) {
				return false;
			}
			// 缓存 supplier.get() 结果，避免重复求值（supplier 可能涉及懒加载）
			BeeIngredient ing = supplier.get();
			return ing != null && PBConstants.MYRIADCREATIONS_TYPE.equals(ing.getBeeType());
		} catch (Exception e) {
			ProductiveBeesGenesis.LOGGER.debug("解析蜜蜂类型供应商失败，返回 false", e);
			return false;
		}
	}

	/**
	 * 判断 BeeIngredient 供应商列表中是否包含万象创世蜜蜂
	 * <br/>
	 * 使用通配符接受 {@code List<Lazy<BeeIngredient>>}（PB 1.20.1 配方字段类型，
	 * Lazy 实现 Supplier）以规避泛型不变性。
	 */
	private static boolean containsMyriadcreations(List<? extends Supplier<BeeIngredient>> outputs) {
		if (outputs == null || outputs.isEmpty()) {
			return false;
		}
		for (Supplier<BeeIngredient> supplier : outputs) {
			if (isMyriadcreations(supplier)) {
				return true;
			}
		}
		return false;
	}
}
