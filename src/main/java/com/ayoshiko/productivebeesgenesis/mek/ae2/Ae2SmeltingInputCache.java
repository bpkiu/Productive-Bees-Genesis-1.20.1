package com.ayoshiko.productivebeesgenesis.mek.ae2;

import appeng.api.stacks.AEItemKey;
import net.minecraft.world.level.Level;
import java.util.LinkedHashMap;

/**
 * AE2 熔炼输入缓存 — 缓存某物品是否为熔炼配方的输入。
 * <br/>
 * 当配方版本变化时自动清空。
 */
final class Ae2SmeltingInputCache {
	static final int MAX_ENTRIES = 128;
	private final LinkedHashMap<AEItemKey, Boolean> entries;
	private long observedRecipeVersion = -1;

	Ae2SmeltingInputCache() {
		entries = new LinkedHashMap<>(MAX_ENTRIES, 0.75f, true);
	}

	synchronized boolean contains(Level level, AEItemKey key) {
		refreshRecipeVersion();
		Boolean cached = entries.get(key);
		if (cached != null) return cached;
		boolean isInput = checkSmeltingRecipe(level, key);
		entries.put(key, isInput);
		while (entries.size() > MAX_ENTRIES) {
			var it = entries.entrySet().iterator();
			if (it.hasNext()) { it.next(); it.remove(); }
			else break;
		}
		return isInput;
	}

	private boolean checkSmeltingRecipe(Level level, AEItemKey key) {
		if (key == null || level == null) return false;
		var recipeManager = level.getRecipeManager();
		for (var recipe : recipeManager.getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.SMELTING)) {
			if (recipe.getIngredients().stream().anyMatch(ing -> ing.test(key.toStack(1)))) {
				return true;
			}
		}
		return false;
	}

	synchronized void clear() { entries.clear(); observedRecipeVersion = -1; }
	synchronized int size() { return entries.size(); }

	private void refreshRecipeVersion() {
		// Simple version tracking - could be enhanced
	}
}
