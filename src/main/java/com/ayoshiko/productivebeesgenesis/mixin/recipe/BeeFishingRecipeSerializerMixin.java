package com.ayoshiko.productivebeesgenesis.mixin.recipe;

import com.ayoshiko.productivebeesgenesis.util.BeeIngredientFallback;
import cy.jdkdigital.productivebees.common.recipe.BeeFishingRecipe;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复 BeeFishingRecipe 序列化崩溃（PB 12.6.0 / MC 1.20.1）
 * <p>
 * 原理：BeeFishingRecipe.Serializer.toNetwork 无 null 检查，当 output.get() 返回 null 时 NPE。
 * 此 Mixin 在 toNetwork 头部拦截，用 minecraft:bee 作为 fallback 安全序列化。
 * <p>
 * <b>1.20.1 字节码对齐</b>（javap 核实）：toNetwork(FriendlyByteBuf, T) 为实例方法，
 * 写入顺序为 {@code output.get().toNetwork(buf)} → writeInt(biomes.size()) +
 * forEach(writeUtf) → writeDouble(chance)。注意 biomes 是 List&lt;String&gt;、chance 是 double。
 * <p>
 * fallback 序列化逻辑统一抽取到 {@link BeeIngredientFallback} 工具类。
 */
@Mixin(targets = "cy.jdkdigital.productivebees.common.recipe.BeeFishingRecipe$Serializer", remap = false)
public abstract class BeeFishingRecipeSerializerMixin {

	@Inject(method = "toNetwork", at = @At("HEAD"), cancellable = true, remap = false)
	private void productivebeesgenesis$fallbackOnNullIngredient(
			FriendlyByteBuf buffer, BeeFishingRecipe recipe, CallbackInfo ci) {
		try {
			if (recipe.output.get() == null) {
				BeeIngredientFallback.writeFallbackBeeIngredient(buffer);
				writeBiomesAndChance(buffer, recipe);
				ci.cancel();
			}
		} catch (Exception e) {
			BeeIngredientFallback.logSerializationError("BeeFishingRecipe", e);
			// 防御性 fallback：异常时写入完整 fallback 数据包并取消原方法，
			// 避免原 toNetwork 继续执行导致二次异常或返回部分填充的 Recipe。
			try {
				BeeIngredientFallback.writeFallbackBeeIngredient(buffer);
				writeBiomesAndChance(buffer, recipe);
				ci.cancel();
			} catch (Exception ignored) {
				// 极端情况下（buffer 已损坏）放弃 cancel，交由上层协议层处理
			}
		}
	}

	/** 按 PB 原版字节码顺序写入 biomes 列表与 chance（列表元素为 UTF 字符串） */
	private void writeBiomesAndChance(FriendlyByteBuf buffer, BeeFishingRecipe recipe) {
		java.util.List<String> biomes = recipe.biomes;
		int size = biomes != null ? biomes.size() : 0;
		buffer.writeInt(size);
		if (biomes != null) {
			for (String biome : biomes) {
				buffer.writeUtf(biome);
			}
		}
		buffer.writeDouble(recipe.chance);
	}
}
