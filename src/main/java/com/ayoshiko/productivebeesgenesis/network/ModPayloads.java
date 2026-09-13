package com.ayoshiko.productivebeesgenesis.network;

import com.ayoshiko.productivebeesgenesis.MyriadCreationsEventHandler;
import com.ayoshiko.productivebeesgenesis.ProductiveBeesGenesis;
import com.ayoshiko.productivebeesgenesis.config.ModConfig;
import com.ayoshiko.productivebeesgenesis.mek.ae2.Ae2IntegrationLoader;
import com.ayoshiko.productivebeesgenesis.util.LogThrottle;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 模组网络通道注册与服务端处理入口
 * <p>
 * Forge 1.20.1 使用 {@link SimpleChannel} + {@link net.minecraft.network.FriendlyByteBuf} 编解码，
 * 替代 NeoForge 1.21.1 的 {@code CustomPacketPayload} + {@code StreamCodec} 体系。
 * <p>
 * 职责：
 * <ol>
 *   <li>创建并注册 {@link SimpleChannel}，在 {@code FMLCommonSetupEvent} 期间注册所有消息</li>
 *   <li>频率限制：每玩家 3 秒冷却，防止恶意高频发包</li>
 *   <li>万象创世过滤配置同步包的服务端处理：权限校验 → 数据校验 → 写入配置 → 持久化 → 失效缓存</li>
 * </ol>
 * <p>
 * 消息 ID 分配策略：使用固定 ID 而非递增，确保 AE2 可选依赖消息缺失时
 * 非 AE2 消息的 ID 保持一致（客户端与服务端必须匹配）。
 */
public final class ModPayloads {

	/** 修改服务端配置所需权限等级（与原版配置界面一致：OP 2 级） */
	private static final int REQUIRED_PERMISSION_LEVEL = 2;

	/** 频率限制：玩家 UUID → 上次接受配置同步包的时间戳（毫秒） */
	private static final ConcurrentHashMap<UUID, AtomicLong> FILTER_SYNC_LAST_ACCEPT = new ConcurrentHashMap<>();

	/** 配置同步包冷却时间：3 秒（3000ms），防止恶意客户端高频发包导致磁盘 I/O 风暴 */
	private static final long FILTER_SYNC_COOLDOWN_MS = 3000L;

	/** 惰性清理触发阈值：每处理 64 个包清理一次过期条目 */
	private static final int LAZY_CLEANUP_THRESHOLD = 64;

	/** 条目过期时间：5 分钟（300000ms）未活动则视为可清理 */
	private static final long ENTRY_EXPIRATION_MS = 5 * 60 * 1000L;

	/** 包处理计数器，用于触发惰性清理 */
	private static final AtomicInteger packetCounter = new AtomicInteger(0);

	/** 协议版本（客户端与服务端必须匹配） */
	private static final String PROTOCOL_VERSION = "1";

