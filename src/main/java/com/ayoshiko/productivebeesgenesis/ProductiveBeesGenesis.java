package com.ayoshiko.productivebeesgenesis;

import com.ayoshiko.productivebeesgenesis.MyriadBeeTypeCache;
import com.ayoshiko.productivebeesgenesis.apiary.BeeProduceProcessor;
import com.ayoshiko.productivebeesgenesis.command.DevModeCommand;
import com.ayoshiko.productivebeesgenesis.client.screen.CustomConfigScreenFactory;
import com.ayoshiko.productivebeesgenesis.config.BalanceConfig;
import com.ayoshiko.productivebeesgenesis.config.ClientConfigMigrationService;
import com.ayoshiko.productivebeesgenesis.config.FactoryTierConfigService;
import com.ayoshiko.productivebeesgenesis.config.ModConfig;
import com.ayoshiko.productivebeesgenesis.config.ServerConfigMigrationService;
import com.ayoshiko.productivebeesgenesis.datagen.ConditionalBlockLootProvider;
import com.ayoshiko.productivebeesgenesis.datagen.ModBlockTagsProvider;
import com.ayoshiko.productivebeesgenesis.datagen.ModLootTables;
import com.ayoshiko.productivebeesgenesis.datagen.ModRecipes;
import com.ayoshiko.productivebeesgenesis.init.ModBlockEntities;
import com.ayoshiko.productivebeesgenesis.init.ModBlocks;
import com.ayoshiko.productivebeesgenesis.init.ModCreativeTabs;
import com.ayoshiko.productivebeesgenesis.init.ModItems;
import com.ayoshiko.productivebeesgenesis.init.ModMenuTypes;
import com.ayoshiko.productivebeesgenesis.init.ModStats;
import com.ayoshiko.productivebeesgenesis.mek.DevModeManager;
import com.ayoshiko.productivebeesgenesis.mek.MekCentrifugeFactoryHelper;
import com.ayoshiko.productivebeesgenesis.mek.MyriadBatchPlanner;
import com.ayoshiko.productivebeesgenesis.mek.PbRecipeCompleter;
import com.ayoshiko.productivebeesgenesis.mek.ServerTickTimeMonitor;
import com.ayoshiko.productivebeesgenesis.mek.ae2.Ae2CapabilityRegistrar;
import com.ayoshiko.productivebeesgenesis.mek.ae2.Ae2IntegrationLoader;
import com.ayoshiko.productivebeesgenesis.mek.ae2.CombFuzzyMatcher;
import com.ayoshiko.productivebeesgenesis.network.DevModeStateSyncPacket;
import com.ayoshiko.productivebeesgenesis.network.ModPayloads;
import com.ayoshiko.productivebeesgenesis.network.PayloadRateLimiter;
import com.ayoshiko.productivebeesgenesis.util.BeeConfigApplier;
import com.ayoshiko.productivebeesgenesis.util.EssenceConversionUpgradeHelper;
import com.ayoshiko.productivebeesgenesis.util.RawOreSmeltingUpgradeHelper;
import com.ayoshiko.productivebeesgenesis.util.BeeInfoHelper;
import com.ayoshiko.productivebeesgenesis.util.BeeConversionQueries;
import com.ayoshiko.productivebeesgenesis.util.BeeRecipeReloader;
import com.ayoshiko.productivebeesgenesis.util.CentrifugeRecipeIndex;
import com.ayoshiko.productivebeesgenesis.util.LogThrottle;
import com.ayoshiko.productivebeesgenesis.util.RecipeReloadRetryManager;
import com.ayoshiko.productivebeesgenesis.util.ServerTickClock;
import com.ayoshiko.productivebeesgenesis.util.SingleIngredientCraftingIndex;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
// 注：原 import net.minecraftforge.capabilities.RegisterCapabilitiesEvent 已移除 — Forge 1.20.1 无此类
// 注：Forge ModConfig.Type 使用全限定名引用，避免与项目 ModConfig 类同名歧义
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
	 * 资源蜜蜂：创世模组主类
	 * <br/>
	 * 为资源蜜蜂模组添加万象创世蜜蜂，可产出所有其他蜜蜂的蜜脾
	 * 通过Mixin注入原版离心机实现随机蜜脾产出
	 */
