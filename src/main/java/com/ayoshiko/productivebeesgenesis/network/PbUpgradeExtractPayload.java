package com.ayoshiko.productivebeesgenesis.network;

import com.ayoshiko.productivebeesgenesis.apiary.PbUpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
	 * 客户端 → 服务端：卸载指定类型的PB升级到输出槽
	 * <br/>
	 * 携带 {@link BlockPos} 和升级类型，服务端收到后从 pbUpgradeHandler
	 * 查找对应类型的第一个升级物品，移到升级输出槽。
	 * <p>
	 * 设计原因：GUI选择升级类型后点击卸载按钮，通过网络包通知服务端执行。
	 * 相比旧版按槽位索引提取，按类型提取更符合MEK升级窗口的交互模式。
	 *
	 * @param pos          蜂箱方块坐标
	 * @param upgradeTypeId 升级类型ID（对应PbUpgradeType.id）
	 */
public record PbUpgradeExtractPayload(
		BlockPos pos,
		String upgradeTypeId,
		boolean removeAll
) {

	public PbUpgradeExtractPayload(BlockPos pos, String upgradeTypeId) {
		this(pos, upgradeTypeId, false);
	}

	public PbUpgradeExtractPayload(FriendlyByteBuf buf) {
		this(buf.readBlockPos(), buf.readUtf(64), buf.readBoolean());
	}

	public static void encode(PbUpgradeExtractPayload msg, FriendlyByteBuf buf) {
		buf.writeBlockPos(msg.pos);
		buf.writeUtf(msg.upgradeTypeId, 64);
		buf.writeBoolean(msg.removeAll);
	}

	public PbUpgradeType getUpgradeType() {
		return PbUpgradeType.byId(upgradeTypeId);
	}

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> ApiaryPayloadHandlers.handlePbUpgradeExtract(this, ctx));
		ctx.get().setPacketHandled(true);
	}
}
