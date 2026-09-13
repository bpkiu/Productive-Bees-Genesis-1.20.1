package com.ayoshiko.productivebeesgenesis.mixin.recipe;

import com.ayoshiko.productivebeesgenesis.util.BeeIngredientFallback;
import cy.jdkdigital.productivebees.common.recipe.BeeConversionRecipe;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复 BeeConversionRecipe 序列化崩溃（PB 12.6.0 / MC 1.20.1）
 * <p>
 * 原理：BeeConversionRecipe.Serializer.toNetwork 无 null 检查，当 source.get()
 * 或 result.get() 返回 null 时会 NPE。此 Mixin 在 toNetwork 头部拦截，若 source
 * 或 result 为 null 则用 minecraft:bee 作为 fallback 安全序列化，保留原 item 和 chance。
 * <p>
 * <b>1.20.1 字节码对齐</b>（javap 核实）：toNetwork(FriendlyByteBuf, T) 为实例方法，
 * 写入顺序为 source.toNetwork → result.toNetwork → item(Ingredient).toNetwork →
 * writeInt(chance)。注意 chance 是 int（旧代码误用 writeFloat）。
 * <p>
 * fallback 序列化逻辑统一抽取到 {@link BeeIngredientFallback} 工具类。
 */
@Mixin(targets = "cy.jdkdigital.productivebees.common.recipe.BeeConversionRecipe$Serializer", remap = false)
public abstract class BeeConversionRecipeSerializerMixin {

	@Inject(method = "toNetwork", at = @At("HEAD"), cancellable = true, remap = false)
	private void productivebeesgenesis$fallbackOnNullIngredient(
			FriendlyByteBuf buffer, BeeConversionRecipe recipe,
			CallbackInfo ci) {
		try {
			if (recipe.source.get() == null || recipe.result.get() == null) {
				writeFallbackPacket(buffer, recipe);
				ci.cancel();
			}
		} catch (Exception e) {
			BeeIngredientFallback.logSerializationError("BeeConversionRecipe", e);
			// 防御性 fallback：异常时写入完整 fallback 数据包并取消原方法，
			// 避免原 toNetwork 继续执行导致二次异常或返回部分填充的 Recipe。
			try {
				writeFallbackPacket(buffer, recipe);
				ci.cancel();
			} catch (Exception ignored) {
				// 极端情况下（buffer 已损坏）放弃 cancel，交由上层协议层处理
			}
		}
	}

	/** fallback 包体：source/result 用 minecraft:bee 替代，item/chance 保留原值（字段序同 PB 原版） */
	private void writeFallbackPacket(FriendlyByteBuf buffer, BeeConversionRecipe recipe) {
		// source
		BeeIngredientFallback.writeFallbackBeeIngredient(buffer);
		// result
		BeeIngredientFallback.writeFallbackBeeIngredient(buffer);
		// item（保留原值）
		recipe.item.toNetwork(buffer);
		// chance（保留原值，int）
		buffer.writeInt(recipe.chance);
	}
}