@Mod(ProductiveBeesGenesis.MOD_ID)
public final class ProductiveBeesGenesis {
	public static final String MOD_ID = "productivebeesgenesis";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final String PRODUCTIVE_BEES_MOD_ID = "productivebees";

	/** 配方版本号 — 每次 /reload 或数据包重载时递增,通知所有 PB 配方处理器清空缓存。AtomicLong 保证原子递增。 */
	public static final AtomicLong RECIPE_VERSION = new AtomicLong(0L);

	/** 存档 serverconfig 目录 — 存档级配置迁移重载时使用。 */
	private static final net.minecraft.world.level.storage.LevelResource SERVER_CONFIG_DIRECTORY =
			new net.minecraft.world.level.storage.LevelResource("serverconfig");

	/** 三个服务端规格是否已完成本会话的首次初始化（跨字段校验/平衡刷新/倍率快照）。 */
	private boolean serverConfigsInitialized;

	@SuppressWarnings("removal")
	public ProductiveBeesGenesis() {
		LOGGER.info("资源蜜蜂：创世模组初始化中...");

		// Forge 1.20.1：主类构造器无参，通过 FMLJavaModLoadingContext 获取 mod 事件总线
		// （NeoForge 1.21.1 通过构造器参数 IEventBus eventBus 注入，Forge 1.20.1 改用上下文获取）
		IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();

		// 初始化 Mek 离心机扩展（EM/ME/EME 三层工厂）— 必须在 DeferredRegister.register() 之前
		initMekCentrifugeExtensions();

		// 初始化 Mek 蜂箱扩展（ME/EME 两层工厂蜂箱）— 必须在 DeferredRegister.register() 之前
		initMekApiaryExtensions();

		// 注册 DeferredRegister 到 mod 事件总线
		registerDeferredRegisters(eventBus);

		// 注册配置文件（内部改用 ModLoadingContext）
		registerConfigs();

		// 客户端专属：“模组列表 → 配置屏幕”入口扩展点（专用服务器跳过，避免触碰客户端 GUI 类）
		if (FMLEnvironment.dist.isClient()) {
			CustomConfigScreenFactory.register();
		}

		// 注册配置加载/重载监听器（跨字段校验 + 蜜蜂属性覆盖 + 缓存失效）
		registerConfigListeners(eventBus);

		// 注册 mod 事件总线监听器（FML 生命周期）
		registerModEventBusListeners(eventBus);

		// 注册 Forge 事件总线监听器（运行时事件）
		registerForgeEventBusListeners();

		LOGGER.info("资源蜜蜂：创世模组初始化完成");
	}

	/**
	 * 初始化 Mek 离心机扩展（EM/ME/EME 三层工厂）— 必须在 registerDeferredRegisters 之前完成。
	 */
	private void initMekCentrifugeExtensions() {
		MekCompatInitializer.initMekCentrifugeExtensions();
	}

	/**
	 * 初始化 Mek 蜂箱扩展（ME/EME 两层工厂蜂箱）— 必须在 registerDeferredRegisters 之前完成。
	 */
	private void initMekApiaryExtensions() {
		MekCompatInitializer.initMekApiaryExtensions();
	}

	/**
	 * 注册 DeferredRegister 到 mod 事件总线
	 */
	private void registerDeferredRegisters(IEventBus eventBus) {
		ModBlocks.BLOCKS.register(eventBus);
		ModBlockEntities.register(eventBus);
		ModItems.ITEMS.register(eventBus);
		ModCreativeTabs.CREATIVE_MODE_TABS.register(eventBus);
		ModStats.register(eventBus);
		ModMenuTypes.register(eventBus);
	}

