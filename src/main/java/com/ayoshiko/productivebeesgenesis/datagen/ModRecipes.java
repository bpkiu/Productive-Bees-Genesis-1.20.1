package com.ayoshiko.productivebeesgenesis.datagen;

import com.ayoshiko.productivebeesgenesis.ProductiveBeesGenesis;
import com.ayoshiko.productivebeesgenesis.recipe.ApiaryShapedRecipe;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.advancements.RequirementsStrategy;
import net.minecraft.advancements.critereon.RecipeUnlockedTrigger;
import net.minecraft.core.NonNullList;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 配方数据生成器（Forge 1.20.1）
 * <br/>
 * 为MEK离心机/蜂箱工厂方块生成合成配方，输出 productivebeesgenesis:apiary_shaped 类型配方
 * （合成时保留机器数据，如能量、物品等）。
 * <p>
 * <b>1.20.1 迁移说明</b>：Forge 1.20.1 的 datagen 端点为原版
 * {@code RecipeProvider#buildRecipes(Consumer<FinishedRecipe>)}（NeoForge 1.21.1 才有
 * {@code RecipeOutput}）。条件（{@link ICondition}）通过序列化到 JSON 的 {@code conditions}
 * 字段输出，由 Forge RecipeManager 在加载时求值。
 * <p>
 * 配方模式遵循各模组原版：
 * <ul>
 *   <li>Mekanism基础：基础离心机/蜂箱(RBR/ICI/RBR)，4级工厂TIER_PATTERN（ACA/IPI/ACA）</li>
 *   <li>EM 5等级：TIER_PATTERN，使用EM的合金/电路/锭标签</li>
 *   <li>ME 4等级：TIER_PATTERN，使用ME的合金/电路/锭标签（INFINITE特殊模式）</li>
 *   <li>EME 4等级：EMEXTRA_PATTERN（ACT/PXQ/TCA），组合ME+EM材料</li>
 * </ul>
 * 所有配方使用ModLoadedCondition条件，仅在对应模组加载时生成。
 * <p>
 * 配方按内容拆分到独立辅助类：
 * <ul>
 *   <li>{@link ModRecipesCentrifuge} — 离心机相关配方</li>
 *   <li>{@link ModRecipesApiary} — 蜂箱相关配方</li>
 * </ul>
 * 本类保留共享的 MekDataBuilder 构建器与通用工具方法（addTierRecipe、addEMETierRecipe、rl）。
 */
public final class ModRecipes extends RecipeProvider {

	public ModRecipes(PackOutput output) {
		// Forge 1.20.1：RecipeProvider 构造器无 registries 参数
		super(output);
	}

	@Override
	protected void buildRecipes(Consumer<FinishedRecipe> output) {
		ModRecipesCentrifuge.addRecipes(output);
		ModRecipesApiary.addRecipes(output);
	}

	// ======================== MekData配方构建器 ========================

	/**
	 * 构建 productivebeesgenesis:apiary_shaped 类型的有序配方
	 * <br/>
	 * 输出的 JSON 与 vanilla ShapedRecipe 完全兼容（外加 type=apiary_shaped 与 conditions 字段），
	 * 由 {@link ApiaryShapedRecipe} 序列化器解析为 {@link ApiaryShapedRecipe} 实例
	 * （继承 MekanismShapedRecipe），在合成时保留机器数据（能量、物品等）
	 * 的同时转移自定义 NBT（蜜蜂/PB升级/喂食槽等），避免合成升级路径数据丢失。
	 */
	static final class MekDataBuilder {
		private final ItemStack result;
		private final List<String> pattern = new ArrayList<>();
		private final Map<Character, Ingredient> key = new LinkedHashMap<>();
		private final Map<String, CriterionTriggerInstance> criteria = new LinkedHashMap<>();
		private final List<ICondition> conditions = new ArrayList<>();

		MekDataBuilder(ItemLike result, int count) {
			this.result = new ItemStack(result, count);
		}

		MekDataBuilder pattern(String... rows) {
			this.pattern.clear();
			this.pattern.addAll(List.of(rows));
			return this;
		}

		MekDataBuilder key(char symbol, TagKey<Item> tag) {
			key.put(symbol, Ingredient.of(tag));
			return this;
		}

		MekDataBuilder key(char symbol, ItemLike item) {
			key.put(symbol, Ingredient.of(item));
			return this;
		}

		MekDataBuilder key(char symbol, Ingredient ingredient) {
			key.put(symbol, ingredient);
			return this;
		}

		/** RegistryObject 版本（如工厂方块）：解引用后按 ItemLike 构建（Block 均实现 ItemLike） */
		MekDataBuilder key(char symbol, @Nullable RegistryObject<?> item) {
			if (item == null || item.get() == null) {
				key.put(symbol, Ingredient.EMPTY);
				return this;
			}
			Object value = item.get();
			if (value instanceof ItemLike itemLike) {
				key.put(symbol, Ingredient.of(itemLike));
			} else {
				throw new IllegalArgumentException("RegistryObject value is not ItemLike: " + value);
			}
			return this;
		}

		MekDataBuilder addCriterion(String name, CriterionTriggerInstance criterion) {
			criteria.put(name, criterion);
			return this;
		}

		MekDataBuilder addCondition(ICondition condition) {
			conditions.add(condition);
			return this;
		}

		void build(Consumer<FinishedRecipe> output, ResourceLocation id) {
			final Advancement.Builder advancementBuilder;
			if (!criteria.isEmpty()) {
				advancementBuilder = Advancement.Builder.advancement()
						.addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(id))
						.rewards(AdvancementRewards.Builder.recipe(id))
						.requirements(RequirementsStrategy.OR);
				criteria.forEach(advancementBuilder::addCriterion);
			} else {
				advancementBuilder = null;
			}
			final ICondition[] recipeConditions = conditions.toArray(new ICondition[0]);
			output.accept(new MekDataFinishedRecipe(
					id, List.copyOf(pattern), new LinkedHashMap<>(key), result,
					advancementBuilder, recipeConditions));
		}
	}

	/**
	 * apiary_shaped 配方的 FinishedRecipe 实现
	 * <br/>
	 * 输出格式与 vanilla ShapedRecipe 的 Result 序列化一致（type 指向本模组序列化器），
	 * 并按 Forge 约定把 {@link ICondition} 写入 {@code conditions} 字段
	 * （运行时由 Forge 条件系统过滤）。datagen 阶段不构造配方对象——
	 * type/键序直接携带构建器的 pattern/key/result 数据。
	 */
	private record MekDataFinishedRecipe(
			ResourceLocation id,
			List<String> pattern,
			Map<Character, Ingredient> key,
			ItemStack result,
			@Nullable Advancement.Builder advancement,
			ICondition[] conditions
	) implements FinishedRecipe {

		@Override
		public void serializeRecipeData(JsonObject json) {
			json.addProperty("type", ForgeRegistries.RECIPE_SERIALIZERS
					.getKey(ApiaryShapedRecipe.SERIALIZER).toString());
			json.addProperty("category", "equipment");
			JsonArray rows = new JsonArray();
			for (String row : pattern) {
				rows.add(row);
			}
			json.add("pattern", rows);
			JsonObject keyJson = new JsonObject();
			for (Map.Entry<Character, Ingredient> entry : key.entrySet()) {
				keyJson.add(String.valueOf(entry.getKey()), entry.getValue().toJson());
			}
			if (keyJson.size() > 0) {
				json.add("key", keyJson);
			}
			JsonObject resultJson = new JsonObject();
			resultJson.addProperty("item",
					ForgeRegistries.ITEMS.getKey(result.getItem()).toString());
			if (result.getCount() != 1) {
				resultJson.addProperty("count", result.getCount());
			}
			json.add("result", resultJson);
			writeConditions(json, conditions);
		}

		@Override
		public ResourceLocation getId() {
			return id;
		}

		@Override
		public RecipeSerializer<?> getType() {
			return ApiaryShapedRecipe.SERIALIZER;
		}

		@Nullable
		@Override
		public JsonObject serializeAdvancement() {
			return advancement == null ? null : advancement.serializeToJson();
		}

		@Nullable
		@Override
		public ResourceLocation getAdvancementId() {
			return advancement == null ? null : ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "recipes/" + id.getPath());
		}

		/** 按 Forge 约定把条件写入 conditions 数组字段 */
		private static void writeConditions(JsonObject json, ICondition[] conditions) {
			if (conditions == null || conditions.length == 0) return;
			JsonArray array = new JsonArray();
			for (ICondition condition : conditions) {
				array.add(CraftingHelper.serialize(condition));
			}
			json.add("conditions", array);
		}
	}

	// ======================== 通用配方方法 ========================

	/**
	 * 添加标准TIER_PATTERN配方（ACA/IPI/ACA）
	 * <br/>
	 * A=合金, C=电路, I=锭/材料, P=上一级工厂
	 * 适用于EM和ME等级
	 */
	static void addTierRecipe(Consumer<FinishedRecipe> output, String path,
			RegistryObject<?> previousFactory,
			RegistryObject<?> resultFactory,
			TagKey<Item> ingotTag,
			TagKey<Item> alloyTag, TagKey<Item> circuitTag,
			ICondition condition) {
		if (previousFactory == null || resultFactory == null) {
			return;
		}
		new MekDataBuilder(asItemLike(resultFactory), 1)
				.pattern("ACA", "IPI", "ACA")
				.key('A', alloyTag)
				.key('C', circuitTag)
				.key('I', ingotTag)
				.key('P', previousFactory)
				.addCondition(condition)
				.build(output, rl(path));
	}

	/**
	 * 添加标准TIER_PATTERN配方（ItemLike版本，用于nether_star等非Tag材料）
	 */
	static void addTierRecipe(Consumer<FinishedRecipe> output, String path,
			RegistryObject<?> previousFactory,
			RegistryObject<?> resultFactory,
			ItemLike ingotItem,
			TagKey<Item> alloyTag, TagKey<Item> circuitTag,
			ICondition condition) {
		if (previousFactory == null || resultFactory == null) {
			return;
		}
		new MekDataBuilder(asItemLike(resultFactory), 1)
				.pattern("ACA", "IPI", "ACA")
				.key('A', alloyTag)
				.key('C', circuitTag)
				.key('I', ingotItem)
				.key('P', previousFactory)
				.addCondition(condition)
				.build(output, rl(path));
	}

	/**
	 * 添加EME组合配方（ACT/PXQ/TCA）
	 * <br/>
	 * A=ME合金, C=EME电路, T=EM合金, P=ME上一级工厂, Q=EM上一级工厂, X=Steel Casing
	 */
	static void addEMETierRecipe(Consumer<FinishedRecipe> output, String path,
			RegistryObject<?> mePreviousFactory,
			RegistryObject<?> emPreviousFactory,
			RegistryObject<?> resultFactory,
			TagKey<Item> meAlloyTag, TagKey<Item> emAlloyTag, Ingredient emeCircuitIngredient,
			ICondition condition) {
		if (mePreviousFactory == null || emPreviousFactory == null || resultFactory == null) {
			return;
		}
		new MekDataBuilder(asItemLike(resultFactory), 1)
				.pattern("ACT", "PXQ", "TCA")
				.key('A', meAlloyTag)
				.key('C', emeCircuitIngredient)
				.key('T', emAlloyTag)
				.key('P', mePreviousFactory)
				.key('Q', emPreviousFactory)
				.key('X', MekanismBlocks.STEEL_CASING)
				.addCondition(condition)
				.build(output, rl(path));
	}

	/** RegistryObject 解引用为 ItemLike（工厂方块均为 Block，实现 ItemLike） */
	private static ItemLike asItemLike(RegistryObject<?> ro) {
		Object value = ro.get();
		if (value instanceof ItemLike itemLike) {
			return itemLike;
		}
		throw new IllegalArgumentException("RegistryObject value is not ItemLike: " + value);
	}

	/** 创建模组命名空间的ResourceLocation */
	static ResourceLocation rl(String path) {
		return ResourceLocation.fromNamespaceAndPath(ProductiveBeesGenesis.MOD_ID, path);
	}
}
