package com.ayoshiko.productivebeesgenesis.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
	 * 客户端 → 服务端：切换指定方块实体的 per-tile 精确模式开关
	 * <br/>
	 * 携带 {@link BlockPos}，服务端校验玩家身份、8 格交互距离、
	 * 方块实体是否为 {@link com.ayoshiko.productivebeesgenesis.mek.ae2.IAe2InputHost} 后调用
	 * {@code Ae2InputFilter.togglePreciseMode()} 执行切换。
	 * <p>
	 * 精确模式开启时区分蜜脾和蜜脾块（同种 beeType 但物品类型不同），
	 * 关闭时同种 beeType 的蜜脾和蜜脾块一起拉取。
	 *
	 * @param pos 方块坐标
	 */
public record ToggleAeInputPreciseModePayload(
		BlockPos pos
) {

	public ToggleAeInputPreciseModePayload(FriendlyByteBuf buf) {
		this(buf.readBlockPos());
	}

	public static void encode(ToggleAeInputPreciseModePayload msg, FriendlyByteBuf buf) {
		buf.writeBlockPos(msg.pos);
	}

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> Ae2FilterPayloadHandlers.handleToggleAeInputPreciseMode(this, ctx));
		ctx.get().setPacketHandled(true);
	}
}
