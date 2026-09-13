package com.ayoshiko.productivebeesgenesis.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端到服务端：在机械蜂箱 GUI 中给指定蜜蜂喂食基因小食。
 * <p>
 * 数据包仅携带蜂箱位置和槽位索引。服务端会重新校验玩家容器、距离、槽位内容和
 * 光标物品，客户端不能直接提交蜜蜂 NBT 或基因数据。
 *
 * @param pos       蜂箱方块坐标
 * @param slotIndex 蜜蜂槽位索引
 */
public record ApiaryFeedBeePayload(
		BlockPos pos,
		int slotIndex
) {

	public ApiaryFeedBeePayload(FriendlyByteBuf buf) {
		this(buf.readBlockPos(), buf.readInt());
	}

	public static void encode(ApiaryFeedBeePayload msg, FriendlyByteBuf buf) {
		buf.writeBlockPos(msg.pos);
		buf.writeInt(msg.slotIndex);
	}

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> ApiaryPayloadHandlers.handleApiaryFeedBee(this, ctx));
		ctx.get().setPacketHandled(true);
	}
}
