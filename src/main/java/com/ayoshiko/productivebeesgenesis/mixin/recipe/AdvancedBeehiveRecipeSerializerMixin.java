package com.ayoshiko.productivebeesgenesis.mixin.recipe;

import com.ayoshiko.productivebeesgenesis.util.BeeIngredientFallback;
import cy.jdkdigital.productivebees.common.recipe.AdvancedBeehiveRecipe;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复 AdvancedBeehiveRecipe 序列化崩溃（PB 12.6.0 / MC 1.20.1）
 * <p>
 * 原理：当 BeeIngredientFactory 在配方网络同步时刻未填充 configurable bees 时，
 * AdvancedBeehiveRecipe.ingredient.get() 返回 null，原版 toNetwork 抛出
 * RuntimeException("Bee produce recipe ingredient missing") 导致玩家加入世界崩溃。
 * <p>
 * 此 Mixin 在 toNetwork 头部拦截，若 ingredient 为 null 则用 minecraft:bee 作为
 * fallback 安全序列化（写入空输出列表），保证 buffer 格式完整，客户端能正确反序列化。
 * <p>
 * <b>1.20.1 字节码对齐</b>（javap 核实）：toNetwork(FriendlyByteBuf, T) 为实例方法，
 * 写入顺序为 {@code ingredient.get().toNetwork(buf)} → writeInt(itemOutput.size()) →
 * itemOutput Map 内容。fallback 写空列表跳过 Map 段。
 * <p>
 * fallback 序列化逻辑统一抽取到 {@link BeeIngredientFallback} 工具类。
 */
@Mixin(targets = "cy.jdkdigital.productivebees.common.recipe.AdvancedBeehiveRecipe$Serializer", remap = false)
public abstract class AdvancedBeehiveRecipeSerializerMixin {

	@Inject(method = "toNetwork", at = @At("HEAD"), cancellable = true, remap = false)
	private void productivebeesgenesis$fallbackOnNullIngredient(
			FriendlyByteBuf buffer, AdvancedBeehiveRecipe recipe,
			CallbackInfo ci) {
		try {
			if (recipe.ingredient.get() == null) {
				writeFallbackPacket(buffer);
				ci.cancel();
			}
		} catch (Exception e) {
			// 修复 v15 P1: catch 块必须 ci.cancel()，否则原版 toNetwork 继续执行
			// 会再次访问 recipe.ingredient.get() 并抛出异常，导致玩家加入世界崩溃。
			BeeIngredientFallback.logSerializationError("AdvancedBeehiveRecipe", e);
			try {
				writeFallbackPacket(buffer);
				ci.cancel();
			} catch (Exception ignored) {
				// 极端情况下（buffer 已损坏）放弃 cancel，交由上层协议层处理
			}
		}
	}

	/** fallback 包体：minecraft:bee + 空输出列表（与 PB 原版字段序一致） */
	private void writeFallbackPacket(FriendlyByteBuf buffer) {
		BeeIngredientFallback.writeFallbackBeeIngredient(buffer);
		buffer.writeInt(0); // 空输出列表
	}
}
