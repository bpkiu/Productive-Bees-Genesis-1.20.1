package com.ayoshiko.productivebeesgenesis.mixin.recipe;

import com.ayoshiko.productivebeesgenesis.util.BeeIngredientFallback;
import cy.jdkdigital.productivebees.common.recipe.BeeSpawningRecipe;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.common.util.Lazy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复 BeeSpawningRecipe 序列化崩溃（PB 12.6.0 / MC 1.20.1）
 * <p>
 * 原理：BeeSpawningRecipe.Serializer.toNetwork 对 output 中 null 的处理是跳过不写入，
 * 这会导致客户端读取的 output 数量与实际不符，引发 buffer 错乱或崩溃。
 * 此 Mixin 在 toNetwork 头部拦截，只要 output 列表中存在任何 null，
 * 就将全部 output 替换为单个 minecraft:bee fallback，并保留原 ingredient/spawnItem/biomes。
 * <p>
 * <b>1.20.1 字节码对齐</b>（javap 核实）：toNetwork(FriendlyByteBuf, T) 为实例方法，
 * 写入顺序为 ingredient → spawnItem → writeInt(output.size()) → 逐个
 * {@code Lazy#get().toNetwork(buf)} → writeUtf(biomes)。fallback 包严格按此顺序重放，
 * biomes 为 String（非 HolderSet），空值回退 "any"。
 * <p>
 * fallback 序列化逻辑统一抽取到 {@link BeeIngredientFallback} 工具类。
 */
@Mixin(targets = "cy.jdkdigital.productivebees.common.recipe.BeeSpawningRecipe$Serializer", remap = false)
public abstract class BeeSpawningRecipeSerializerMixin {

	@Inject(method = "toNetwork", at = @At("HEAD"), cancellable = true, remap = false)
	private void productivebeesgenesis$fallbackOnNullOutput(
			FriendlyByteBuf buffer, BeeSpawningRecipe recipe, CallbackInfo ci) {
		try {
			// 防御性检查：recipe.output 列表本身可能为 null，此时直接触发 fallback 逻辑
			boolean hasNull = recipe.output == null;
			if (!hasNull) {
				for (Lazy<cy.jdkdigital.productivebees.compat.jei.ingredients.BeeIngredient> beeOutput : recipe.output) {
					if (beeOutput == null || beeOutput.get() == null) {
						hasNull = true;
						break;
					}
				}
			}
			if (!hasNull) {
				return;
			}
			// 重放 PB 原版字段序（biomes 为 UTF 字符串）
			recipe.ingredient.toNetwork(buffer);
			recipe.spawnItem.toNetwork(buffer);
			buffer.writeInt(1); // 替换为单个 fallback 输出
			BeeIngredientFallback.writeFallbackBeeIngredient(buffer);
			buffer.writeUtf(recipe.biomes != null ? recipe.biomes : "any");
			ci.cancel();
		} catch (Exception e) {
			BeeIngredientFallback.logSerializationError("BeeSpawningRecipe", e);
			// 防御性 fallback：异常时写入完整 fallback 数据包并取消原方法，
			// 避免原 toNetwork 继续执行导致二次异常或返回部分填充的 Recipe。
			try {
				recipe.ingredient.toNetwork(buffer);
				recipe.spawnItem.toNetwork(buffer);
				buffer.writeInt(1);
				BeeIngredientFallback.writeFallbackBeeIngredient(buffer);
				buffer.writeUtf(recipe.biomes != null ? recipe.biomes : "any");
				ci.cancel();
			} catch (Exception ignored) {
				// 极端情况下（buffer 已损坏）放弃 cancel，交由上层协议层处理
			}
		}
	}
}
