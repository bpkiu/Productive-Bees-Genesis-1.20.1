package com.ayoshiko.productivebeesgenesis.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
	 * 客户端 → 服务端：切换指定离心机的 per-tile 电力熔炼炉配方兼容开关
	 * <br/>
	 * 携带 {@link BlockPos}，服务端校验玩家身份、8 格交互距离与全局总开关后，
	 * 调用状态持有者的 {@code toggleSmeltingCompatEnabled()} 执行切换并持久化。
	 *
	 * @param pos 方块坐标
	 */
public record ToggleSmeltingCompatPayload(
		BlockPos pos
) {

	public ToggleSmeltingCompatPayload(FriendlyByteBuf buf) {
		this(buf.readBlockPos());
	}

	public static void encode(ToggleSmeltingCompatPayload msg, FriendlyByteBuf buf) {
		buf.writeBlockPos(msg.pos);
	}

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> SmeltingCompatPayloadHandler.handle(this, ctx));
		ctx.get().setPacketHandled(true);
	}
}
