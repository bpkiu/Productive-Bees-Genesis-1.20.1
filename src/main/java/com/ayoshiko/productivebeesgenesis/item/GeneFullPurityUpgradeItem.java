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
 * Gene Full-Purity upgrade — locks gene sampler output purity to 4 (maximum),
 * instead of the default random 1-4 range.
 * <br/>
 * Functional upgrade (max 1 installed). Apiary-only; centrifuges reject it. Pairs with
 * the gene sampler: when installed, {@link com.ayoshiko.productivebeesgenesis.apiary.GeneSampler}
 * is invoked with {@code fullPurity=true}.
 *
 * @since 1.0.7
 */
public final class GeneFullPurityUpgradeItem extends UpgradeItem {

	public GeneFullPurityUpgradeItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Level level,
			List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable(
				"item.productivebeesgenesis.gene_full_purity_upgrade.description.effect")
				.withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable(
				"item.productivebeesgenesis.gene_full_purity_upgrade.description.limit")
				.withStyle(ChatFormatting.DARK_GRAY));
		super.appendHoverText(stack, level, tooltip, flag);
	}
}