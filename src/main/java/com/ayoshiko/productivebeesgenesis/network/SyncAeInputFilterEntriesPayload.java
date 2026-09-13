package com.ayoshiko.productivebeesgenesis.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
	 * 服务端 → 客户端：同步 per-tile AE2 输入过滤器的完整条目列表
	 * <br/>
	 * 携带 {@link BlockPos}、过滤模式 ordinal、精确模式标志和非空条目列表（含位置索引）。
	 * <p>
	 * <b>V15 变更</b>：
	 * <ul>
	 *   <li>从 {@code List<String> entries} 改为 {@code List<Integer> indices} +
	 *       {@code List<String> entries} 两个并行数组，携带槽位位置信息</li>
	 *   <li>仅同步非空槽位，客户端按 index 写入固定大小数组，保留位置固定语义</li>
	 * </ul>
	 * <p>
	 * <b>V13 变更</b>：
	 * <ul>
	 *   <li>entries 从 {@code List<ResourceLocation>} 改为 {@code List<String>}，
	 *       支持 #block 后缀（精确模式下区分蜜脾和蜜脾块）</li>
	 *   <li>新增 preciseMode 字段，同步精确模式开关状态</li>
	 * </ul>
	 * <p>
	 * <b>设计原因</b>：Mekanism 的 {@code SyncableInt}/{@code SyncableBoolean} 等 container tracker
	 * 仅支持原子类型同步，无法同步集合数据。故服务端在条目增删、模式切换、GUI 打开时推送此包。
	 *
	 * @param pos         方块坐标
	 * @param filterMode  过滤模式 ordinal（0=DISABLED, 1=WHITELIST, 2=BLACKLIST）
	 * @param preciseMode 精确模式（true=区分蜜脾/蜜脾块）
	 * @param indices     非空槽位的 index 列表（与 entries 平行）
	 * @param entries     非空槽位的 entry 字符串列表（与 indices 平行，可能含 #block 后缀）
	 * @param amounts     直连条目的每次拉取数量（与 entries 平行，非直连条目为 0）
	 * @param reserveAmounts 直连条目的网络库存保留量（与 entries 平行，非直连条目为 0）
	 * @param unlimited    直连条目的无限提供状态（与 entries 平行，非直连条目为 false）
	 * @param networkStock 直连条目的库存模式状态（与 entries 平行，非直连条目为 false）
	 * @param globalNetworkStock 是否对所有过滤器允许的蜜脾应用默认库存保留
	 * @param globalReserveAmount 过滤器级默认库存保留量
	 */
public record SyncAeInputFilterEntriesPayload(
		BlockPos pos,
		int filterMode,
		boolean preciseMode,
		List<Integer> indices,
	List<String> entries,
	List<Long> amounts,
	List<Long> reserveAmounts,
	List<Long> visibleAmounts,
	List<Boolean> unlimited,
	List<Boolean> networkStock,
	boolean unlimitedAllFallback,
	boolean globalNetworkStock,
	long globalReserveAmount
) {

	/** Backward-compatible constructor for callers that do not provide stock metadata. */
	public SyncAeInputFilterEntriesPayload(BlockPos pos, int filterMode, boolean preciseMode,
			List<Integer> indices, List<String> entries) {
		this(pos, filterMode, preciseMode, indices, entries,
				Collections.nCopies(entries.size(), 0L),
				Collections.nCopies(entries.size(), 0L),
				Collections.nCopies(entries.size(), 0L),
				Collections.nCopies(entries.size(), false),
				Collections.nCopies(entries.size(), false), false, false, 0L);
	}

	public SyncAeInputFilterEntriesPayload(FriendlyByteBuf buf) {
		this(buf.readBlockPos(),
				buf.readVarInt(),
				buf.readBoolean(),
				readVarIntList(buf),
				readStringList(buf),
				readVarLongList(buf),
				readVarLongList(buf),
				readVarLongList(buf),
				readBooleanList(buf),
				readBooleanList(buf),
				buf.readBoolean(),
				buf.readBoolean(),
				buf.readVarLong());
	}

	public static void encode(SyncAeInputFilterEntriesPayload msg, FriendlyByteBuf buf) {
		buf.writeBlockPos(msg.pos);
		buf.writeVarInt(msg.filterMode);
		buf.writeBoolean(msg.preciseMode);
		writeVarIntList(buf, msg.indices);
		writeStringList(buf, msg.entries);
		writeVarLongList(buf, msg.amounts);
		writeVarLongList(buf, msg.reserveAmounts);
		writeVarLongList(buf, msg.visibleAmounts);
		writeBooleanList(buf, msg.unlimited);
		writeBooleanList(buf, msg.networkStock);
		buf.writeBoolean(msg.unlimitedAllFallback);
		buf.writeBoolean(msg.globalNetworkStock);
		buf.writeVarLong(msg.globalReserveAmount);
	}

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> {
			DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
				Level level = Minecraft.getInstance().level;
				if (level != null) {
					Ae2FilterPayloadHandlers.handleSyncAeInputFilterEntries(this, level);
				}
			});
		});
		ctx.get().setPacketHandled(true);
	}

	private static void writeVarIntList(FriendlyByteBuf buf, List<Integer> list) {
		buf.writeVarInt(list.size());
		list.forEach(buf::writeVarInt);
	}

	private static void writeStringList(FriendlyByteBuf buf, List<String> list) {
		buf.writeVarInt(list.size());
		list.forEach(s -> buf.writeUtf(s, NetworkSecurityConstants.MAX_FILTER_ENTRY_LENGTH));
	}

	private static void writeVarLongList(FriendlyByteBuf buf, List<Long> list) {
		buf.writeVarInt(list.size());
		list.forEach(buf::writeVarLong);
	}

	private static void writeBooleanList(FriendlyByteBuf buf, List<Boolean> list) {
		buf.writeVarInt(list.size());
		list.forEach(buf::writeBoolean);
	}

	private static List<Integer> readVarIntList(FriendlyByteBuf buf) {
		int size = buf.readVarInt();
		List<Integer> list = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			list.add(buf.readVarInt());
		}
		return list;
	}

	private static List<String> readStringList(FriendlyByteBuf buf) {
		int size = buf.readVarInt();
		List<String> list = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			list.add(buf.readUtf(NetworkSecurityConstants.MAX_FILTER_ENTRY_LENGTH));
		}
		return list;
	}

	private static List<Long> readVarLongList(FriendlyByteBuf buf) {
		int size = buf.readVarInt();
		List<Long> list = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			list.add(buf.readVarLong());
		}
		return list;
	}

	private static List<Boolean> readBooleanList(FriendlyByteBuf buf) {
		int size = buf.readVarInt();
		List<Boolean> list = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			list.add(buf.readBoolean());
		}
		return list;
	}
}
