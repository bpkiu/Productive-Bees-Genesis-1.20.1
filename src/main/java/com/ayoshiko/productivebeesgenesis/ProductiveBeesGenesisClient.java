package com.ayoshiko.productivebeesgenesis;

import com.ayoshiko.productivebeesgenesis.apiary.MekApiaryContainer;
import com.ayoshiko.productivebeesgenesis.apiary.client.GuiMekApiary;
import com.ayoshiko.productivebeesgenesis.apiary.client.GuiMekApiaryFactory;
import com.ayoshiko.productivebeesgenesis.client.render.cosmic.AbstractBakedModelCosmic;
import com.ayoshiko.productivebeesgenesis.client.render.cosmic.BakedModelHalo;
import com.ayoshiko.productivebeesgenesis.client.render.cosmic.CosmicRenderQueue;
import com.ayoshiko.productivebeesgenesis.client.render.cosmic.CosmicShaders;
import com.ayoshiko.productivebeesgenesis.client.render.cosmic.GeometryLoaderCosmic;
import com.ayoshiko.productivebeesgenesis.client.render.cosmic.GeometryLoaderHalo;
import com.ayoshiko.productivebeesgenesis.client.render.cosmic.GeometryLoaderHell;
import com.ayoshiko.productivebeesgenesis.client.screen.CustomConfigScreenFactory;
import com.ayoshiko.productivebeesgenesis.client.screen.GuiExtraMekCentrifugeFactory;
import com.ayoshiko.productivebeesgenesis.client.screen.GuiMekCentrifuge;
import com.ayoshiko.productivebeesgenesis.client.screen.GuiMekCentrifugeFactory;
import com.ayoshiko.productivebeesgenesis.compat.emextras.EMEMenuTypeRegistration;
import com.ayoshiko.productivebeesgenesis.compat.emextras.client.gui.GuiEMExtraMekCentrifugeFactory;
import com.ayoshiko.productivebeesgenesis.compat.mekanism_extras.MEMenuTypeRegistration;
import com.ayoshiko.productivebeesgenesis.init.ModMenuTypes;
import com.ayoshiko.productivebeesgenesis.mek.MekCompatHooks;
import com.ayoshiko.productivebeesgenesis.util.CentrifugeRecipeIndex;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.tile.factory.TileEntityFactory;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.TextureStitchEvent;

/**
 * 资源蜜蜂：创世模组客户端专用初始化
 * <br/>
 * 注册MEK离心机Screen映射与Cosmic渲染系统。
 * <p>
 * Forge 1.20.1：Screen 注册改为在 {@link FMLClientSetupEvent#enqueueWork}
 * 中调用 {@link MenuScreens#register} 静态方法（对应 NeoForge 1.21+ 的 RegisterMenuScreensEvent）。
 * Cosmic 渲染注册仍使用 MOD 事件总线订阅者 + {@link SubscribeEvent}。
 */
public final class ProductiveBeesGenesisClient {

