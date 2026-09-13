package com.ayoshiko.productivebeesgenesis.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：切换蜂箱饲养板转化开关数据包
 * <br/>
 * 仅携带 {@link BlockPos}，服务端收到后定位 {@link com.ayoshiko.productivebeesgenesis.apiary.TileEntityMekApiary}
 * 并调用 {@code toggleFeederConversion()} 切换该蜂箱的蜜蜂转化功能。
 * <p>
 * 转化功能允许蜂箱将饲养板中的物品/方块作为转化原料，将蜜蜂转化为其他类型。
 * 配置项 {@code apiaryItemConversionEnabled} / {@code apiaryBlockConversionEnabled} 控制全局开关，
 * 本开关为单蜂箱覆盖。
 *
 * @param pos 蜂箱方块坐标
 */
public record ToggleApiaryFeederConversionPayload(
		BlockPos pos
) {

	public ToggleApiaryFeederConversionPayload(FriendlyByteBuf buf) {
		this(buf.readBlockPos());
	}

	public static void encode(ToggleApiaryFeederConversionPayload msg, FriendlyByteBuf buf) {
		buf.writeBlockPos(msg.pos);
	}

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> ApiaryPayloadHandlers.handleToggleFeederConversion(this, ctx));
		ctx.get().setPacketHandled(true);
	}
}
