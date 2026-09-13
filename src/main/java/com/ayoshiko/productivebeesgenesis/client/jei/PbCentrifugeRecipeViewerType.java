package com.ayoshiko.productivebeesgenesis.client.jei;

import cy.jdkdigital.productivebees.common.recipe.CentrifugeRecipe;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * PB离心配方的Mekanism配方查看器类型
 * <br/>
 * 独立记录类（1.20.1兼容版：IRecipeViewerRecipeType接口在Mekanism 1.20.1中不存在），
 * 仅作为内部描述符用于JEI注册。保留所有字段和方法，不实现任何Mekanism接口。
 */
public record PbCentrifugeRecipeViewerType(
		ResourceLocation id,
		ItemLike iconItem,
		Component textComponent,
		Class<? extends CentrifugeRecipe> recipeClass,
		int xOffset,
		int yOffset,
		int width,
		int height,
		List<ItemLike> workstations
) {

	/**
	 * 构造PB离心配方查看器类型
	 *
	 * @param id            配方类型ID（用于JEI内部标识）
	 * @param iconItem      图标物品（显示在JEI分类标签上）
	 * @param textComponent 分类标题文本
	 * @param altWorkstations 工作站物品（作为催化剂注册）
	 */
	public PbCentrifugeRecipeViewerType(ResourceLocation id, ItemLike iconItem, Component textComponent,
										ItemLike... altWorkstations) {
		this(id, iconItem, textComponent, CentrifugeRecipe.class,
				-28, -16, 144, 70,
				altWorkstations.length == 0 ? List.of(iconItem) : List.of(altWorkstations));
	}

	public ResourceLocation id() {
		return id;
	}

	public Class<? extends CentrifugeRecipe> recipeClass() {
		return recipeClass;
	}

	/**
	 * PB离心配方不使用RecipeHolder包装
	 */
	public boolean requiresHolder() {
		return false;
	}

	public ItemStack iconStack() {
		return new ItemStack(iconItem);
	}

	@Nullable
	public ResourceLocation icon() {
		// 使用iconStack作为图标，不使用纹理路径
		return null;
	}

	public int xOffset() {
		return xOffset;
	}

	public int yOffset() {
		return yOffset;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	public List<ItemLike> workstations() {
		return workstations;
	}

	public Component getTextComponent() {
		return textComponent;
	}

	/**
	 * 兼容IHasTextComponent接口（部分Mekanism版本通过此接口获取文本）
	 */
	public Component getTranslationComponent() {
		return textComponent;
	}
}
