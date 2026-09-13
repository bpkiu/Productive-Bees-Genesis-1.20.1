package com.ayoshiko.productivebeesgenesis.util;

import com.ayoshiko.productivebeesgenesis.ProductiveBeesGenesis;
import com.mojang.datafixers.util.Pair;
import cy.jdkdigital.productivebees.common.recipe.CentrifugeRecipe;
import cy.jdkdigital.productivebees.common.recipe.TagOutputRecipe;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PB 1.20.1 CentrifugeRecipe 兼容助手
 * <br/>
 * PB 1.20.1 与 1.21 的 CentrifugeRecipe API 差异集中适配：
 * <ul>
 *   <li>{@code getFluidOutputs()} — 1.20.1 返回 {@code Pair<Fluid, Integer>}，
 *       1.21 返回 FluidStack。本类提供 {@link #getFluidOutput} 统一为 FluidStack。</li>
 *   <li>{@code fluidOutput} 字段 — 1.20.1 为 {@code Pair<String, Integer>}（流体名→数量），
 *       1.21 为 SizedFluidIngredient。本类提供 {@link #fluidFromOutputMap} 直接从字段构造，
 *       与 {@link #scaleFluidOutput} 缩放。</li>
 *   <li>{@code itemOutput} 字段 — 1.20.1 为 {@code Map<Ingredient, IntArrayTag>}
 *       （IntArrayTag = [min, max, chance%]），1.21 为 {@code List<ChancedOutput>}。
 *       读取侧用 {@link RecipeOutputAdapter#fromItemOutput}，写入侧用 {@link #scaleItemOutputs}。</li>
 *   <li>构造器 — 1.20.1 为 {@code (id, ingredient, Map<Ingredient, IntArrayTag>, Pair<String, Integer>, int)}，
 *       1.21 无 id 参数。派生配方用 {@link #deriveRecipeId} 生成稳定 id。</li>
 * </ul>
 * 线程安全：所有方法均为无状态纯函数，可安全并发调用。
 */
public final class CentrifugeRecipeCompat {

	/** Wax 物品标签 — 复刻 PB 热力离心机 stripWax 行为（c:waxes） */
	private static final TagKey<Item> WAXES_TAG = TagKey.create(
			net.minecraft.core.registries.Registries.ITEM,
			ResourceLocation.fromNamespaceAndPath("c", "waxes"));

	private CentrifugeRecipeCompat() {
		// 工具类禁止实例化
	}

	/**
	 * 1.20.1 版 {@code recipe.getFluidOutputs()} 的 FluidStack 适配
	 * <br/>
	 * PB 1.20.1 的 {@code getFluidOutputs()} 返回 {@code Pair<Fluid, Integer>}，
	 * 无匹配流体时首元素可能为 {@link Fluids#EMPTY}。转换为 FluidStack 语义：
	 * 空流体或数量 {@code <= 0} 时返回 {@link FluidStack#EMPTY}。
	 *
	 * @param recipe PB离心配方
	 * @return 流体输出栈，永不为 null
	 */
	public static FluidStack getFluidOutput(CentrifugeRecipe recipe) {
		Pair<Fluid, Integer> pair = recipe.getFluidOutputs();
		if (pair == null || pair.getFirst() == null || pair.getSecond() == null) {
			return FluidStack.EMPTY;
		}
		Fluid fluid = pair.getFirst();
		int amount = pair.getSecond();
		if (fluid == Fluids.EMPTY || fluid == null || amount <= 0) {
			return FluidStack.EMPTY;
		}
		return new FluidStack(fluid, amount);
	}

	/**
	 * 从 {@code recipe.fluidOutput} 字段（{@code Pair<String, Integer>} 流体名→数量）直接构造 FluidStack
	 * <br/>
	 * 复刻 PB 1.20.1 {@code getFluidOutputs()} 的解析顺序：
	 * 对流体名先走 {@link TagOutputRecipe#getPreferredFluidByMod}（mod 偏好），
	 * 命中即返回；否则回退 {@link TagOutputRecipe#getAllFluidsFromName} 首个流体。
	 * 与 PB 原版的差异：原版解析失败时返回 null，本方法统一返回 {@link FluidStack#EMPTY}。
	 *
	 * @param fluidOutput 配方流体输出字段，可为 null
	 * @return 构造的 FluidStack，无匹配流体时返回 EMPTY
	 */
	public static FluidStack fluidFromOutputMap(@Nullable Pair<String, Integer> fluidOutput) {
		if (fluidOutput == null) {
			return FluidStack.EMPTY;
		}
		String name = fluidOutput.getFirst();
		Integer amountObj = fluidOutput.getSecond();
		if (name == null || name.isEmpty() || amountObj == null || amountObj <= 0) {
			return FluidStack.EMPTY;
		}
		int amount = amountObj;
		Fluid preferred = TagOutputRecipe.getPreferredFluidByMod(name);
		if (preferred != Fluids.EMPTY) {
			return new FluidStack(preferred, amount);
		}
		List<Fluid> fluids = TagOutputRecipe.getAllFluidsFromName(name);
		if (!fluids.isEmpty()) {
			return new FluidStack(fluids.get(0), amount);
		}
		return FluidStack.EMPTY;
	}

	/**
	 * 构造 IntArrayTag（PB 1.20.1 itemOutput 值格式）
	 *
	 * @param min    最小产出
	 * @param max    最大产出
	 * @param chance 产出概率（0.0-1.0 浮点数，内部转为 0-100 整数百分比）
	 * @return IntArrayTag，格式 [min, max, chance%]
	 */
	public static IntArrayTag toTag(int min, int max, float chance) {
		return new IntArrayTag(new int[]{min, max, Math.round(chance * 100)});
	}

	/**
	 * 判断 ingredient 是否为 Wax 产出 — 复刻 PB 热力离心机 stripWax=true 行为
	 * <br/>
	 * 通过 ingredient 的所有可能物品检查 {@code c:waxes} 标签。
	 *
	 * @param ingredient 配方输出 ingredient
	 * @return true 表示该产出是 Wax（应在蜜脾块配方中过滤）
	 */
	public static boolean isWaxIngredient(Ingredient ingredient) {
		for (ItemStack stack : ingredient.getItems()) {
			if (stack.is(WAXES_TAG)) return true;
		}
		return false;
	}

	/**
	 * 缩放 itemOutput 数量并可选过滤 Wax — 用于蜜脾块配方派生
	 * <br/>
	 * 读取每个条目的 [min, max, chance%]，min/max 乘以 multiplier（饱和运算）后重建 IntArrayTag。
	 * 使用 LinkedHashMap 保持条目顺序（与原配方输出顺序一致）。
	 *
	 * @param itemOutput 原配方 itemOutput，可为 null
	 * @param multiplier 倍率（蜜脾块倍率）
	 * @param stripWax   是否过滤 Wax 产出
	 * @return 缩放后的 itemOutput，永不为 null（输入 null 返回空 Map）
	 */
	public static Map<Ingredient, IntArrayTag> scaleItemOutputs(
			@Nullable Map<Ingredient, IntArrayTag> itemOutput, int multiplier, boolean stripWax) {
		if (itemOutput == null || itemOutput.isEmpty()) {
			return new LinkedHashMap<>(0);
		}
		Map<Ingredient, IntArrayTag> result = new LinkedHashMap<>(itemOutput.size());
		for (Map.Entry<Ingredient, IntArrayTag> entry : itemOutput.entrySet()) {
			if (stripWax && isWaxIngredient(entry.getKey())) continue;
			ChancedOutput chanced = RecipeOutputAdapter.fromTag(entry.getKey(), entry.getValue());
			result.put(entry.getKey(), toTag(
					SaturatingMath.saturatingToInt(
							SaturatingMath.saturatingMultiply(chanced.min(), multiplier)),
					SaturatingMath.saturatingToInt(
							SaturatingMath.saturatingMultiply(chanced.max(), multiplier)),
					chanced.chance()));
		}
		return result;
	}

	/**
	 * 缩放 fluidOutput 数量（饱和运算）
	 *
	 * @param fluidOutput 原配方流体输出（流体名→数量），可为 null
	 * @param multiplier  倍率
	 * @return 缩放后的流体输出，输入 null 返回 null
	 */
	@Nullable
	public static Pair<String, Integer> scaleFluidOutput(
			@Nullable Pair<String, Integer> fluidOutput, int multiplier) {
		if (fluidOutput == null) {
			return null;
		}
		Integer amountObj = fluidOutput.getSecond();
		int amount = amountObj == null ? 0 : amountObj;
		return Pair.of(fluidOutput.getFirst(),
				SaturatingMath.saturatingToInt(
						SaturatingMath.saturatingMultiply(amount, multiplier)));
	}

	/**
	 * 构造单条目 fluidOutput（1.20.1 字段格式：流体名→数量）
	 *
	 * @param fluidName 流体名（可为 tag 名，PB 内部解析）
	 * @param amount    流体数量 mB
	 * @return 单条目 Pair
	 */
	public static Pair<String, Integer> fluidOutputMap(String fluidName, int amount) {
		return Pair.of(fluidName, amount);
	}

	/**
	 * 生成派生配方的稳定 ID
	 * <br/>
	 * 派生配方（蜜脾块配方）不注册到 RecipeManager，id 仅用于 {@code getId()} 日志与 JEI 展示。
	 * 格式：{@code productivebeesgenesis:<base namespace>_<base path>_<suffix>}，
	 * base 的 namespace/path 均为合法 ResourceLocation 字符，下划线拼接保持合法。
	 *
	 * @param base   原配方 ID（或物品 ID）
	 * @param suffix 派生后缀（如 "comb_block"）
	 * @return 派生配方 ID
	 */
	public static ResourceLocation deriveRecipeId(ResourceLocation base, String suffix) {
		return ResourceLocation.fromNamespaceAndPath(ProductiveBeesGenesis.MOD_ID,
				base.getNamespace() + "_" + base.getPath() + "_" + suffix);
	}
}
