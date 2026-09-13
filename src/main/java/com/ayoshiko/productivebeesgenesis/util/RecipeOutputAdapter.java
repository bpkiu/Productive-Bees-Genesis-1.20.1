package com.ayoshiko.productivebeesgenesis.util;

import net.minecraft.nbt.IntArrayTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PB 1.20.1 配方输出适配器 — 将 IntArrayTag 格式转换为本地 {@link ChancedOutput}。
 * <br/>
 * PB 1.20.1 的 {@code TagOutputRecipe} 使用 {@code Map<ItemStack, IntArrayTag>} 存储产物，
 * IntArrayTag 内部格式为 {@code [min, max, chance]}（chance 为 0-100 整数百分比）。
 * 本工具类将此格式转换为类型安全的 {@link ChancedOutput}，使迁移后的业务逻辑保持 1.21.1 语义。
 * <p>
 * 线程安全：所有方法均为无状态纯函数，可安全并发调用。
 */
public final class RecipeOutputAdapter {

	private RecipeOutputAdapter() {
		// 工具类禁止实例化
	}

	/** IntArrayTag 中 chance 的默认值（百分比，0-100） */
	private static final int DEFAULT_CHANCE_PERCENT = 100;

	/** IntArrayTag 中 min 的默认值 */
	private static final int DEFAULT_MIN = 1;

	/** IntArrayTag 中 max 的默认值 */
	private static final int DEFAULT_MAX = 1;

	/**
	 * 将单个 IntArrayTag 转换为 ChancedOutput。
	 * <br/>
	 * IntArrayTag 格式：{@code [min, max, chance]}，chance 为 0-100 整数百分比。
	 * 转换后 chance 为 0.0-1.0 浮点数。
	 *
	 * @param ingredient 产物原料（由调用方提供，因为 Map key 可能是 ItemStack 或 Ingredient）
	 * @param tag        IntArrayTag，包含 [min, max, chance]
	 * @return ChancedOutput，tag 为 null 时返回默认值（min=1, max=1, chance=1.0）
	 */
	public static ChancedOutput fromTag(Ingredient ingredient, @Nullable IntArrayTag tag) {
		if (tag == null) {
			return new ChancedOutput(ingredient, DEFAULT_MIN, DEFAULT_MAX, 1.0f);
		}
		int[] arr = tag.getAsIntArray();
		int min = arr.length > 0 ? arr[0] : DEFAULT_MIN;
		int max = arr.length > 1 ? arr[1] : DEFAULT_MAX;
		int chancePercent = arr.length > 2 ? arr[2] : DEFAULT_CHANCE_PERCENT;
		return new ChancedOutput(ingredient, min, max, chancePercent / 100.0f);
	}

	/**
	 * 将 {@code getRecipeOutputs()} 返回的 {@code Map<ItemStack, IntArrayTag>}
	 * 转换为 {@code Map<ItemStack, ChancedOutput>}。
	 * <br/>
	 * ItemStack key 保持不变，IntArrayTag value 转换为 ChancedOutput，
	 * 其中 ingredient 字段由 ItemStack 包装为 {@link Ingredient#of(ItemStack...)}。
	 *
	 * @param recipeOutputs PB 1.20.1 的 getRecipeOutputs() 结果，可为 null
	 * @return 不可变的 ChancedOutput 映射，永不为 null（输入 null 返回空 Map）
	 */
	public static Map<ItemStack, ChancedOutput> fromRecipeOutputs(
			@Nullable Map<ItemStack, IntArrayTag> recipeOutputs) {
		if (recipeOutputs == null || recipeOutputs.isEmpty()) {
			return Map.of();
		}
		Map<ItemStack, ChancedOutput> result = new LinkedHashMap<>(recipeOutputs.size());
		for (Map.Entry<ItemStack, IntArrayTag> entry : recipeOutputs.entrySet()) {
			ItemStack stack = entry.getKey();
			Ingredient ingredient = Ingredient.of(stack);
			result.put(stack, fromTag(ingredient, entry.getValue()));
		}
		return result;
	}

	/**
	 * 将 {@code TagOutputRecipe.itemOutput} 字段的 {@code Map<Ingredient, IntArrayTag>}
	 * 转换为 {@code List<ChancedOutput>}。
	 * <br/>
	 * 保留插入顺序（使用 {@link LinkedHashMap} 遍历），与 1.21.1 的 List 语义一致。
	 *
	 * @param itemOutput PB 1.20.1 的 TagOutputRecipe.itemOutput 字段值，可为 null
	 * @return ChancedOutput 列表，永不为 null（输入 null 返回空列表）
	 */
	public static List<ChancedOutput> fromItemOutput(
			@Nullable Map<Ingredient, IntArrayTag> itemOutput) {
		if (itemOutput == null || itemOutput.isEmpty()) {
			return new ArrayList<>(0);
		}
		List<ChancedOutput> result = new ArrayList<>(itemOutput.size());
		for (Map.Entry<Ingredient, IntArrayTag> entry : itemOutput.entrySet()) {
			result.add(fromTag(entry.getKey(), entry.getValue()));
		}
		return result;
	}
}
