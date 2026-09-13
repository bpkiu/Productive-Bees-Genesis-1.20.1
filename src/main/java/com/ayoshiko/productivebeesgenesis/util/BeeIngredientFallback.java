package com.ayoshiko.productivebeesgenesis.util;

import com.ayoshiko.productivebeesgenesis.ProductiveBeesGenesis;
import cy.jdkdigital.productivebees.compat.jei.ingredients.BeeIngredient;
import cy.jdkdigital.productivebees.compat.jei.ingredients.BeeIngredientFactory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * BeeIngredient fallback 序列化工具类（PB 12.6.0 / MC 1.20.1）
 * <br/>
 * 抽取 Serializer Mixin（BeeFishingRecipe / BeeSpawningRecipe）中重复的
 * fallback 序列化逻辑，消除代码重复，遵循 DRY 原则。
 * <p>
 * 应用场景：当 PB 的 configurable bees 在配方网络同步时刻尚未填充时，
 * BeeIngredient supplier 返回 null，原版 toNetwork 会 NPE。
 * 此工具类提供用 minecraft:bee 作为安全 fallback 的序列化方法。
 * <p>
 * <b>1.20.1 迁移说明</b>：PB 12.6.0 中 BeeIngredient/BeeIngredientFactory 位于
 * {@code compat.jei.ingredients} 包（旧 {@code common.crafting.ingredient} 包不存在）；
 * 配方网络 IO 使用 {@link FriendlyByteBuf}（RegistryFriendlyByteBuf 为 1.20.5+ API）。
 * <p>
 * 线程安全：所有方法为静态方法，仅依赖参数与不可变静态状态，无并发问题。
 *
 * @since 1.0.0
 */
public final class BeeIngredientFallback {

	/** fallback 用的原版蜜蜂 ResourceLocation */
	private static final ResourceLocation VANILLA_BEE_RL =
			ResourceLocation.parse(PBConstants.VANILLA_BEE_TYPE);

	private BeeIngredientFallback() {
		// 工具类禁止实例化
	}

	/**
	 * 获取 minecraft:bee 的 fallback BeeIngredient
	 * <br/>
	 * 原理：从 BeeIngredientFactory 已注册列表中查找 minecraft:bee，
	 * 若 PB 尚未注册原版蜜蜂则返回 null（由调用方处理）。
	 *
	 * @return minecraft:bee 对应的 BeeIngredient，未注册时返回 null
	 */
	public static BeeIngredient getFallback() {
		return BeeIngredientFactory.getOrCreateList().get(PBConstants.VANILLA_BEE_TYPE);
	}

	/**
	 * 写入 fallback BeeIngredient 的网络序列化格式
	 * <br/>
	 * 优先使用 {@link BeeIngredient#toNetwork(FriendlyByteBuf)} 写入；
	 * 若 fallback 为 null（极端情况：连原版蜜蜂都未注册），
	 * 则手动写入与 BeeIngredient.toNetwork 字节码一致的编码：
	 * <ol>
	 *   <li>UTF 字符串：蜜蜂实体类型 ID（minecraft:bee，供对端回查 EntityType 注册表）</li>
	 *   <li>ResourceLocation：蜜蜂类型 ID</li>
	 *   <li>boolean：是否为可配置蜜蜂（false）</li>
	 * </ol>
	 * 调用方负责保证 buffer 状态一致（写入完整一条 BeeIngredient 的数据）。
	 *
	 * @param buffer 网络字节缓冲区
	 */
	public static void writeFallbackBeeIngredient(FriendlyByteBuf buffer) {
		BeeIngredient fallback = getFallback();
		if (fallback != null) {
			fallback.toNetwork(buffer);
		} else {
			buffer.writeUtf(PBConstants.VANILLA_BEE_TYPE);
			buffer.writeResourceLocation(VANILLA_BEE_RL);
			buffer.writeBoolean(false);
		}
	}

	/**
	 * 记录 fallback 序列化失败的错误日志
	 * <br/>
	 * 统一 Mixin fallback 异常日志格式，便于调试与排查。
	 *
	 * @param recipeType 配方类型名称（如 "BeeSpawningRecipe"）
	 * @param e          捕获的异常
	 */
	public static void logSerializationError(String recipeType, Exception e) {
		ProductiveBeesGenesis.LOGGER.error("Mixin fallback 序列化 {} 时出错", recipeType, e);
	}
}
