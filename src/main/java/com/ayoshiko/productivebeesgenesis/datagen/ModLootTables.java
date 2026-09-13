package com.ayoshiko.productivebeesgenesis.datagen;

import com.ayoshiko.productivebeesgenesis.init.ModBlocks;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
	 * 战利品表数据生成器
	 * <br/>
	 * 所有MEK离心机方块掉落自身
	 */
public final class ModLootTables {

	public static LootTableProvider create(net.minecraft.data.PackOutput output) {
		// Forge 1.20.1：LootTableProvider 构造器无 lookupProvider 参数
		// （NeoForge 1.21.1: new LootTableProvider(output, ..., lookupProvider)）
		return new LootTableProvider(output, Collections.emptySet(),
				List.of(new LootTableProvider.SubProviderEntry(ModBlockLootSubProvider::new, LootContextParamSets.BLOCK)));
	}

	private static class ModBlockLootSubProvider extends BlockLootSubProvider {
		protected ModBlockLootSubProvider() {
			// Forge 1.20.1：BlockLootSubProvider 构造器无 registries 参数
			super(Set.of(), FeatureFlags.REGISTRY.allFlags());
		}

		@Override
	protected void generate() {
		for (var blockHolder : ModBlocks.BLOCKS.getEntries()) {
			ResourceLocation id = blockHolder.getId();
			// F9: 跳过 EM/ME/EME 条件方块 — 由 ConditionalBlockLootProvider 生成带条件的战利品表
			if (ModLoadedConditionResolver.resolveModId(id) != null) {
				continue;
			}
			this.dropSelf(blockHolder.get());
		}
	}

	@Override
	protected Iterable<Block> getKnownBlocks() {
		// F9: 排除 EM/ME/EME 条件方块 — 其战利品表由 ConditionalBlockLootProvider 生成，
		// 基类 generate() 会校验 getKnownBlocks() 中每个方块是否生成了战利品表，必须同步过滤
		return ModBlocks.BLOCKS.getEntries().stream()
				.filter(holder -> ModLoadedConditionResolver.resolveModId(holder.getId()) == null)
				.map(sup -> (Block) sup.get())::iterator;
	}
	}
}
