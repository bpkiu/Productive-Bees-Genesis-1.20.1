package com.ayoshiko.productivebeesgenesis.item;

import cy.jdkdigital.productivebees.common.item.UpgradeItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Raw Ore Smelting upgrade — automatically smelts raw ore items into ingots
 * using vanilla smelting or Mekanism recipes.
 * <br/>
 * Functional upgrade (max 1 installed). When installed in an apiary or
 * centrifuge, raw ore produce items are converted to their smelted ingot
 * form via {@link com.ayoshiko.productivebeesgenesis.util.RawOreSmeltingUpgradeHelper}
 * before being committed to the output inventory.
 *
 * @since 1.0.7
 */
public final class RawOreSmeltingUpgradeItem extends UpgradeItem {

	public RawOreSmeltingUpgradeItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Level level,
			List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable(
				"item.productivebeesgenesis.raw_ore_smelting_upgrade.description.machine")
				.withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable(
				"item.productivebeesgenesis.raw_ore_smelting_upgrade.description.rule")
				.withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable(
				"item.productivebeesgenesis.raw_ore_smelting_upgrade.description.limit")
				.withStyle(ChatFormatting.DARK_GRAY));
		super.appendHoverText(stack, level, tooltip, flag);
	}
}
