package com.ayoshiko.productivebeesgenesis.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端 → 服务端：一键切换全部 AE 输入过滤直连条目的无限拉取状态。 */
public record ToggleAllAeInputFilterUnlimitedPayload(BlockPos pos) {

	public ToggleAllAeInputFilterUnlimitedPayload(FriendlyByteBuf buf) {
		this(buf.readBlockPos());
	}

	public static void encode(ToggleAllAeInputFilterUnlimitedPayload msg, FriendlyByteBuf buf) {
		buf.writeBlockPos(msg.pos);
	}

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> Ae2FilterPayloadHandlers.handleToggleAllAeInputFilterUnlimited(this, ctx));
		ctx.get().setPacketHandled(true);
	}
}
