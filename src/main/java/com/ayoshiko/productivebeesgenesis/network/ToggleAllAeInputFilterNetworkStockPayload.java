package com.ayoshiko.productivebeesgenesis.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client-to-server toggle for network-stock mode on all exact entries. */
public record ToggleAllAeInputFilterNetworkStockPayload(BlockPos pos) {

	public ToggleAllAeInputFilterNetworkStockPayload(FriendlyByteBuf buf) {
		this(buf.readBlockPos());
	}

	public static void encode(ToggleAllAeInputFilterNetworkStockPayload msg, FriendlyByteBuf buf) {
		buf.writeBlockPos(msg.pos);
	}

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> Ae2FilterPayloadHandlers.handleToggleAllAeInputFilterNetworkStock(this, ctx));
		ctx.get().setPacketHandled(true);
	}
}