	/**
	 * 模组网络通道 — 所有数据包通过此通道收发
	 * <p>
	 * 使用 {@link NetworkRegistry#newSimpleChannel} 创建，
	 * 协议版本校验接受所有版本（兼容开发环境与生产环境混用）。
	 */
	public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
			ResourceLocation.fromNamespaceAndPath(ProductiveBeesGenesis.MOD_ID, "main"),
			() -> PROTOCOL_VERSION,
			s -> true,
			s -> true
	);

	private ModPayloads() {
	}

	// ── 消息 ID 常量（固定 ID，确保 AE2 可选消息缺失时 ID 一致） ──

	// C→S 非 AE2 消息（ID 0-7）
	private static final int ID_FILTER_CONFIG_SYNC = 0;
	private static final int ID_APIARY_TOGGLE_SORTING = 1;
	private static final int ID_PB_UPGRADE_EXTRACT = 2;
	private static final int ID_APIARY_SELECT_BEE = 3;
	private static final int ID_APIARY_CAGE_OPERATION = 4;
	private static final int ID_APIARY_FEED_BEE = 5;
	private static final int ID_TOGGLE_APIARY_DIRECT_EJECT = 6;
	private static final int ID_TOGGLE_SMELTING_COMPAT = 7;
	private static final int ID_TOGGLE_APIARY_FEEDER_CONVERSION = 9;

	// S→C 非 AE2 消息（ID 8）
	private static final int ID_DEV_MODE_STATE_SYNC = 8;

	// C→S AE2 消息（ID 10-21, 23）
	private static final int ID_CYCLE_AE_OUTPUT = 10;
	private static final int ID_TOGGLE_AE_INPUT = 11;
	private static final int ID_TOGGLE_AE_INPUT_NBT_IGNORE = 12;
	private static final int ID_CYCLE_AE_INPUT_FILTER_MODE = 13;
	private static final int ID_TOGGLE_AE_INPUT_PRECISE_MODE = 14;
	private static final int ID_SET_AE_INPUT_FILTER_ENTRY = 15;
	private static final int ID_SET_AE_INPUT_FILTER_AMOUNT = 16;
	private static final int ID_SET_ALL_AE_INPUT_FILTER_AMOUNT = 17;
	private static final int ID_TOGGLE_ALL_AE_INPUT_FILTER_UNLIMITED = 18;
	private static final int ID_TOGGLE_ALL_AE_INPUT_FILTER_NETWORK_STOCK = 19;
	private static final int ID_AE_INPUT_OUTPUT_SLOT = 20;
	private static final int ID_OPEN_AE_INPUT_CONFIG = 21;
	private static final int ID_SET_AE_INPUT_TAG_FILTER = 23;

	// S→C AE2 消息（ID 22, 24）
	private static final int ID_SYNC_AE_INPUT_FILTER_ENTRIES = 22;
	private static final int ID_SYNC_AE_INPUT_TAG_FILTER = 24;

	/**
	 * 注册所有网络消息 — 由 {@code ProductiveBeesGenesis.onCommonSetup} 在
	 * {@code FMLCommonSetupEvent} 期间调用。
	 * <p>
	 * AE2 相关消息仅在 {@link Ae2IntegrationLoader#isAe2Loaded()} 时注册，
	 * 使用固定 ID 确保非 AE2 消息的 ID 不受 AE2 是否加载影响。
	 */
	public static void registerMessages() {
		// ── C→S 非 AE2 消息 ──
		CHANNEL.registerMessage(ID_FILTER_CONFIG_SYNC,
				FilterConfigSyncPayload.class,
				FilterConfigSyncPayload::encode,
				FilterConfigSyncPayload::new,
				FilterConfigSyncPayload::handle);

		CHANNEL.registerMessage(ID_APIARY_TOGGLE_SORTING,
				ApiaryToggleSortingPayload.class,
				ApiaryToggleSortingPayload::encode,
				ApiaryToggleSortingPayload::new,
				ApiaryToggleSortingPayload::handle);

		CHANNEL.registerMessage(ID_PB_UPGRADE_EXTRACT,
				PbUpgradeExtractPayload.class,
				PbUpgradeExtractPayload::encode,
				PbUpgradeExtractPayload::new,
				PbUpgradeExtractPayload::handle);

		CHANNEL.registerMessage(ID_APIARY_SELECT_BEE,
				ApiarySelectBeePayload.class,
				ApiarySelectBeePayload::encode,
				ApiarySelectBeePayload::new,
				ApiarySelectBeePayload::handle);

		CHANNEL.registerMessage(ID_APIARY_CAGE_OPERATION,
				ApiaryCageOperationPayload.class,
				ApiaryCageOperationPayload::encode,
				ApiaryCageOperationPayload::new,
				ApiaryCageOperationPayload::handle);

		CHANNEL.registerMessage(ID_APIARY_FEED_BEE,
				ApiaryFeedBeePayload.class,
				ApiaryFeedBeePayload::encode,
				ApiaryFeedBeePayload::new,
				ApiaryFeedBeePayload::handle);

		CHANNEL.registerMessage(ID_TOGGLE_APIARY_DIRECT_EJECT,
				ToggleApiaryDirectEjectPayload.class,
				ToggleApiaryDirectEjectPayload::encode,
				ToggleApiaryDirectEjectPayload::new,
				ToggleApiaryDirectEjectPayload::handle);

		CHANNEL.registerMessage(ID_TOGGLE_SMELTING_COMPAT,
				ToggleSmeltingCompatPayload.class,
				ToggleSmeltingCompatPayload::encode,
				ToggleSmeltingCompatPayload::new,
				ToggleSmeltingCompatPayload::handle);

		CHANNEL.registerMessage(ID_TOGGLE_APIARY_FEEDER_CONVERSION,
				ToggleApiaryFeederConversionPayload.class,
				ToggleApiaryFeederConversionPayload::encode,
				ToggleApiaryFeederConversionPayload::new,
				ToggleApiaryFeederConversionPayload::handle);

		// ── S→C 非 AE2 消息 ──
		CHANNEL.registerMessage(ID_DEV_MODE_STATE_SYNC,
				DevModeStateSyncPacket.class,
				DevModeStateSyncPacket::encode,
				DevModeStateSyncPacket::new,
				DevModeStateSyncPacket::handle);

		// ── AE2 消息（仅当 AE2 已加载时注册） ──
		if (Ae2IntegrationLoader.isAe2Loaded()) {
			CHANNEL.registerMessage(ID_CYCLE_AE_OUTPUT,
					CycleAeOutputPayload.class,
					CycleAeOutputPayload::encode,
					CycleAeOutputPayload::new,
					CycleAeOutputPayload::handle);

			CHANNEL.registerMessage(ID_TOGGLE_AE_INPUT,
					ToggleAeInputPayload.class,
					ToggleAeInputPayload::encode,
					ToggleAeInputPayload::new,
					ToggleAeInputPayload::handle);

			CHANNEL.registerMessage(ID_TOGGLE_AE_INPUT_NBT_IGNORE,
					ToggleAeInputNbtIgnorePayload.class,
					ToggleAeInputNbtIgnorePayload::encode,
					ToggleAeInputNbtIgnorePayload::new,
					ToggleAeInputNbtIgnorePayload::handle);

			CHANNEL.registerMessage(ID_CYCLE_AE_INPUT_FILTER_MODE,
					CycleAeInputFilterModePayload.class,
					CycleAeInputFilterModePayload::encode,
					CycleAeInputFilterModePayload::new,
					CycleAeInputFilterModePayload::handle);

			CHANNEL.registerMessage(ID_TOGGLE_AE_INPUT_PRECISE_MODE,
					ToggleAeInputPreciseModePayload.class,
					ToggleAeInputPreciseModePayload::encode,
					ToggleAeInputPreciseModePayload::new,
					ToggleAeInputPreciseModePayload::handle);

			CHANNEL.registerMessage(ID_SET_AE_INPUT_FILTER_ENTRY,
					SetAeInputFilterEntryPayload.class,
					SetAeInputFilterEntryPayload::encode,
					SetAeInputFilterEntryPayload::new,
					SetAeInputFilterEntryPayload::handle);

			CHANNEL.registerMessage(ID_SET_AE_INPUT_FILTER_AMOUNT,
					SetAeInputFilterAmountPayload.class,
					SetAeInputFilterAmountPayload::encode,
					SetAeInputFilterAmountPayload::new,
					SetAeInputFilterAmountPayload::handle);

			CHANNEL.registerMessage(ID_SET_ALL_AE_INPUT_FILTER_AMOUNT,
					SetAllAeInputFilterAmountPayload.class,
					SetAllAeInputFilterAmountPayload::encode,
					SetAllAeInputFilterAmountPayload::new,
					SetAllAeInputFilterAmountPayload::handle);

			CHANNEL.registerMessage(ID_TOGGLE_ALL_AE_INPUT_FILTER_UNLIMITED,
					ToggleAllAeInputFilterUnlimitedPayload.class,
					ToggleAllAeInputFilterUnlimitedPayload::encode,
					ToggleAllAeInputFilterUnlimitedPayload::new,
					ToggleAllAeInputFilterUnlimitedPayload::handle);

			CHANNEL.registerMessage(ID_TOGGLE_ALL_AE_INPUT_FILTER_NETWORK_STOCK,
					ToggleAllAeInputFilterNetworkStockPayload.class,
					ToggleAllAeInputFilterNetworkStockPayload::encode,
					ToggleAllAeInputFilterNetworkStockPayload::new,
					ToggleAllAeInputFilterNetworkStockPayload::handle);

			CHANNEL.registerMessage(ID_AE_INPUT_OUTPUT_SLOT,
					AeInputOutputSlotPayload.class,
					AeInputOutputSlotPayload::encode,
					AeInputOutputSlotPayload::new,
					AeInputOutputSlotPayload::handle);

			CHANNEL.registerMessage(ID_OPEN_AE_INPUT_CONFIG,
					OpenAeInputConfigPayload.class,
					OpenAeInputConfigPayload::encode,
					OpenAeInputConfigPayload::new,
					OpenAeInputConfigPayload::handle);

			CHANNEL.registerMessage(ID_SET_AE_INPUT_TAG_FILTER,
					SetAeInputTagFilterPayload.class,
					SetAeInputTagFilterPayload::encode,
					SetAeInputTagFilterPayload::new,
					SetAeInputTagFilterPayload::handle);

			// S→C AE2 消息
			CHANNEL.registerMessage(ID_SYNC_AE_INPUT_FILTER_ENTRIES,
					SyncAeInputFilterEntriesPayload.class,
					SyncAeInputFilterEntriesPayload::encode,
					SyncAeInputFilterEntriesPayload::new,
					SyncAeInputFilterEntriesPayload::handle);

			CHANNEL.registerMessage(ID_SYNC_AE_INPUT_TAG_FILTER,
					SyncAeInputTagFilterPayload.class,
					SyncAeInputTagFilterPayload::encode,
					SyncAeInputTagFilterPayload::new,
					SyncAeInputTagFilterPayload::handle);
		}
	}

	/**
	 * 服务端处理：万象创世过滤配置同步
	 * <p>
	 * 处理流程：
	 * <ol>
	 *   <li>频率限制 — 每玩家 3 秒冷却</li>
	 *   <li>权限校验 — 非 OP 玩家拒绝</li>
	 *   <li>配置加载状态校验 — SERVER_SPEC 未加载时拒绝</li>
	 *   <li>过滤模式校验 — 枚举名称必须可解析</li>
	 *   <li>蜜蜂类型列表校验 — 逐条 ResourceLocation 格式校验 + 去重</li>
	 *   <li>写入配置 + spec.save() 持久化</li>
	 *   <li>失效过滤缓存 — 让下次 tick 重建反映最新配置</li>
	 * </ol>
	 */
	static void handleFilterConfigSync(FilterConfigSyncPayload payload, Supplier<NetworkEvent.Context> ctx) {
		ServerPlayer serverPlayer = ctx.get().getSender();
		if (serverPlayer == null) {
			return;
		}

		// 0. 频率限制：每玩家 3 秒冷却，防止恶意客户端高频发包
		UUID playerId = serverPlayer.getUUID();
		long now = System.currentTimeMillis();
		if (packetCounter.incrementAndGet() % LAZY_CLEANUP_THRESHOLD == 0) {
			cleanupExpiredEntries(now);
		}
		AtomicLong lastAccept = FILTER_SYNC_LAST_ACCEPT.computeIfAbsent(playerId, k -> new AtomicLong(0));
		long last = lastAccept.get();
		if (now - last < FILTER_SYNC_COOLDOWN_MS) {
			long remainingSeconds = (FILTER_SYNC_COOLDOWN_MS - (now - last) + 999) / 1000;
			serverPlayer.sendSystemMessage(Component.translatable(
					"productivebeesgenesis.config.sync.rate_limited", remainingSeconds));
			return;
		}
		if (!lastAccept.compareAndSet(last, now)) {
			return;
		}

		// 1. 权限校验
		if (!serverPlayer.hasPermissions(REQUIRED_PERMISSION_LEVEL)) {
			serverPlayer.sendSystemMessage(Component.translatable(
					"productivebeesgenesis.config.sync.permission_denied"));
			LogThrottle.warn("filter_sync_permission", "玩家 {} 尝试修改服务端过滤配置但权限不足",
					serverPlayer.getName().getString());
			return;
		}

		// 2. 配置加载状态校验
		if (!ModConfig.SERVER_SPEC.isLoaded()) {
			serverPlayer.sendSystemMessage(Component.translatable(
					"productivebeesgenesis.config.sync.not_loaded"));
			LogThrottle.warn("filter_sync_spec_not_loaded", "收到过滤配置同步包但 SERVER_SPEC 未加载");
			return;
		}

		// 2.5 防御性输入长度校验
		String filterModeName = payload.filterModeName();
		if (filterModeName == null
				|| filterModeName.length() > NetworkSecurityConstants.MAX_FILTER_MODE_NAME_LENGTH) {
			LogThrottle.warn("filter_sync_mode_too_long", "收到过长的过滤模式名称：长度 {}",
					filterModeName == null ? "null" : filterModeName.length());
			return;
		}
		List<String> rawBeeTypes = payload.beeTypes();
		if (rawBeeTypes != null && rawBeeTypes.size() > NetworkSecurityConstants.MAX_BEE_TYPES_LIST_SIZE) {
			LogThrottle.warn("filter_sync_list_too_long", "收到过长的蜜蜂类型列表：大小 {}", rawBeeTypes.size());
			return;
		}

		// 3. 过滤模式校验
		ModConfig.FilterMode filterMode;
		try {
			filterMode = ModConfig.FilterMode.valueOf(filterModeName);
		} catch (IllegalArgumentException e) {
			serverPlayer.sendSystemMessage(Component.translatable(
					"productivebeesgenesis.config.sync.invalid_mode", filterModeName));
			LogThrottle.warn("filter_sync_invalid_mode", "收到无效的过滤模式: {}", filterModeName);
			return;
		}

		// 4. 蜜蜂类型列表校验（格式 + 去重）
		List<String> validated = validateAndDeduplicate(payload.beeTypes(), serverPlayer);
		if (validated == null) {
			return;
		}

		// 5. 写入配置并持久化
		try {
			ModConfig.SERVER.myriadCreationsFilteredBeeTypes.set(validated);
			ModConfig.SERVER.myriadCreationsFilterMode.set(filterMode);
			ModConfig.SERVER_SPEC.save();
			MyriadCreationsEventHandler.invalidateFilterCache();
		} catch (Exception e) {
			LogThrottle.error("filter_sync_exception", "处理过滤配置同步包时发生异常", e);
			serverPlayer.sendSystemMessage(Component.translatable(
					"productivebeesgenesis.config.sync.error"));
		}
	}

	/**
	 * 校验蜜蜂类型列表并去重
	 */
	private static List<String> validateAndDeduplicate(List<String> beeTypes, ServerPlayer serverPlayer) {
		LinkedHashSet<String> validated = new LinkedHashSet<>(beeTypes.size());
		for (String raw : beeTypes) {
			if (raw == null || raw.isBlank()) {
				continue;
			}
			String trimmed = raw.trim();
			if (trimmed.length() > NetworkSecurityConstants.MAX_BEE_TYPE_KEY_LENGTH) {
				serverPlayer.sendSystemMessage(Component.translatable(
						"productivebeesgenesis.config.sync.invalid_type", trimmed));
				LogThrottle.warn("filter_sync_bee_too_long", "收到过长的蜜蜂类型：长度 {}", trimmed.length());
				return null;
			}
			if (!ModConfig.isValidBeeTypeEntry(trimmed)) {
				serverPlayer.sendSystemMessage(Component.translatable(
						"productivebeesgenesis.config.sync.invalid_type", trimmed));
				LogThrottle.warn("filter_sync_invalid_bee", "收到无效的蜜蜂类型: {}", trimmed);
				return null;
			}
			validated.add(trimmed);
		}
		return new ArrayList<>(validated);
	}

	/**
	 * 清理过期的频率限制条目
	 */
	private static void cleanupExpiredEntries(long now) {
		FILTER_SYNC_LAST_ACCEPT.entrySet().removeIf(entry ->
				now - entry.getValue().get() > ENTRY_EXPIRATION_MS);
	}

	/**
	 * 清理指定玩家的过滤同步频率限制记录（玩家下线时调用）
	 */
	public static void clearFilterSyncRateLimit(UUID uuid) {
		FILTER_SYNC_LAST_ACCEPT.remove(uuid);
	}
}
