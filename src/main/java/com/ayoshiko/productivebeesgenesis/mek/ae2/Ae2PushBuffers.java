package com.ayoshiko.productivebeesgenesis.mek.ae2;

import appeng.api.networking.energy.IEnergySource;
import appeng.api.stacks.AEItemKey;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import mekanism.common.capabilities.energy.MachineEnergyContainer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AE2 推送缓冲区 — 持有所有 AE2 输出推送操作的可重用状态。
 * <br/>
 * 为减少 GC 压力，内部使用对象池模式重用 List 和 Entry 对象。
 * 每次 tick 开始时重置，累积输出槽物品，然后批量推送到 AE2 网络。
 * <p>
 * <b>TODO 未落地依赖</b>：以下两个类尚未实现，落地前本文件无法编译通过：
 * <ul>
 *   <li>{@code Ae2InsertCostTracker} — per-tile 插入成本追踪器，提供
 *       {@code isExhausted(long gameTick)} 与 {@code averageCostNanos()} 等方法。
 *       替换 {@link Ae2GlobalInsertBudget} 全服预算以获取 per-tile 精细控制。</li>
 *   <li>{@code Ae2DirectItemPushSession} — 直接推送会话，用于新生成物品直接写入 AE
 *       而非先落入本地输出槽。</li>
 * </ul>
 * 落地后请移除本说明并填充相应字段的初始化逻辑。
 */
final class Ae2PushBuffers {

	/** 直接推送会话（用于新生成物品直接写入 AE 网络） */
	Ae2DirectItemPushSession directItemPushSession;

	/** per-tile 插入成本追踪器（per-tile 时间预算，替代全服预算） */
	final Ae2InsertCostTracker insertCostTracker;

	/** Mekanism 能量容器到 AE2 能量源的适配器（懒初始化，跨 tick 持有） */
	private volatile MekEnergyToAeSource energyAdapter;

	/** 待推送槽位条目列表（每 tick 重置） */
	final List<Ae2SlotEntry> entries = new ArrayList<>();

	/** 槽位条目对象池（避免每 tick 分配） */
	final List<Ae2SlotEntry> entryPool = new ArrayList<>();

	/** 对象池游标 */
	int entryPoolCursor = 0;

	/** 输出槽扫描游标（round-robin 起始位置） */
	int outputSlotScanCursor = 0;

	/** 按 key 分组的槽位条目映射（合并传递时填充） */
	final Map<AEItemKey, List<Ae2SlotEntry>> keyToEntries = new Object2ObjectOpenHashMap<>();

	/** 按 key 分组的槽位条目 List 对象池 */
	final List<List<Ae2SlotEntry>> keyEntryListPool = new ArrayList<>();

	/** keyEntryListPool 游标 */
	int keyEntryListPoolCursor = 0;

	/** 按 key 聚合的待推送总数 */
	final Object2LongLinkedOpenHashMap<AEItemKey> keyToTotalCount = new Object2LongLinkedOpenHashMap<>();

	// ===== 输入拉取器状态 =====

	/** 拉取条目列表（使用 Object 类型保持与 {@link Ae2InputPuller.PullEntry} 解耦） */
	final List<Object> pullList = new ArrayList<>();

	/** 拉取条目对象池 */
	final List<Object> pullEntryPool = new ArrayList<>();

	/** pullEntryPool 游标 */
	int pullEntryPoolCursor = 0;

	/** 当前 tick 已收集的拉取 key 集合（去重用） */
	final Set<AEItemKey> pullKeys = new HashSet<>();

	// ===== 候选扫描状态 =====

	/** 扫描前缀 key 列表（游标轮转的可见前缀） */
	final List<AEItemKey> scanPrefixKeys = new ArrayList<>();

	/** 扫描选中的 key 列表 */
	final List<AEItemKey> scanSelectedKeys = new ArrayList<>();

	/** 扫描候选数量映射 */
	final Ae2PullCandidateAmounts scanCandidateAmounts = new Ae2PullCandidateAmounts();

	/** 扫描熔炼候选 key 列表 */
	final List<AEItemKey> scanSmeltingCandidateKeys = new ArrayList<>();

	/** 扫描候选 key 列表（最近一次刷新的可见候选） */
	final List<AEItemKey> scanCandidateKeys = new ArrayList<>();

	/** 上次扫描候选源（KeyCounter 等） */
	volatile Object scanCandidateSource;

	/** 上次扫描候选刷新游戏刻 */
	volatile long scanCandidateRefreshTick;

	/** 上次扫描候选的配方版本 */
	volatile long scanCandidateRecipeVersion;

	/** 上次扫描候选时是否启用熔炼 */
	volatile boolean scanCandidateSmeltingEnabled;

	/** 上次扫描候选的标签 generation */
	volatile int scanCandidateTagGeneration;

	// ===== 子模块缓存 =====

	/** 熔炼输入缓存（按 AEItemKey 缓存是否为熔炼配方输入） */
	final Ae2SmeltingInputCache smeltingInputCache = new Ae2SmeltingInputCache();

	/** 标签过滤器结果缓存 */
	final Ae2TagFilterCache tagFilterCache = new Ae2TagFilterCache();

	/** 物品指纹缓存（AEItemKey → 字符串指纹） */
	final Ae2FingerprintCache fingerprintCache = new Ae2FingerprintCache();

