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
 * Essence Conversion upgrade — compresses/reduces produce items
 * based on crafting recipes (e.g. 9 ingots -> 1 block).
 * <br/>
 * Functional upgrade (max 1 installed). When installed in an apiary or
 * centrifuge, pending outputs are passed through
 * {@link com.ayoshiko.productivebeesgenesis.util.EssenceConversionUpgradeHelper}
 * before being committed to the output inventory.
 *
 * @since 1.0.7
 */
public final class EssenceConversionUpgradeItem extends UpgradeItem {

	public EssenceConversionUpgradeItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Level level,
			List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable(
				"item.productivebeesgenesis.essence_conversion_upgrade.description.machine")
				.withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable(
				"item.productivebeesgenesis.essence_conversion_upgrade.description.rule")
				.withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable(
				"item.productivebeesgenesis.essence_conversion_upgrade.description.limit")
				.withStyle(ChatFormatting.DARK_GRAY));
		super.appendHoverText(stack, level, tooltip, flag);
	}
}
