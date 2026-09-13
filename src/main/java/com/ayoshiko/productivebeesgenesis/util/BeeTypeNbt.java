package com.ayoshiko.productivebeesgenesis.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * PB 1.20.1 蜜蜂类型 NBT 读写工具
 * <p>
 * 替代 1.21.1 的 {@code ModDataComponents.BEE_TYPE} DataComponent API。
 * 在 1.20.1 中，蜜蜂类型存储在物品 NBT 的 {@code EntityTag.type} 字段，
 * 与 PB 离心机配方的 forge:nbt 成分匹配格式一致。
 */
public final class BeeTypeNbt {

	private BeeTypeNbt() {
	}

	/**
	 * 设置物品的蜜蜂类型（替代 {@code stack.set(ModDataComponents.BEE_TYPE.get(), beeType)}）
	 */
	public static void setBeeType(ItemStack stack, ResourceLocation beeType) {
		CompoundTag tag = stack.getOrCreateTag();
		CompoundTag entityTag = new CompoundTag();
		entityTag.putString("type", beeType.toString());
		tag.put("EntityTag", entityTag);
	}

	/**
	 * 读取物品的蜜蜂类型（替代 {@code stack.get(ModDataComponents.BEE_TYPE.get())}）
	 *
	 * @return 蜜蜂类型，无数据时返回 null
	 */
	public static ResourceLocation getBeeType(ItemStack stack) {
		CompoundTag tag = stack.getTag();
		if (tag == null || !tag.contains("EntityTag")) return null;
		CompoundTag entityTag = tag.getCompound("EntityTag");
		String type = entityTag.getString("type");
		if (type.isEmpty()) return null;
		return ResourceLocation.tryParse(type);
	}
}
