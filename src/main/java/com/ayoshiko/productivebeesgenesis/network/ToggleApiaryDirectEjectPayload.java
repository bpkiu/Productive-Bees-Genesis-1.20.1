package com.ayoshiko.productivebeesgenesis.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端到服务端：切换单个机械蜂箱的相邻离心机快速直连通道。 */
public record ToggleApiaryDirectEjectPayload(BlockPos pos) {

	public ToggleApiaryDirectEjectPayload(FriendlyByteBuf buf) {
		this(buf.readBlockPos());
	}

	public static void encode(ToggleApiaryDirectEjectPayload msg, FriendlyByteBuf buf) {
		buf.writeBlockPos(msg.pos);
	}

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> ApiaryPayloadHandlers.handleToggleApiaryDirectEject(this, ctx));
		ctx.get().setPacketHandled(true);
	}
}