	/**
	 * 注册配置文件（CLIENT / COMMON / SERVER×3）
	 * <br/>
	 * Forge 1.20.1：通过 ModLoadingContext 注册配置（NeoForge 1.21.1 用 ModContainer.registerConfig）。
	 * 1.0.7 起服务端配置拆分为 gameplay/machines/capacities 三个文件，均以 SERVER 类型
	 * 注册（随存档 serverconfig/ 目录生效）；旧单文件由迁移服务事务式拆分。
	 */
	@SuppressWarnings("removal")
	private void registerConfigs() {
		ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT, ModConfig.CLIENT_SPEC);
		ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, ModConfig.COMMON_SPEC);
		ModLoadingContext.get().registerConfig(
				net.minecraftforge.fml.config.ModConfig.Type.SERVER,
				ModConfig.GAMEPLAY_SERVER_SPEC, ModConfig.GAMEPLAY_SERVER_FILE_NAME);
		ModLoadingContext.get().registerConfig(
				net.minecraftforge.fml.config.ModConfig.Type.SERVER,
				ModConfig.MACHINES_SERVER_SPEC, ModConfig.MACHINES_SERVER_FILE_NAME);
		ModLoadingContext.get().registerConfig(
				net.minecraftforge.fml.config.ModConfig.Type.SERVER,
				ModConfig.CAPACITIES_SERVER_SPEC, ModConfig.CAPACITIES_SERVER_FILE_NAME);
	}

	/**
	 * 注册配置加载/重载监听器
	 * <br/>
	 * 服务端配置加载时：
	 * <ol>
	 *   <li>记录到迁移服务，三个规格齐备后事务式迁移旧单文件</li>
	 *   <li>跨字段联合校验 + 平衡预设刷新 + 工厂倍率快照构建（仅一次）</li>
	 *   <li>应用蜜蜂属性覆盖（按存档生效）</li>
	 * </ol>
	 * 服务端配置重载时：跨字段校验、蜜蜂属性覆盖、过滤缓存与熔炉配方缓存失效；
	 * 工厂倍率快照只在 Loading 构建，Reloading 不替换（修改后需重启生效）。
	 */
	private void registerConfigListeners(IEventBus eventBus) {
		eventBus.addListener((ModConfigEvent.Loading event) -> {
			if (isOwnClientConfig(event.getConfig())) {
				ClientConfigMigrationService.onConfigLoading(event.getConfig());
			}
			if (isOwnServerConfig(event.getConfig())) {
				ServerConfigMigrationService.onConfigLoading(event.getConfig());
				initializeServerConfigs();
			}
		});
		eventBus.addListener((ModConfigEvent.Reloading event) -> {
			if (isOwnServerConfig(event.getConfig()) && ModConfig.areServerSpecsLoaded()) {
				boolean changed = ModConfig.validateAndFixCrossFields();
				changed |= BalanceConfig.refresh(true);
				if (changed) {
					ModConfig.saveServerSpecs();
				}
				BeeConfigApplier.applyOverrides();
				MekCentrifugeFactoryHelper.refreshSmeltingCompatConfig();
				// The master recipe-mode switch can invalidate already cached Mekanism recipes.
				mekanism.common.CommonWorldTickHandler.flushTagAndRecipeCaches = true;
				MyriadCreationsEventHandler.invalidateFilterCache();
				// 同步万象创世启用状态缓存（避免每 tick 32 次 volatile read 配置查询）
				MyriadCreationsEventHandler.invalidateEnabledCache();
				// 工厂倍率快照只在 Loading 构建，Reloading 不替换；修改后仍需重启游戏生效。
			}
			// 通知 RecipeReloadRetryManager 检测 EM/ME 配置死循环（Task 8）
			// 注：放在本模组 SERVER 判断之外 — EM/ME 配置重载事件不匹配我们的 spec，
			// 但仍需通知死循环检测器进行死循环判定；客户端同步的服务端配置没有本地路径，
			// getFullPath 对内存配置会抛异常，按空路径降级处理
			String configPath = "";
			try {
				configPath = event.getConfig().getFullPath().toString();
			} catch (IllegalStateException | ClassCastException ignored) {
				// 内存同步配置（客户端收到的 SERVER 配置回执）无文件路径
			}
			RecipeReloadRetryManager.onConfigFileChanged(configPath, event.getConfig().getModId());
		});
	}

	/** 仅处理本模组的 CLIENT 配置事件。 */
	private static boolean isOwnClientConfig(net.minecraftforge.fml.config.ModConfig config) {
		return config != null
				&& MOD_ID.equals(config.getModId())
				&& config.getType() == net.minecraftforge.fml.config.ModConfig.Type.CLIENT
				&& config.getSpec() == ModConfig.CLIENT_SPEC;
	}

	/** 三个服务端规格全部就绪后只初始化一次运行时配置快照。 */
	private synchronized void initializeServerConfigs() {
		if (serverConfigsInitialized || !ModConfig.areServerSpecsLoaded()) return;
		serverConfigsInitialized = true;
		boolean changed = ModConfig.validateAndFixCrossFields();
		changed |= BalanceConfig.refresh(false);
		FactoryTierConfigService.load(ModConfig.SERVER);
		if (changed) ModConfig.saveServerSpecs();
		BeeConfigApplier.applyOverrides();
		MekCentrifugeFactoryHelper.refreshSmeltingCompatConfig();
	}

	/**
	 * 仅处理本模组注册的 SERVER 配置，避免其他模组的配置事件触发本模组逻辑。
	 * 配置同步到客户端后 spec 身份仍保持不变，mod id 和类型则提供额外边界校验。
	 */
	private static boolean isOwnServerConfig(net.minecraftforge.fml.config.ModConfig config) {
		return config != null
				&& MOD_ID.equals(config.getModId())
				&& config.getType() == net.minecraftforge.fml.config.ModConfig.Type.SERVER
				&& ModConfig.isServerSpec(config.getSpec());
	}

	/**
	 * 注册 mod 事件总线监听器（FML 生命周期事件）
	 */
	private void registerModEventBusListeners(IEventBus eventBus) {
		eventBus.addListener(this::onCommonSetup);
		// TODO: Capability migration — NeoForge 1.21.1 的 RegisterCapabilitiesEvent 在 Forge 1.20.1 中不存在
		// Forge 1.20.1 使用 AttachCapabilitiesEvent<?> 注册 capability，API 完全不同
		// 需要 mekanism.common.attachments.IAttachmentAware / ICapabilityAware 接口对应的 1.20.1 版本
		// 以及 Ae2CapabilityRegistrar 的 1.20.1 重写，暂时禁用，等 capability 系统迁移时恢复
		// eventBus.addListener(this::onRegisterCapabilities);
		// 注册数据生成器
		eventBus.addListener(this::gatherData);
	}

	/**
	 * 注册 NeoForge 事件总线监听器（游戏运行时事件）
	 */
	private void registerForgeEventBusListeners() {
		// 监听数据重载事件（/reload、数据包变更、服务器启动）— 递增 RECIPE_VERSION，
		// 通知所有 PB 配方处理器清空 SMELTING/PB 配方缓存。
		// TagsUpdatedEvent 在所有 reload listener（含配方重载）完成后触发，是重置缓存的可靠信号。
		MinecraftForge.EVENT_BUS.addListener(this::onTagsReload);
		// 注册蜜蜂配方重载器 — 在 RecipeManager 加载完成后动态修改 PB 的 bee_fishing/bee_breeding/bee_spawning/bee_conversion 配方
		MinecraftForge.EVENT_BUS.addListener(this::onAddReloadListener);
		// 注册配方重载器的延迟重试 tick 处理器 — 处理首次进入世界时配置未加载的情况
		MinecraftForge.EVENT_BUS.addListener(BeeRecipeReloader::onServerTick);
		// 注册服务端 tick 时间监测器 — 通过 Pre/Post 监听器记录每 tick 实际耗时（MSPT），
		// 维护最近 100 tick 滚动平均，暴露 getTpsFactor() 供所有节流逻辑使用
		MinecraftForge.EVENT_BUS.addListener(ServerTickTimeMonitor.getInstance()::onTickPre);
		MinecraftForge.EVENT_BUS.addListener(ServerTickTimeMonitor.getInstance()::onTickPost);
		// 推进全局游戏刻时钟 — 槽位对象拿不到 Level，「外部退回窗口」靠它判断凭据是否过期
		MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
			if (event.phase == TickEvent.Phase.END) ServerTickClock.tick();
		});
		// 注册开发者模式命令 — /productivebeesgenesis dev on|off|status|<feature> on|off
		// 使用内存状态而非配置文件，避免生产环境意外持久化
		MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
		// 玩家登录时同步开发者模式状态到客户端（控制创造标签页开发物品可见性）
		MinecraftForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
		// 玩家下线时清理 PayloadRateLimiter 与 ModPayloads 频次限制缓存（防止离线玩家 UUID 残留）（Task 9）
		MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
			PayloadRateLimiter.onPlayerLogout(event.getEntity().getUUID());
			ModPayloads.clearFilterSyncRateLimit(event.getEntity().getUUID());
		});
		// 合成升级数据转移已迁移至 ApiaryShapedRecipe.assemble（recipe 包），
		// 通过重写 MekanismShapedRecipe.assemble 在输入消耗前转移 BLOCK_ENTITY_DATA，
		// 避免 ItemCraftedEvent 在输入被消耗后读到空物品的时序问题。
		// 服务器即将启动时处理存档级配置迁移的重载请求
		MinecraftForge.EVENT_BUS.addListener(this::onServerAboutToStart);
		// 服务器停止时清理静态缓存，防止跨存档数据泄漏
		MinecraftForge.EVENT_BUS.addListener(this::onServerStopped);
	}

	/**
	 * 迁移落盘后重新按 Forge 的存档覆盖规则加载三个 SERVER 配置。
	 * <p>
	 * 仅当存档 {@code serverconfig/} 里原本没有拆分文件、Forge 已把配置绑定到全局
	 * config 目录时才需要重载；整合包只改全局 config 的情况由迁移服务直接替换内存对象。
	 * {@code unloadConfigs} 是全局操作，会让所有模组多收一轮配置事件，
	 * 因此这里限定为“确实发生了存档级迁移”这一次，并捕获异常避免拖垮开服流程。
	 */
	private void onServerAboutToStart(net.minecraftforge.event.server.ServerAboutToStartEvent event) {
		if (!ServerConfigMigrationService.consumeReloadRequired()) return;
		java.nio.file.Path serverConfigDirectory = event.getServer().getWorldPath(SERVER_CONFIG_DIRECTORY);
		try {
			serverConfigsInitialized = false;
			net.minecraftforge.fml.config.ConfigTracker.INSTANCE.unloadConfigs(
					net.minecraftforge.fml.config.ModConfig.Type.SERVER, serverConfigDirectory);
			net.minecraftforge.fml.config.ConfigTracker.INSTANCE.loadConfigs(
					net.minecraftforge.fml.config.ModConfig.Type.SERVER, serverConfigDirectory);
			LOGGER.info("存档级配置迁移完成，已按存档覆盖规则重新加载服务端配置：{}",
					serverConfigDirectory);
		} catch (RuntimeException exception) {
			LOGGER.error("重新加载存档级服务端配置失败，本次会话使用迁移前的配置对象：{}",
					serverConfigDirectory, exception);
		} finally {
			initializeServerConfigs();
		}
	}

	/**
	 * 标签/配方重载完成回调 — 递增配方版本号、失效缓存、重建离心配方索引。
	 * 失效 BeeInfoHelper/BeeProduceProcessor/PbRecipeCompleter/MyriadBatchPlanner/CombFuzzyMatcher 缓存。
	 * Task 16.3 同步失效 BeeProduceProcessor 产出配方缓存(静态共享)。
	 */
	private void onTagsReload(TagsUpdatedEvent event) {
		long newVersion = RECIPE_VERSION.incrementAndGet();
		BeeInfoHelper.invalidateCache();
		// 失效机械蜂箱产出配方缓存（Task 16.3 — 静态缓存全局失效）
		BeeProduceProcessor.invalidateCache();
		// PBG 1.0.7：失效精华转化和原矿熔炼配方索引缓存
		EssenceConversionUpgradeHelper.invalidateCache();
		RawOreSmeltingUpgradeHelper.invalidateCache();
		// 失效物品/方块转化配方索引（配方重载后转化原料花朵判定需重建）
		BeeConversionQueries.invalidate();
		// 失效单原料合成配方索引（染料蜜蜂花→染料查找，重载后首次查询重建）
		SingleIngredientCraftingIndex.invalidate();
		// 失效 PB 离心配方输出表缓存（防止 getRecipeOutputs 返回过期 LinkedHashMap）
		PbRecipeCompleter.invalidateRecipeOutputsCache();
		// 失效万象批量规划器模板缓存（标签重载后 bee_type 可能变化）（Task 19）
		MyriadBatchPlanner.clearTemplateCache();
		// 失效 AE2 蜜脾模糊匹配缓存（AE2 未加载时跳过，避免 NoClassDefFoundError）（Task 20）
		if (Ae2IntegrationLoader.isAe2Loaded()) {
			CombFuzzyMatcher.clearCache();
		}
		// 失效万象创世 bee_type 缓存（标签重载可能变更 BeeReloadListener 数据，5 秒过期窗口消除）
		MyriadBeeTypeCache.invalidate();
		// 重建离心配方索引 — 服务端用 MinecraftServer，客户端用 ClientLevel
		// Bug 1 修复：专用服务器客户端无本地服务器，此前跳过重建导致索引永远为 EMPTY，
		// 客户端 containsRecipe 校验失败无法放入蜜脾。现在客户端也重建索引。
		var server = ServerLifecycleHooks.getCurrentServer();
		if (server != null) {
			CentrifugeRecipeIndex.rebuild(server.getRecipeManager());
		} else if (FMLEnvironment.dist.isClient()) {
			// 客户端场景：通过反射安全调用客户端类方法，避免服务端加载 ProductiveBeesGenesisClient
			// （该类引用 net.minecraft.client.Minecraft，服务端加载会导致 ClassNotFoundException）
			try {
				Class<?> clientClass = Class.forName(
						"com.ayoshiko.productivebeesgenesis.ProductiveBeesGenesisClient");
				java.lang.reflect.Method method = clientClass.getMethod("rebuildCentrifugeIndex");
				method.invoke(null);
			} catch (Exception e) {
				LOGGER.warn("客户端离心配方索引重建失败，降级到全量遍历", e);
			}
		}
		LOGGER.info("配方/标签重载完成，recipeVersion 递增至 {}", newVersion);
	}

	/**
	 * 注册命令 — 开发者模式命令树
	 * <br/>
	 * 提供统一的开发调试入口，命令树详见 {@link DevModeCommand#register}。
	 */
	private void onRegisterCommands(RegisterCommandsEvent event) {
		DevModeCommand.register(event);
	}

	/**
	 * 玩家登录回调 — 同步开发者模式状态到新加入的客户端
	 * <br/>
	 * 由于 DevModeManager 状态仅存在于服务端内存，新加入的客户端默认 masterEnabled=false。
	 * 通过登录事件推送当前状态，确保创造标签页开发物品可见性与服务端一致。
	 */
	private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
			return;
		}
		DevModeStateSyncPacket packet = new DevModeStateSyncPacket(
				DevModeManager.isEnabled(),
				DevModeManager.getFeatureStates()
		);
		// Forge 1.20.1：通过 SimpleChannel 发送（PacketDistributor.send 仅接受原生 Packet<?>，
		// NeoForge 1.21.1 才有 sendToPlayer 静态方法）
		ModPayloads.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> serverPlayer), packet);
	}

	/**
	 * 注册蜜蜂配方重载器
	 * <br/>
	 * AddReloadListenerEvent 在 RecipeManager 完成数据包加载后触发，
	 * 自定义监听器在所有内置监听器之后执行，此时配方已就绪可被替换。
	 */
	private void onAddReloadListener(AddReloadListenerEvent event) {
		event.addListener(new BeeRecipeReloader(
				event.getServerResources().getRecipeManager()
		));
	}

	/**
	 * 服务器停止回调 — 清理静态缓存防止跨存档数据泄漏:
	 * CentrifugeRecipeIndex/BeeInfoHelper/BeeProduceProcessor/MyriadCreationsEventHandler/
	 * RecipeReloadRetryManager/AbstractCombEventHandler.ThreadLocals/PayloadRateLimiter/MyriadBatchPlanner/
	 * CombFuzzyMatcher/ServerTickTimeMonitor。每个清理独立 try-catch,单个失败不中断后续。
	 */
	private void onServerStopped(ServerStoppedEvent event) {
		// 异常隔离：每个清理操作独立 try-catch，单个失败不中断后续清理，防止跨存档泄漏
		safeClear(CentrifugeRecipeIndex::clear, "CentrifugeRecipeIndex");
		safeClear(BeeInfoHelper::invalidateCache, "BeeInfoHelper");
		// 清理机械蜂箱产出配方缓存（Task 16.3 — 静态缓存防止跨存档泄漏）
		safeClear(BeeProduceProcessor::invalidateCache, "BeeProduceProcessor");
		// PBG 1.0.7：清理精华转化和原矿熔炼配方索引
		safeClear(EssenceConversionUpgradeHelper::invalidateCache, "EssenceConversionUpgradeHelper");
		safeClear(RawOreSmeltingUpgradeHelper::invalidateCache, "RawOreSmeltingUpgradeHelper");
		// 清理物品/方块转化配方索引 — 防止跨存档残留旧 RecipeHolder 引用（与 onTagsReload 生命周期一致）
		safeClear(BeeConversionQueries::invalidate, "BeeConversionQueries");
		// 清理单原料合成配方索引 — 防止跨存档残留旧配方产物引用
		safeClear(SingleIngredientCraftingIndex::invalidate, "SingleIngredientCraftingIndex");
		safeClear(MyriadCreationsEventHandler::clearAllCaches, "MyriadCreationsEventHandler");
		// 清理 BeeRecipeReloader 延迟重试上下文 — 防止持有的 RecipeManager 引用阻碍 GC
	safeClear(RecipeReloadRetryManager::clearPendingRetryContext, "RecipeReloadRetryManager");
		// 清理 AbstractCombEventHandler 的 ThreadLocal — 防止线程池复用场景下引用残留
		safeClear(AbstractCombEventHandler::clearThreadLocals, "AbstractCombEventHandler.ThreadLocals");
		// 清理网络包频次限制器映射 — 防止跨存档玩家数据残留
		safeClear(PayloadRateLimiter::clearAll, "PayloadRateLimiter");
		// 清理万象批量规划器模板缓存 — 防止跨存档 bee_type 模板残留（Task 19）
		safeClear(MyriadBatchPlanner::clearTemplateCache, "MyriadBatchPlanner.TEMPLATE_CACHE");
		// 清理万象批量规划器 ThreadLocal 快照缓存 — 防止线程池复用场景下的引用残留
		safeClear(MyriadBatchPlanner::clearThreadLocals, "MyriadBatchPlanner.snapshotCache");
		// 清理 AE2 蜜脾模糊匹配缓存（AE2 未加载时跳过，避免 NoClassDefFoundError）（Task 20）
		safeClear(() -> {
			if (Ae2IntegrationLoader.isAe2Loaded()) {
				CombFuzzyMatcher.clearCache();
			}
		}, "CombFuzzyMatcher.aeItemKeyToBeeTypeCache");
		// 清理服务端 tick 时间监测器状态 — 防止跨存档 MSPT 样本与 tpsFactor 缓存残留
		safeClear(ServerTickTimeMonitor.getInstance()::invalidate, "ServerTickTimeMonitor");
		// 复位全局游戏刻时钟 — 槽位的「外部退回窗口」依赖它判定过期，跨存档必须归零
		safeClear(ServerTickClock::reset, "ServerTickClock");
		// 复位服务端配置三规格初始化标记 — 下个存档需重新构建运行时快照
		serverConfigsInitialized = false;
		// 清理配置迁移服务的世界级状态 — 防止跨存档持有配置对象
		safeClear(ServerConfigMigrationService::reset, "ServerConfigMigrationService");
		// 复位工厂等级倍率快照 — 防止跨存档保留旧容量矩阵
		safeClear(FactoryTierConfigService::resetToDefaults, "FactoryTierConfigService");
		safeClear(LogThrottle::clearAll, "LogThrottle");
	}

	/**
	 * 安全清理包装 — 单个清理操作失败不影响其他清理
	 *
	 * @param action 清理操作
	 * @param name   清理目标名称（用于日志）
	 */
	private void safeClear(Runnable action, String name) {
		try {
			action.run();
		} catch (Exception e) {
			LOGGER.error("清理 {} 时发生异常", name, e);
		}
	}

	private void onCommonSetup(FMLCommonSetupEvent event) {
		// 注册网络消息（SimpleChannel）— 必须在 enqueueWork 之前，确保通道在并行任务前就绪
		ModPayloads.registerMessages();
		event.enqueueWork(() -> {
			checkProductiveBeesCompatibility();
			ModStats.init();
		});
	}

	/** 注册数据生成器 */
	private void gatherData(GatherDataEvent event) {
		var generator = event.getGenerator();
		var packOutput = generator.getPackOutput();

		// 配方（构造器已去掉 lookupProvider 参数）
		generator.addProvider(event.includeServer(), new ModRecipes(packOutput));
		// 战利品表（create 已去掉 lookupProvider 参数）
		generator.addProvider(event.includeServer(), ModLootTables.create(packOutput));
		// F9: 条件战利品表 — 为 EM/ME/EME 方块生成带 forge:conditions 的 dropSelf 战利品表
		generator.addProvider(event.includeServer(), new ConditionalBlockLootProvider(packOutput));
		// 方块标签（Forge 1.20.1 构造器为 output + lookupProvider + existingFileHelper）
		generator.addProvider(event.includeServer(), new ModBlockTagsProvider(packOutput,
			event.getLookupProvider(), event.getExistingFileHelper()));
		// 语言文件：主 lang（src/main/resources）已包含全部键（GUI + configuration + config.*），
		// 不再通过 ModLanguageProvider 生成，避免 generated lang 与主 lang 键重叠触发 DuplicatesStrategy.EXCLUDE。
	}

	/**
	 * 注册 MEK 离心机物品与方块的 Capability
	 * <br/>
	 * 原理：ItemBlockTooltip 实现了 ICapabilityAware 接口，需要通过 RegisterCapabilitiesEvent
	 * 注册安全 Capability（拥有者/安全等级 tooltip）和能量 Capability（储能 tooltip）。
	 * Mekanism 原版在 Mekanism 主类中遍历自己的物品注册表调用 addCapabilities，
	 * 我们需要对自己的物品做同样的事。
	 * <p>
	 * v1.5.3 新增：AE2 已安装时委托 {@link Ae2CapabilityRegistrar#register} 为全部 18 个离心机
	 * BlockEntityType 注册 {@code AECapabilities.IN_WORLD_GRID_NODE_HOST} capability，
	 * 使 AE2 线缆（含 ExtendedAE/AdvancedAE/ae2cs/ae2lt/Glodium/AppliedFlux 等附属模组线缆）
	 * 能自动发现并连接离心机。AE2 未安装时安全跳过，不触发类加载失败。
	 */
	// TODO: Capability migration — 整个方法无法直接迁移到 Forge 1.20.1
	// NeoForge 1.21.1 的 RegisterCapabilitiesEvent 是新 capability 系统的注册入口，
	// Forge 1.20.1 完全没有此 API，需要为每个 Item/BlockEntity 重新实现：
	//   1. mekanism.common.attachments.IAttachmentAware / ICapabilityAware 在 1.20.1 Forge 版本中签名不同
	//   2. Ae2CapabilityRegistrar.register 需要改用 1.20.1 AE2 的 capability 注册方式
	// 监听器调用已在 registerModEventBusListeners 中注释，方法体保留作参考，待迁移时恢复
	private void onRegisterCapabilities(Object event) {
		// 暂时空实现 — event 参数类型用 Object 避免引用已删除的 RegisterCapabilitiesEvent
		/*
		for (var entry : ModItems.ITEMS.getEntries()) {
			Item item = entry.get();
			if (item instanceof ICapabilityAware aware) {
				aware.attachCapabilities(event);
			}
		}
		if (Ae2IntegrationLoader.isAe2Loaded()) {
			Ae2CapabilityRegistrar.register(event);
		}
		*/
	}

	private static void checkProductiveBeesCompatibility() {
		try {
			if (!net.minecraftforge.fml.ModList.get().isLoaded(PRODUCTIVE_BEES_MOD_ID)) {
				LOGGER.error("未检测到资源蜜蜂模组 (Productive Bees)，模组无法正常工作！");
				return;
			}
			LOGGER.info("资源蜜蜂模组兼容性检查通过");
		} catch (Exception e) {
			LOGGER.warn("检查资源蜜蜂模组兼容性时发生错误", e);
		}
	}
}
