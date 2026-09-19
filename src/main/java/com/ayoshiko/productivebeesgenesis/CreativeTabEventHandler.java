package com.ayoshiko.productivebeesgenesis;

import com.ayoshiko.productivebeesgenesis.config.ModConfig;
import com.ayoshiko.productivebeesgenesis.util.PBConstants;
import cy.jdkdigital.productivebees.ProductiveBees;
import com.ayoshiko.productivebeesgenesis.util.BeeTypeNbt;
import cy.jdkdigital.productivebees.init.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.common.util.MutableHashedLinkedMap;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
	 * 创造模式物品栏事件处理器
	 * <br/>
	 * 根据万象创世蜜蜂的启用状态动态调整创造模式物品栏内容：
	 * <ul>
	 *   <li>启用时：向 PB 创造栏添加万象创世蜜脾和蜜脾块（createComb=false 时 PB 不会自动添加）</li>
	 *   <li>禁用时：从 PB 标签页和刷怪蛋标签页移除万象创世刷怪蛋</li>
	 * </ul>
	 * <p>
	 * 原理：拦截 {@link BuildCreativeModeTabContentsEvent} 事件，
	 * 启用时使用 {@link BuildCreativeModeTabContentsEvent#accept} 添加物品，
	 * 禁用时通过 {@link BuildCreativeModeTabContentsEvent#getEntries} 拿到条目表后
	 * 调用 {@link MutableHashedLinkedMap#remove} 移除物品。
	 * <p>
	 * 1.20.1 迁移说明：Forge 47 的事件类没有 1.21 的 {@code getParentEntries}/
	 * {@code getSearchEntries}/{@code remove(ItemStack, TabVisibility)} 方法，
	 * 父栏与搜索栏共用同一 {@link MutableHashedLinkedMap} 条目表
	 * （以 {@code TabVisibility} 为值区分可见性），移除时无需区分两种列表。
	 */
@EventBusSubscriber(modid = ProductiveBeesGenesis.MOD_ID)
public final class CreativeTabEventHandler {

	private CreativeTabEventHandler() {}

	/**
	 * 拦截创造模式物品栏内容构建事件
	 * <br/>
	 * 根据配置决定操作：
	 * <ul>
	 *   <li>万象创世启用：向 PB 创造栏添加蜜脾和蜜脾块</li>
	 *   <li>万象创世禁用：从创造栏移除万象创世刷怪蛋</li>
	 * </ul>
	 */
	@SubscribeEvent
	public static void onBuildCreativeModeTabContents(BuildCreativeModeTabContentsEvent event) {
		// 检查配置是否加载
		if (!ModConfig.areServerSpecsLoaded()) {
			return; // 配置未加载，不干预（保持默认行为）
		}

		if (ModConfig.SERVER.myriadCreationsEnabled.get()) {
			// 万象创世启用：向 PB 创造栏添加蜜脾和蜜脾块
			if (event.getTabKey().equals(ProductiveBees.TAB_KEY)) {
				addMyriadCreationsCombs(event);
			}
			return; // 启用时不执行移除逻辑
		}

		// 万象创世禁用：只处理 ProductiveBees 的标签页和刷怪蛋标签页
		if (!event.getTabKey().equals(ProductiveBees.TAB_KEY)
				&& !event.getTabKey().equals(net.minecraft.world.item.CreativeModeTabs.SPAWN_EGGS)) {
			return;
		}

		// 获取万象创世蜜蜂的刷怪蛋物品
		ResourceLocation myriadSpawnEggId = ResourceLocation.fromNamespaceAndPath(
				PBConstants.PRODUCTIVE_BEES_MOD_ID, "spawn_egg_" + PBConstants.MYRIADCREATIONS_TYPE.getPath());
		var myriadSpawnEgg = BuiltInRegistries.ITEM.get(myriadSpawnEggId);

		// 从条目表中移除万象创世刷怪蛋（同时覆盖父栏与搜索栏两种可见性）
		removeSpawnEggsFromEntries(event.getEntries(), myriadSpawnEgg);
	}

	/**
	 * 向 PB 创造栏添加万象创世蜜脾和蜜脾块
	 * <br/>
	 * 当 createComb=false 时，PB 不会自动将万象创世蜜脾加入创造栏，需在此手动构造
	 * 携带 bee_type 数据组件的物品栈并添加。PB 的 configurable_honeycomb /
	 * configurable_comb_block 物品据此组件识别蜜脾种类，使其在创造栏和 JEI 中正确显示。
	 * <p>
	 * 重复添加保护：PB 创造栏可能已包含同物品但不同 bee_type 的条目（或相同 bee_type），
	 * 直接 event.accept 会触发 "already exists in the tab's list" 异常。
	 * 此处先遍历已有条目，匹配 ItemStack.matches（含组件比较），仅在不存在时添加。
	 */
	private static void addMyriadCreationsCombs(BuildCreativeModeTabContentsEvent event) {
		ItemStack honeycomb = new ItemStack(ModItems.CONFIGURABLE_HONEYCOMB.get());
		BeeTypeNbt.setBeeType(honeycomb, PBConstants.MYRIADCREATIONS_TYPE);
		safeAccept(event, honeycomb);

		ItemStack combBlock = new ItemStack(ModItems.CONFIGURABLE_COMB_BLOCK.get());
		BeeTypeNbt.setBeeType(combBlock, PBConstants.MYRIADCREATIONS_TYPE);
		safeAccept(event, combBlock);
	}

	/**
	 * 安全添加物品栈到创造栏 — 先检查是否已存在相同条目（含数据组件），避免重复添加异常
	 */
	private static void safeAccept(BuildCreativeModeTabContentsEvent event, ItemStack stack) {
		// 1.20.1：遍历条目表（含父栏与搜索栏全部条目），逐项精确匹配
		for (Map.Entry<ItemStack, CreativeModeTab.TabVisibility> entry : event.getEntries()) {
			if (ItemStack.matches(entry.getKey(), stack)) {
				return;
			}
		}
		event.accept(stack);
	}

	/**
	 * 从条目表中移除万象创世蜜蜂的刷怪蛋
	 * <br/>
	 * 先对键做快照（避免移除时触发 ConcurrentModificationException），
	 * 再逐项检查是否为万象创世刷怪蛋并调用 {@link MutableHashedLinkedMap#remove}。
	 */
	private static void removeSpawnEggsFromEntries(
			MutableHashedLinkedMap<ItemStack, CreativeModeTab.TabVisibility> entries,
			net.minecraft.world.item.Item myriadSpawnEgg) {
		// 遍历键快照，避免 remove 触发并发修改异常
		List<ItemStack> snapshot = new ArrayList<>();
		for (Map.Entry<ItemStack, CreativeModeTab.TabVisibility> entry : entries) {
			snapshot.add(entry.getKey());
		}
		for (ItemStack stack : snapshot) {
			if (stack.isEmpty()) continue;
			if (isMyriadCreationsSpawnEgg(stack, myriadSpawnEgg)) {
				entries.remove(stack);
			}
		}
	}

	/**
	 * 检查物品栈是否为万象创世蜜蜂的刷怪蛋
	 * <br/>
	 * 两种情况：
	 * 1. 直接是 spawn_egg_myriadcreations 物品
	 * 2. 是 configurable_spawn_egg 但携带了万象创世蜜蜂类型数据
	 */
	private static boolean isMyriadCreationsSpawnEgg(ItemStack stack, net.minecraft.world.item.Item myriadSpawnEgg) {
		if (stack.getItem() instanceof SpawnEggItem) {
			// 直接匹配万象创世刷怪蛋
			if (myriadSpawnEgg != null && stack.getItem() == myriadSpawnEgg) {
				return true;
			}

			// 检查是否为 Configurable Spawn Egg 携带万象创世类型
			if (stack.getItem() == ModItems.CONFIGURABLE_SPAWN_EGG.get()) {
				net.minecraft.nbt.CompoundTag tag = stack.getTag();
				if (tag != null && tag.contains("EntityTag")) {
					net.minecraft.nbt.CompoundTag entityTag = tag.getCompound("EntityTag");
					String beeType = entityTag.getString("type");
					if (PBConstants.MYRIADCREATIONS_TYPE_STRING.equals(beeType)) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
