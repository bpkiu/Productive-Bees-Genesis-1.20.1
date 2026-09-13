package com.ayoshiko.productivebeesgenesis.network;

import com.ayoshiko.productivebeesgenesis.mek.ae2.Ae2TagFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：设置 per-tile AE2 输入标签过滤器（白名单 + 黑名单表达式）
 * <br/>
 * 携带 {@link BlockPos} 与白名单/黑名单标签表达式字符串，
 * 服务端校验玩家身份、容器一致性、方块实体类型与 8 格交互距离后，
 * 通过 {@code productivebeesgenesis$getAe2TagFilter()} 获取标签过滤器并调用
 * {@link Ae2TagFilter#apply(String, String)} 应用新表达式。
 * <p>
 * 字符串长度上限与 {@link Ae2TagFilter#MAX_EXPRESSION_LENGTH} 对齐，
 * 防止恶意客户端发送超长表达式导致解析开销过大。
 *
 * @param pos       方块坐标
 * @param whitelist 白名单标签表达式（可为空字符串表示不限制）
 * @param blacklist 黑名单标签表达式（可为空字符串表示不排除）
 */
public record SetAeInputTagFilterPayload(
		BlockPos pos,
		String whitelist,
		String blacklist
) {

	public SetAeInputTagFilterPayload(FriendlyByteBuf buf) {
		this(buf.readBlockPos(),
				buf.readUtf(Ae2TagFilter.MAX_EXPRESSION_LENGTH),
				buf.readUtf(Ae2TagFilter.MAX_EXPRESSION_LENGTH));
	}

	public static void encode(SetAeInputTagFilterPayload msg, FriendlyByteBuf buf) {
		buf.writeBlockPos(msg.pos);
		buf.writeUtf(msg.whitelist, Ae2TagFilter.MAX_EXPRESSION_LENGTH);
		buf.writeUtf(msg.blacklist, Ae2TagFilter.MAX_EXPRESSION_LENGTH);
	}

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> Ae2TagFilterPayloadHandlers.handleSetAeInputTagFilter(this, ctx));
		ctx.get().setPacketHandled(true);
	}
}
