package com.ayoshiko.productivebeesgenesis.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端 → 服务端：批量设置直连条目的拉取数量或过滤器级默认库存保留量。 */
public record SetAllAeInputFilterAmountPayload(BlockPos pos, long amount, boolean reserve) {
	public SetAllAeInputFilterAmountPayload(BlockPos pos, long amount) {
		this(pos, amount, false);
	}

	public SetAllAeInputFilterAmountPayload(FriendlyByteBuf buf) {
		this(buf.readBlockPos(), buf.readVarLong(), buf.readBoolean());
	}

	public static void encode(SetAllAeInputFilterAmountPayload msg, FriendlyByteBuf buf) {
		buf.writeBlockPos(msg.pos);
		buf.writeVarLong(msg.amount);
		buf.writeBoolean(msg.reserve);
	}

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> Ae2FilterPayloadHandlers.handleSetAllAeInputFilterAmount(this, ctx));
		ctx.get().setPacketHandled(true);
	}
}