	/** 输入槽容量临时数组（按槽位索引） */
	private long[] inputSlotCapacities;

	Ae2PushBuffers() {
		insertCostTracker = new Ae2InsertCostTracker();
	}

	/**
	 * 获取或更新能量适配器。
	 * <br/>
	 * 首次调用时构造适配器；后续调用复用同一实例。
	 *
	 * @param energyContainer 宿主 Mekanism 能量容器
	 * @return AE2 能量源
	 */
	IEnergySource getEnergyAdapter(MachineEnergyContainer<?> energyContainer) {
		// TODO MekEnergyToAeSource 无 updateContainer 方法；当前实现假设容器在宿主生命周期内固定不变，
		//      首次调用后复用同一适配器实例（与既有 Ae2OutputPusher.ReusableBuffers 行为一致）。
		//      若未来支持容器热切换，需扩展 MekEnergyToAeSource 暴露 updateContainer 方法。
		if (energyAdapter == null) {
			energyAdapter = new MekEnergyToAeSource(energyContainer);
		}
		return energyAdapter;
	}

	/**
	 * 借用拉取列表（每 tick 重置后复用）。
	 *
	 * @return 拉取列表
	 */
	List<Object> borrowPullList() {
		pullList.clear();
		return pullList;
	}

	/** 重置拉取条目对象池游标 */
	void resetPullEntryPool() {
		pullEntryPoolCursor = 0;
	}

	/**
	 * 借用扫描前缀 key 列表。
	 *
	 * @return 已清空的前缀 key 列表
	 */
	List<AEItemKey> borrowScanPrefixKeys() {
		scanPrefixKeys.clear();
		return scanPrefixKeys;
	}

	/**
	 * 借用扫描候选 key 列表。
	 *
	 * @return 已清空的候选 key 列表
	 */
	List<AEItemKey> borrowScanCandidateKeys() {
		scanCandidateKeys.clear();
		return scanCandidateKeys;
	}

	/**
	 * 借用扫描熔炼候选 key 列表。
	 *
	 * @return 已清空的熔炼候选 key 列表
	 */
	List<AEItemKey> borrowScanSmeltingCandidateKeys() {
		scanSmeltingCandidateKeys.clear();
		return scanSmeltingCandidateKeys;
	}

	/**
	 * 判断是否需要刷新扫描候选缓存。
	 *
	 * @param source           当前候选源
	 * @param currentTick      当前游戏刻
	 * @param recipeVersion    当前配方版本
	 * @param smeltingEnabled  当前熔炼开关
	 * @param tagGeneration    当前标签 generation
	 * @return true 表示需要刷新
	 */
	boolean needsScanCandidateRefresh(Object source, long currentTick, long recipeVersion,
			boolean smeltingEnabled, int tagGeneration) {
		return source != scanCandidateSource
				|| currentTick - scanCandidateRefreshTick > 20
				|| recipeVersion != scanCandidateRecipeVersion
				|| smeltingEnabled != scanCandidateSmeltingEnabled
				|| tagGeneration != scanCandidateTagGeneration;
	}

	/**
	 * 标记扫描候选已刷新 — 更新缓存元数据。
	 *
	 * @param source           当前候选源
	 * @param currentTick      当前游戏刻
	 * @param smeltingEnabled  当前熔炼开关
	 * @param tagGeneration    当前标签 generation
	 */
	void markScanCandidateRefresh(Object source, long currentTick, boolean smeltingEnabled, int tagGeneration) {
		scanCandidateSource = source;
		scanCandidateRefreshTick = currentTick;
		// 简化：recipeVersion 与 currentTick 共用，待落地真实版本号后分离
		scanCandidateRecipeVersion = currentTick;
		scanCandidateSmeltingEnabled = smeltingEnabled;
		scanCandidateTagGeneration = tagGeneration;
	}

	/** 失效扫描候选缓存（gridChanged 等触发） */
	void invalidateScanCandidateCache() {
		scanCandidateSource = null;
		scanCandidateRefreshTick = 0;
	}

	/**
	 * 借用扫描选中 key 列表。
	 *
	 * @return 已清空的选中 key 列表
	 */
	List<AEItemKey> borrowScanSelectedKeys() {
		scanSelectedKeys.clear();
		return scanSelectedKeys;
	}

	/**
	 * 借用扫描候选数量映射。
	 *
	 * @return 已清空的候选数量映射
	 */
	Ae2PullCandidateAmounts borrowScanCandidateAmounts() {
		scanCandidateAmounts.clear();
		return scanCandidateAmounts;
	}

	/**
	 * 借用输入槽容量数组（按需扩容）。
	 *
	 * @param size 所需容量
	 * @return 已清零的输入槽容量数组（前 size 项为 0L）
	 */
	long[] borrowInputSlotCapacities(int size) {
		if (inputSlotCapacities == null || inputSlotCapacities.length < size) {
			inputSlotCapacities = new long[Math.max(size, 16)];
		}
		Arrays.fill(inputSlotCapacities, 0, size, 0L);
		return inputSlotCapacities;
	}

	/**
	 * 借用拉取 key 集合（去重用）。
	 *
	 * @return 已清空的 key 集合
	 */
	Set<AEItemKey> borrowPullKeys() {
		pullKeys.clear();
		return pullKeys;
	}
}