	/**
	 * Screen 注册订阅者（MOD 事件总线，仅客户端）
	 * <br/>
	 * Forge 1.20.1 无 RegisterMenuScreensEvent，在 FMLClientSetupEvent 期间执行。
	 */
	@EventBusSubscriber(modid = ProductiveBeesGenesis.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
	public static final class ScreenRegistry {
		private ScreenRegistry() {}

		@SubscribeEvent
		@SuppressWarnings({"rawtypes", "unchecked"})
		public static void onClientSetup(FMLClientSetupEvent event) {
			event.enqueueWork(() -> {
				// 基础MEK离心机Screen
				MenuScreens.register(ModMenuTypes.MEK_CENTRIFUGE.get(), GuiMekCentrifuge::new);

				// MEK通用机械蜂箱Screen
				// 使用 lambda 而非方法引用：GuiMekApiary 已泛型化，方法引用无法推断类型参数
				MenuScreens.register(ModMenuTypes.MEK_APIARY.get(),
						(MekApiaryContainer menu, Inventory inv, Component title) -> new GuiMekApiary<>(menu, inv, title));

				// 工厂版MEK通用机械蜂箱Screen（4个等级共用，运行时根据 tile.getTier() 区分）
				MenuScreens.register(ModMenuTypes.MEK_APIARY_FACTORY.get(), GuiMekApiaryFactory::new);

				// 工厂版MEK离心机Screen  需要类型转换
				MenuScreens.register((MenuType) ModMenuTypes.MEK_CENTRIFUGE_FACTORY.get(),
						(MenuScreens.ScreenConstructor) (menu, inv, title) ->
								new GuiMekCentrifugeFactory(
										(MekanismTileContainer<TileEntityFactory<?>>) (MekanismTileContainer<?>) menu,
										inv, title));

				// ME扩展版离心机工厂Screen — 仅当 MekanismExtras 加载时注册
				if (MekCompatHooks.isMekanismExtrasLoaded()) {
					MenuScreens.register(MEMenuTypeRegistration.ME_CENTRIFUGE_FACTORY.get(), GuiExtraMekCentrifugeFactory::new);
				}

				// EME扩展版离心机工厂Screen — 仅当 EvolvedMekanismExtras 加载时注册
				if (MekCompatHooks.isEvolvedMekanismExtrasLoaded()) {
					MenuScreens.register(EMEMenuTypeRegistration.EME_CENTRIFUGE_FACTORY.get(), GuiEMExtraMekCentrifugeFactory::new);
				}
			});
		}
	}

	/**
	 * Cosmic 渲染系统注册（MOD 事件总线，仅客户端）
	 * <br/>
	 * 负责注册：
	 * <ol>
	 *   <li>cosmic 几何加载器（ModelEvent.RegisterGeometryLoaders）</li>
	 *   <li>cosmic 着色器（RegisterShadersEvent）</li>
	 *   <li>cosmic 纹理 UV 采集（TextureStitchEvent.Post）</li>
	 * </ol>
	 */
	@EventBusSubscriber(modid = ProductiveBeesGenesis.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
	public static final class CosmicRenderRegistry {
		private CosmicRenderRegistry() {}

		@SubscribeEvent
		public static void onRegisterGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
			// Forge 1.20.1：register(String, IGeometryLoader)，名称自动挂到当前 mod 命名空间
			event.register("cosmic", new GeometryLoaderCosmic());
			event.register("halo", new GeometryLoaderHalo());
			event.register("hell", new GeometryLoaderHell());
		}

		@SubscribeEvent
		public static void onRegisterShaders(RegisterShadersEvent event) {
			CosmicShaders.onRegisterShaders(event);
		}

		@SubscribeEvent
		public static void onTextureAtlasStitched(TextureStitchEvent.Post event) {
			CosmicShaders.onTextureAtlasStitched(event);
			// 失效 halo 四边形缓存：图集重建后 UV 可能变化，旧缓存会导致 halo 渲染错位或采样错误纹理
			BakedModelHalo.invalidateCache();
			// 失效 cosmic 烘焙四边形缓存：图集重建后 atlasSprites 的 UV 变化，旧 bakedQuads 会采样错误纹理
			AbstractBakedModelCosmic.invalidateCache();
		}
	}

	/**
	 * Cosmic 渲染事件处理（FORGE 事件总线，仅客户端）
	 * <br/>
	 * 负责处理运行时渲染事件：
	 * <ol>
	 *   <li>世界渲染后阶段（RenderLevelStageEvent）— 执行 cosmic 队列渲染</li>
	 *   <li>GUI 屏幕渲染前/后（ScreenEvent.Render.Pre/Post）— 切换 cosmic 渲染模式</li>
	 * </ol>
	 */
	@EventBusSubscriber(modid = ProductiveBeesGenesis.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.FORGE)
	public static final class CosmicRenderHandler {
		private CosmicRenderHandler() {}

		@SubscribeEvent
		public static void onRenderLevelAfterLevel(RenderLevelStageEvent event) {
			if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
				// 强制重置 GUI 渲染标志：防止 ScreenEvent.Render.Pre 设为 true 后 Post 未触发（异常情况）导致标志位卡在 true。
				// AFTER_LEVEL 阶段一定处于世界渲染，不应使用 GUI 模式的固定视角参数。
				CosmicShaders.cosmicInventoryRender = false;
				CosmicRenderQueue.renderAll();
			}
		}

		/**
		 * GUI 屏幕渲染前事件
		 * <br/>
		 * 设置 cosmicInventoryRender 标志为 true，使 cosmic 渲染使用固定视角参数（scale=100）。
		 * 这确保 GUI 中物品的星空效果呈现静态星空而非动态视角流动。
		 */
		@SubscribeEvent
		public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
			CosmicShaders.cosmicInventoryRender = true;
		}

		/**
		 * GUI 屏幕渲染后事件
		 * <br/>
		 * 重置 cosmicInventoryRender 标志为 false，恢复世界模式的动态视角参数。
		 */
		@SubscribeEvent
		public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
			CosmicShaders.cosmicInventoryRender = false;
		}
	}

	/**
	 * 客户端重建离心配方索引
	 * <br/>
	 * Bug 1 修复：专用服务器客户端无本地服务器，此前 {@code onTagsReload} 中
	 * {@code ServerLifecycleHooks.getCurrentServer()} 返回 null 导致索引不重建。
	 * 此方法在客户端从 {@link net.minecraft.client.Minecraft#getLevel()} 获取 RecipeManager 重建索引。
	 * <p>
	 * 通过 {@link net.minecraftforge.fml.loading.FMLEnvironment#dist()} 守卫调用，
	 * 服务端不会加载此方法（DistExecutor 隔离），避免 {@code net.minecraft.client.Minecraft} 类加载。
	 */
	public static void rebuildCentrifugeIndex() {
		try {
			net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
			net.minecraft.world.level.Level level = mc.level;
			if (level != null) {
				CentrifugeRecipeIndex.rebuild(level.getRecipeManager());
			}
		} catch (Throwable t) {
			ProductiveBeesGenesis.LOGGER.warn("客户端离心配方索引重建失败，降级到全量遍历", t);
		}
	}
}
