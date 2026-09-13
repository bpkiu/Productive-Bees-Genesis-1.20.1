package com.ayoshiko.productivebeesgenesis.client.render.cosmic;

import com.ayoshiko.productivebeesgenesis.ProductiveBeesGenesis;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
	 * Cosmic 渲染类型定义
	 * <br/>
 * 继承 RenderStateShard 以便在 1.20.1 下访问 protected 静态 shard 常量（仅作访问桥，不实例化）。
 */
public class CosmicRenderTypes extends RenderStateShard {

	/** 2MB 顶点缓冲区大小 */
	private static final int BUFFER_SIZE_2MB = 0x200000;

	/** 访问桥构造器 — 永不实例化 */
	private CosmicRenderTypes() {
		super(ProductiveBeesGenesis.MOD_ID, () -> {
		}, () -> {
		});
	}

	public static final RenderType COSMIC = RenderType.create(
			ResourceLocation.fromNamespaceAndPath(ProductiveBeesGenesis.MOD_ID, "cosmic").toString(),
			DefaultVertexFormat.NEW_ENTITY,
			VertexFormat.Mode.QUADS,
			BUFFER_SIZE_2MB,
			true,
			false,
			RenderType.CompositeState.builder()
					.setShaderState(new RenderStateShard.ShaderStateShard(() -> CosmicShaders.COSMIC_SHADER))
					.setDepthTestState(LEQUAL_DEPTH_TEST)
					.setLightmapState(LIGHTMAP)
					.setWriteMaskState(COLOR_WRITE)
					.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
					.setTextureState(RenderUtils.COSMIC_TEXTURE_ISOLATED)
					.setLayeringState(RenderUtils.POLYGON_OFFSET_LAYERING)
					.createCompositeState(true));

	public static final RenderType COSMIC_ARMOR = RenderType.create(
			// 名称使用 "cosmic_armor" 区别于 COSMIC，便于调试与 profiler 阶段区分两套渲染类型
			ResourceLocation.fromNamespaceAndPath(ProductiveBeesGenesis.MOD_ID, "cosmic_armor").toString(),
			DefaultVertexFormat.NEW_ENTITY,
			VertexFormat.Mode.QUADS,
			BUFFER_SIZE_2MB,
			true,
			false,
			RenderType.CompositeState.builder()
					.setShaderState(new RenderStateShard.ShaderStateShard(() -> CosmicShaders.COSMIC_ARMOR_SHADER))
					.setDepthTestState(LEQUAL_DEPTH_TEST)
					.setLightmapState(LIGHTMAP)
					.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
					.setWriteMaskState(COLOR_WRITE)
					.setCullState(NO_CULL)
					.setLayeringState(VIEW_OFFSET_Z_LAYERING)
					.setTextureState(BLOCK_SHEET)
					.createCompositeState(true));

	public static final RenderType HELL = RenderType.create(
			ResourceLocation.fromNamespaceAndPath(ProductiveBeesGenesis.MOD_ID, "hell").toString(),
			DefaultVertexFormat.NEW_ENTITY,
			VertexFormat.Mode.QUADS,
			BUFFER_SIZE_2MB,
			true,
			false,
			RenderType.CompositeState.builder()
					.setShaderState(new RenderStateShard.ShaderStateShard(() -> CosmicShaders.HELL_SHADER))
					.setDepthTestState(LEQUAL_DEPTH_TEST)
					.setLightmapState(LIGHTMAP)
					.setWriteMaskState(COLOR_WRITE)
					.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
					.setTextureState(RenderUtils.COSMIC_TEXTURE_ISOLATED)
					.setLayeringState(RenderUtils.POLYGON_OFFSET_LAYERING)
					.createCompositeState(true));
}
