package com.ayoshiko.productivebeesgenesis.apiary;

import com.ayoshiko.productivebeesgenesis.util.BeeInfoHelper.FlowerPreference;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 琥珀封存生物花朵匹配工具（纯静态，无状态）
 * <br/>
 * 从 {@link FeederSlotManager} 拆分而来，职责（SRP）：Butcher / Rancher 等
 * entity_type 蜜蜂的花朵判定 —— 从琥珀方块物品的实体数据组件读取封存实体 ID，
 * 与花朵偏好声明的实体 ID 或实体类型标签匹配，全程不实例化实体。
 */
final class AmberEntityFlowerHelper {

	private AmberEntityFlowerHelper() {
	}

	/**
	 * 检查喂食器中是否有琥珀封存的实体匹配指定的花朵偏好（entity_types 类型蜜蜂适用）
	 * <br/>
	 * 与 {@link FeederSlotManager#hasValidFlower} 的缓存联动，只在喂食槽内容变化后首次调用时计算。
	 *
	 * @param slots 喂食槽列表
	 * @param pref  花朵偏好
	 * @return true 如果任意喂食槽包含匹配实体的琥珀方块
	 */
	static boolean hasContainedEntityAmberMatching(List<FeederInventorySlot> slots, FlowerPreference pref) {
		String entityId = pref.flowerItem();
		if (!entityId.isEmpty()) {
			boolean inverse = pref.inverseFlower();
			ResourceLocation expected;
			try {
				expected = ResourceLocation.parse(entityId);
			} catch (RuntimeException ignored2) {
				return false;
			}
			for (FeederInventorySlot slot : slots) {
				ResourceLocation contained = getAmberEntityId(slot.getStack());
				if (contained != null && expected.equals(contained) != inverse) return true;
			}
			return false;
		}

		String tagName = pref.flowerTag();
		if (tagName.isEmpty()) return false;
		boolean inverse = pref.inverseFlower();
		if (tagName.charAt(0) == '!') {
			inverse = true;
			tagName = tagName.substring(1);
		}
		try {
			TagKey<EntityType<?>> entityTag = TagKey.create(
					BuiltInRegistries.ENTITY_TYPE.key(), ResourceLocation.parse(tagName));
			return hasContainedEntityAmberMatching(slots, entityTag, inverse);
		} catch (RuntimeException ignored3) {
			return false;
		}
	}

	static boolean hasContainedEntityAmberMatching(List<FeederInventorySlot> slots,
			TagKey<EntityType<?>> entityTag, boolean inverse) {
		for (FeederInventorySlot slot : slots) {
			ResourceLocation entityId = getAmberEntityId(slot.getStack());
			if (entityId == null) continue;
			var entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId);
			if (entityType.isPresent()
					&& BuiltInRegistries.ENTITY_TYPE.getHolder(net.minecraft.resources.ResourceKey.create(BuiltInRegistries.ENTITY_TYPE.key(), BuiltInRegistries.ENTITY_TYPE.getKey(entityType.get())))
							.map(h -> h.is(entityTag)).orElse(false) != inverse) return true;
		}
		return false;
	}

	/**
	 * 为一次 Wanna Bee 生产批次构建有效 PB 琥珀的实体数据快照。
	 * <br/>
	 * 快照按槽位保留候选（相同琥珀占用多个槽位时仍具有对应权重），且直接复用
	 * ItemStack 中不可变的 {@link CompoundTag}，不在扫描阶段复制实体 NBT。
	 *
	 * @param slots 喂食槽列表
	 * @return 实体数据快照；无有效琥珀时返回空列表
	 */
	static List<CompoundTag> getAmberEntityDataSnapshot(List<FeederInventorySlot> slots) {
		List<CompoundTag> candidates = null;
		for (FeederInventorySlot slot : slots) {
			CompoundTag entityData = getAmberEntityData(slot.getStack());
			if (entityData == null || readEntityId(entityData) == null) continue;
			if (candidates == null) candidates = new java.util.ArrayList<>(slots.size());
			candidates.add(entityData);
		}
		return candidates == null ? List.of() : candidates;
	}

	private static CompoundTag getAmberEntityData(ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return null;
		ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
		if (!"productivebees".equals(blockId.getNamespace()) || !"amber".equals(blockId.getPath())) return null;
		// 兼容旧版本 ModBlocks.AMBER 判定："amber" 方块路径同样视为 WannaBee 的封存生物琥珀
		CompoundTag entityData = stack.getOrCreateTag().getCompound("EntityTag");
		return entityData.isEmpty() ? null : entityData;
	}

	private static ResourceLocation getAmberEntityId(ItemStack stack) {
		CompoundTag entityData = getAmberEntityData(stack);
		return entityData == null ? null : readEntityId(entityData);
	}

	private static ResourceLocation readEntityId(CompoundTag entityData) {
		String idStr = entityData.getString("id");
		if (idStr.isEmpty()) return null;
		try {
			return ResourceLocation.parse(idStr);
		} catch (RuntimeException ignored) {
			return null;
		}
	}
}
