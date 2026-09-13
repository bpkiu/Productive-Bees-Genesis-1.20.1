package com.ayoshiko.productivebeesgenesis.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client-to-server update for one exact AE input entry's pull amount or stock reserve. */
public record SetAeInputFilterAmountPayload(BlockPos pos, int slotIndex, long amount, boolean reserve) {
	public SetAeInputFilterAmountPayload(BlockPos pos, int slotIndex, long amount) {
		this(pos, slotIndex, amount, false);
	}

	public SetAeInputFilterAmountPayload(FriendlyByteBuf buf) {
		this(buf.readBlockPos(), buf.readVarInt(), buf.readVarLong(), buf.readBoolean());
	}

	public static void encode(SetAeInputFilterAmountPayload msg, FriendlyByteBuf buf) {
		buf.writeBlockPos(msg.pos);
		buf.writeVarInt(msg.slotIndex);
		buf.writeVarLong(msg.amount);
		buf.writeBoolean(msg.reserve);
	}

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> Ae2FilterPayloadHandlers.handleSetAeInputFilterAmount(this, ctx));
		ctx.get().setPacketHandled(true);
	}
}
