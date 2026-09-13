package com.ayoshiko.productivebeesgenesis.network;

import com.ayoshiko.productivebeesgenesis.mek.ae2.Ae2TagFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：同步 per-tile AE2 输入标签过滤器（白名单 + 黑名单表达式）
 * <br/>
 * 由服务端在玩家打开配置 GUI 或过滤器变更后推送，客户端收到后通过
 * {@code productivebeesgenesis$getAe2TagFilter()} 获取标签过滤器并调用
 * {@link Ae2TagFilter#apply(String, String)} 同步本地副本。
 * <p>
 * 与 {@link SyncAeInputFilterEntriesPayload} 一样，使用 {@link DistExecutor#unsafeRunWhenOn}
 * 在客户端线程中执行，避免在服务端引用 {@link Minecraft} 类导致类加载异常。
 *
 * @param pos       方块坐标
 * @param whitelist 白名单标签表达式（可为空字符串表示不限制）
 * @param blacklist 黑名单标签表达式（可为空字符串表示不排除）
 */
public record SyncAeInputTagFilterPayload(
		BlockPos pos,
		String whitelist,
		String blacklist
) {

	public SyncAeInputTagFilterPayload(FriendlyByteBuf buf) {
		this(buf.readBlockPos(),
				buf.readUtf(Ae2TagFilter.MAX_EXPRESSION_LENGTH),
				buf.readUtf(Ae2TagFilter.MAX_EXPRESSION_LENGTH));
	}

	public static void encode(SyncAeInputTagFilterPayload msg, FriendlyByteBuf buf) {
		buf.writeBlockPos(msg.pos);
		buf.writeUtf(msg.whitelist, Ae2TagFilter.MAX_EXPRESSION_LENGTH);
		buf.writeUtf(msg.blacklist, Ae2TagFilter.MAX_EXPRESSION_LENGTH);
	}

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
			Level level = Minecraft.getInstance().level;
			if (level != null) {
				Ae2TagFilterPayloadHandlers.handleSyncAeInputTagFilter(this, level);
			}
		}));
		ctx.get().setPacketHandled(true);
	}
}
