package com.ayoshiko.productivebeesgenesis.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
	 * Client to server: interact with one virtual output slot of the AE2 input
	 * config GUI (AE2LT overloaded-interface style third row).
	 * <br/>
	 * Left click with empty cursor extracts from the ME network into the cursor;
	 * right click extracts half. Left click with a carried stack inserts it into
	 * the network; right click inserts one. Shift-click extracts into the
	 * player inventory (quick move).
	 *
	 * @param pos       centrifuge block position
	 * @param slotIndex global filter slot index
	 * @param shift     shift held (extract into inventory)
	 * @param rightClick right mouse button (half extract / single insert)
	 */
public record AeInputOutputSlotPayload(
		BlockPos pos,
		int slotIndex,
		boolean shift,
		boolean rightClick
) {

	public AeInputOutputSlotPayload(FriendlyByteBuf buf) {
		this(buf.readBlockPos(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
	}

	public static void encode(AeInputOutputSlotPayload msg, FriendlyByteBuf buf) {
		buf.writeBlockPos(msg.pos);
		buf.writeVarInt(msg.slotIndex);
		buf.writeBoolean(msg.shift);
		buf.writeBoolean(msg.rightClick);
	}

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> Ae2PayloadHandlers.handleAeInputOutputSlot(this, ctx));
		ctx.get().setPacketHandled(true);
	}
}
