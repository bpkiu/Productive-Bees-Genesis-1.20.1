package com.ayoshiko.productivebeesgenesis.mixin.recipe;

import com.ayoshiko.productivebeesgenesis.util.BeeIngredientFallback;
import cy.jdkdigital.productivebees.common.recipe.BeeBreedingRecipe;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
	 * 修复 BeeBreedingRecipe 序列化崩溃
	 * <p>
	 * 原理：BeeBreedingRecipe.Serializer.toNetwork 无 null 检查，当亲代/子代
	 * 任一 supplier 返回 null 时 NPE。此 Mixin 在 toNetwork 头部拦截，用 minecraft:bee 作为
	 * fallback 安全序列化三个 BeeIngredient（2 亲代 + 1 子代）。
	 * <p>
	 * 1.20.1 迁移说明：PB 12.6.0 的 BeeBreedingRecipe 字段为
	 * {@code List<Lazy<BeeIngredient>> ingredients}（2 个亲代）+ {@code offspring}，
	 * 无 1.21 的 parent1/parent2/parentDeathChance 字段；toNetwork 依次写入
	 * 2 个亲代 BeeIngredient.toNetwork + 1 个 offspring BeeIngredient.toNetwork，
	 * 无 parentDeathChance float 字段。
	 * <p>
	 * fallback 序列化逻辑统一抽取到 {@link BeeIngredientFallback} 工具类。
	 */
@Mixin(targets = "cy.jdkdigital.productivebees.common.recipe.BeeBreedingRecipe$Serializer", remap = false)
public abstract class BeeBreedingRecipeSerializerMixin {

	@Inject(method = "toNetwork", at = @At("HEAD"), cancellable = true, remap = false)
	private void productivebeesgenesis$fallbackOnNullIngredient(
			FriendlyByteBuf buffer, BeeBreedingRecipe recipe,
			CallbackInfo ci) {
		try {
			// PB 1.20.1（12.6.0）：亲代为 ingredients 列表（固定 2 项），子代为 offspring
			if (recipe.ingredients.size() != 2
					|| recipe.ingredients.get(0).get() == null
					|| recipe.ingredients.get(1).get() == null
					|| recipe.offspring.get() == null) {
				BeeIngredientFallback.writeFallbackBeeIngredient(buffer);
				BeeIngredientFallback.writeFallbackBeeIngredient(buffer);
				BeeIngredientFallback.writeFallbackBeeIngredient(buffer);
				ci.cancel();
			}
		} catch (Exception e) {
			// 修复 v15 P1: catch 块必须 ci.cancel()，否则原版 toNetwork 继续执行
			// 会再次访问 ingredients/offspring.get() 并抛出异常，导致玩家加入世界崩溃。
			// 写入 fallback 数据保证 buffer 格式完整：1.20.1 的 toNetwork 依次写入
			// 2 个亲代 BeeIngredient.toNetwork + 1 个 offspring BeeIngredient.toNetwork，
			// 无 parentDeathChance float 字段，故只需 3 条 fallback BeeIngredient，
			// 客户端能正确反序列化。
			BeeIngredientFallback.logSerializationError("BeeBreedingRecipe", e);
			BeeIngredientFallback.writeFallbackBeeIngredient(buffer);
			BeeIngredientFallback.writeFallbackBeeIngredient(buffer);
			BeeIngredientFallback.writeFallbackBeeIngredient(buffer);
			ci.cancel();
		}
	}
}
