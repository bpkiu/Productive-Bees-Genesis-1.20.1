package com.ayoshiko.productivebeesgenesis.apiary;

import com.ayoshiko.productivebeesgenesis.util.DevLog;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.common.inventory.container.sync.SyncableEnum;
import mekanism.common.inventory.container.sync.SyncableFloat;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.container.sync.SyncableItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 蜜蜂槽序列化器
 * <br/>
 * 从 {@link ApiarySlotManager} 拆分，负责蜜蜂槽数组的 NBT 持久化与客户端网络同步：
 * <ul>
 *   <li>{@link #saveBeeSlots} / {@link #loadBeeSlots} — 存档序列化/反序列化</li>
 *   <li>{@link #addContainerTrackers} — 容器追踪器同步蜜蜂状态到客户端</li>
 *   <li>{@link #serializeBeeDataCached} / {@link #deserializeBeeData} — beeData
 *       经 ItemStack NBT 载体编解码（网络同步用）</li>
 * </ul>
 * <p>
 * 通过组合关系持有 {@link ApiarySlotManager} 引用，访问蜜蜂槽数组。
 * <p>
 * <b>1.20.1 迁移说明</b>：Mekanism 10.4.16 没有 SyncableByteArray（字节容器网络 API 亦无
 * byte[] PropertyData），改用 {@link SyncableItemStack} 同步：将蜜蜂 NBT 挂载在纸物品的
 * tag 上作为载体传输（客户端经 ItemStackPropertyData 原生解码），符合本工程
 * 「NBT/getOrCreateTag 替代 DataComponent」迁移约定。
 * 空槽位共享 {@link ItemStack#EMPTY}，客户端据此判断槽位为空。
 * <p>
 * 线程安全：序列化在服务端主线程执行；网络同步的 dirty 检查基于载体引用相等，
 * SyncableItemStack 内部保证同步线程安全。
 */
public class ApiarySlotSerializer {

	/** NBT key — 蜜蜂槽数组（带模组前缀避免冲突） */
	public static final String NBT_KEY_BEE_SLOTS = "productivebeesgenesis_apiary_bee_slots";

	/** 载体 ItemStack 内承载蜜蜂 NBT 的子键 */
	private static final String CARRIER_KEY_BEE_DATA = "BeeData";

	/** 所属槽位管理器 — 访问蜜蜂槽数组与数量 */
	private final ApiarySlotManager manager;

	/** per-slot 序列化缓存 — beeData 引用未变时复用载体实例，避免每 gameTick 重建 ItemStack（v1.0.2） */
	private ItemStack[] serializedBeeDataCache;

	/** per-slot 缓存对应的 beeData 引用 — 引用相等即缓存命中 */
	private CompoundTag[] serializedSourceCache;

	/**
	 * 构造蜜蜂槽序列化器
	 *
	 * @param manager 所属槽位管理器
	 */
	ApiarySlotSerializer(ApiarySlotManager manager) {
		this.manager = manager;
	}

	// ===== NBT 序列化 =====

	/**
	 * 保存蜜蜂槽数组到 NBT
	 * <br/>
	 * 使用 ListTag 存储，每个 BeeSlot 序列化为 CompoundTag。
	 * 复用 PB 原生 Occupant 格式存储 beeData（entity_data 字段）。
	 * 空 BeeSlot 跳过以减小存档体积。
	 *
	 * @param nbt 目标 NBT 标签
	 */
	void saveBeeSlots(CompoundTag nbt) {
		ListTag list = new ListTag();
		BeeSlot[] beeSlots = manager.getBeeSlots();
		int beeSlotCount = manager.getBeeSlotCount();
		for (int i = 0; i < beeSlotCount; i++) {
			BeeSlot slot = beeSlots[i];
			if (slot.isEmpty()) continue;
			CompoundTag slotNbt = new CompoundTag();
			// 保存绝对槽位索引：列表会跳过空槽压缩存储，加载时按索引还原
			// 修复：selectedBeeSlot 等绝对索引在压缩/还原后不再错位
			slotNbt.putInt("slot_index", i);
			if (slot.getBeeData() != null) {
				slotNbt.put("entity_data", slot.getBeeData());
			}
			slotNbt.putInt("ticks_in_hive", slot.getTicksInHive());
			slotNbt.putInt("min_occupation_ticks", slot.getMinOccupationTicks());
			// 模块1修复：持久化基础最小 occupation ticks，避免 adjusted 值被回写后下一 tick 再次乘以倍率
			slotNbt.putInt("base_min_occupation_ticks", slot.getBaseMinOccupationTicks());
			slotNbt.putBoolean("has_nectar", slot.hasNectar());
			slotNbt.putString("state", slot.getState().name());
			slotNbt.putFloat("progress", slot.getProgress());
			list.add(slotNbt);
		}
		nbt.put(NBT_KEY_BEE_SLOTS, list);
	}

	/**
	 * 从 NBT 加载蜜蜂槽数组
	 * <br/>
	 * 兼容空槽位（ListTag 长度 < beeSlotCount 时，剩余槽位保持空状态）。
	 * 兼容旧版存档：若 ListTag 长度大于当前 beeSlotCount（如从工厂版降级到初始版），
	 * 仅加载前 beeSlotCount 个槽位，多余数据忽略。
	 *
	 * @param nbt 源 NBT 标签
	 */
	void loadBeeSlots(CompoundTag nbt) {
		BeeSlot[] beeSlots = manager.getBeeSlots();
		int beeSlotCount = manager.getBeeSlotCount();
		// 先清空所有槽位，防止旧数据残留
		for (int i = 0; i < beeSlotCount; i++) {
			beeSlots[i].clear();
		}
		if (!nbt.contains(NBT_KEY_BEE_SLOTS, Tag.TAG_LIST)) return;
		ListTag list = nbt.getList(NBT_KEY_BEE_SLOTS, Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag slotNbt = list.getCompound(i);
			// 优先按保存的绝对索引还原（防止压缩存储导致槽位左移、绝对索引错位）；
			// 旧存档无 slot_index 键时回退到顺序填充
			int target = slotNbt.contains("slot_index", Tag.TAG_ANY_NUMERIC)
					? slotNbt.getInt("slot_index") : i;
			if (target < 0 || target >= beeSlotCount) {
				continue;
			}
			BeeSlot slot = beeSlots[target];
			if (slotNbt.contains("entity_data", Tag.TAG_COMPOUND)) {
				// .copy() 防止共享 NBT 引用导致意外修改
				slot.setBeeData(slotNbt.getCompound("entity_data").copy());
			}
			slot.setTicksInHive(slotNbt.getInt("ticks_in_hive"));
			slot.setMinOccupationTicks(slotNbt.getInt("min_occupation_ticks"));
			// 模块1修复：读取基础最小 occupation ticks，含老存档迁移逻辑
			if (slotNbt.contains("base_min_occupation_ticks")) {
				// 新存档：直接读取 base 值
				slot.setBaseMinOccupationTicks(slotNbt.getInt("base_min_occupation_ticks"));
			} else {
				// 老存档迁移：无 base 字段时从 min_occupation_ticks 推断
				// 代码审查修复：上界从配置读取（默认1200），而非硬编码10000
				// 避免被bug污染的较低值（如500）被误迁移为base，导致卸载升级后无法恢复
				int upperBound = 1200; // fallback 默认值
				try {
					upperBound = com.ayoshiko.productivebeesgenesis.config.ModConfig.SERVER.apiaryProcessingTime.get();
				} catch (NullPointerException e) {
					// 配置未加载时使用默认值 1200
				}
				int oldMinTicks = slotNbt.getInt("min_occupation_ticks");
				// 仅当值在合理范围（>0 且 <=配置值）时迁移，否则设为0触发fallback到配置默认值
				slot.setBaseMinOccupationTicks((oldMinTicks > 0 && oldMinTicks <= upperBound) ? oldMinTicks : 0);
			}
			slot.setHasNectar(slotNbt.getBoolean("has_nectar"));
			try {
				slot.setState(BeeState.valueOf(slotNbt.getString("state")));
			} catch (IllegalArgumentException e) {
				// 未知状态名（可能来自未来版本），回退到 IDLE
				DevLog.warn("nbt_serialize", "加载蜜蜂槽位时遇到未知状态: {}", slotNbt.getString("state"));
				slot.setState(BeeState.IDLE);
			}
			slot.setProgress(slotNbt.getFloat("progress"));
		}
	}

	// ===== 网络同步框架 =====

	/**
	 * 添加容器追踪器 — 同步蜜蜂状态到客户端
	 * <br/>
	 * 每只蜜蜂同步：
	 * <ul>
	 *   <li>state（枚举 ordinal）— 状态灯渲染</li>
	 *   <li>progress（float）— 进度条渲染</li>
	 *   <li>hasNectar（boolean）— 状态灯渲染</li>
	 *   <li>beeData（ItemStack NBT 载体）— 蜜蜂完整 NBT，供客户端渲染蜜蜂实体、名称、tooltip</li>
	 *   <li>ticksInHive（int）— tooltip 进度显示</li>
	 *   <li>minOccupationTicks（int）— tooltip 进度显示</li>
	 * </ul>
	 * <p>
	 * beeData 以 {@link SyncableItemStack} 载体同步（dirty 检查基于缓存引用相等，
	 * 仅在蜜蜂装入/取出时触发同步；载体仅在 beeData 引用变化时重建一次）。
	 *
	 * @param container 待注册追踪器的容器
	 */
	void addContainerTrackers(MekanismContainer container) {
		BeeSlot[] beeSlots = manager.getBeeSlots();
		int beeSlotCount = manager.getBeeSlotCount();
		// 惰性初始化 per-slot 序列化缓存（tracker 注册仅在 GUI 打开时执行）
		if (serializedBeeDataCache == null || serializedBeeDataCache.length != beeSlotCount) {
			serializedBeeDataCache = new ItemStack[beeSlotCount];
			serializedSourceCache = new CompoundTag[beeSlotCount];
		}
		for (int i = 0; i < beeSlotCount; i++) {
			final BeeSlot slot = beeSlots[i];
			final int slotIndex = i;
			// 状态枚举 — 通过 ordinal 同步
			container.track(SyncableEnum.create(
					ApiarySlotSerializer::stateByOrdinal,
					BeeState.IDLE,
					slot::getState,
					slot::setState
			));
			// 生产进度 — 供 GUI 进度条渲染
			container.track(SyncableFloat.create(
					slot::getProgress,
					slot::setProgress
			));
			// 是否有蜜 — 供 GUI 状态灯渲染
			container.track(SyncableBoolean.create(
					slot::hasNectar,
					slot::setHasNectar
			));
			// 蜜蜂完整 NBT（ItemStack 载体形式）— 供客户端渲染蜜蜂实体、名称、tooltip
			// v1.0.2：同步 dirty 检查每 gameTick 调用 getter，
			// BeeSlot 仅在蜜蜂实质变化时更新 beeData 引用，引用相等即复用载体，
			// 载体重建次数从 每 tick×N 降为 仅蜜蜂变化时。
			container.track(SyncableItemStack.create(
					() -> serializeBeeDataCached(slotIndex, slot),
					applySyncedBeeData(slot)
			));
			// 已居住 tick 数 — tooltip 进度显示
			container.track(SyncableInt.create(
					slot::getTicksInHive,
					slot::setTicksInHive
			));
			// 最小 occupation ticks — tooltip 进度显示
			container.track(SyncableInt.create(
					slot::getMinOccupationTicks,
					slot::setMinOccupationTicks
			));
		}
	}

	/** 客户端侧 setter：从同步载体还原 beeData 到槽位 */
	private java.util.function.Consumer<ItemStack> applySyncedBeeData(BeeSlot slot) {
		return stack -> slot.setBeeData(deserializeBeeData(stack));
	}

	/**
	 * 带引用缓存的 beeData 载体构建（网络同步 getter 专用，v1.0.2）
	 * <br/>
	 * {@code BeeSlot.setBeeData} 仅在蜜蜂实质变化（装入/取出/NBT 修改）时更新引用，
	 * 因此引用相等即可安全复用上次构建的载体 ItemStack。空槽位返回共享 {@code ItemStack.EMPTY}。
	 * 载体实例不落盘、不入真实背包，仅存在于 Mekanism 属性同步通道。
	 *
	 * @param slotIndex 槽位索引（缓存数组下标）
	 * @param slot      蜜蜂槽
	 * @return 含蜜蜂 NBT 的载体栈（缓存实例，调用方不得修改）
	 */
	private ItemStack serializeBeeDataCached(int slotIndex, BeeSlot slot) {
		CompoundTag beeData = slot.getBeeData();
		if (beeData == null) return ItemStack.EMPTY;
		if (beeData == serializedSourceCache[slotIndex]) {
			return serializedBeeDataCache[slotIndex];
		}
		ItemStack carrier = new ItemStack(Items.PAPER);
		carrier.getOrCreateTag().put(CARRIER_KEY_BEE_DATA, beeData.copy());
		serializedSourceCache[slotIndex] = beeData;
		serializedBeeDataCache[slotIndex] = carrier;
		return carrier;
	}

	/**
	 * 从同步载体提取 beeData（网络同步）
	 * <br/>
	 * 空载体或缺失标记时返回 null（表示空槽位）。取出副本避免客户端/服务端共享 NBT 引用。
	 *
	 * @param carrier 同步而来的载体栈
	 * @return 蜜蜂 NBT 数据，空载体返回 null
	 */
	private static CompoundTag deserializeBeeData(ItemStack carrier) {
		if (carrier == null || carrier.isEmpty()) return null;
		if (!carrier.hasTag() || !carrier.getTag().contains(CARRIER_KEY_BEE_DATA, Tag.TAG_COMPOUND)) {
			return null;
		}
		return carrier.getTag().getCompound(CARRIER_KEY_BEE_DATA).copy();
	}

	/**
	 * 通过 ordinal 查找 BeeState（带边界保护）
	 *
	 * @param ordinal 枚举序号
	 * @return 对应的 BeeState，越界返回 IDLE
	 */
	private static BeeState stateByOrdinal(int ordinal) {
		BeeState[] values = BeeState.values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : BeeState.IDLE;
	}
}
