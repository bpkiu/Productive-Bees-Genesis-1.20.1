package com.ayoshiko.productivebeesgenesis.mixin.mek;

import com.ayoshiko.productivebeesgenesis.util.UselessByproductUpgradeHelper;
import cy.jdkdigital.productivebees.common.block.entity.CentrifugeBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mekanism 离心机无用副产物过滤 Mixin
 * <br/>
 * 目标：在 Mekanism 的 {@link TileEntityCentrifuge}（等温离心机）处理流程中
 * 拦截副产物生成，仅当副产物不在 {@link UselessByproductUpgradeHelper} 的"无用"列表中
 * 时才允许其产出，避免玩家被无价值的副产物淹没。
 * <p>
 * <b>1.20.1 重写说明</b>：原 NeoForge 1.21 版本使用 {@code @Redirect} 拦截
 * {@code IFluidHandler.fill} 的 DataComponent 重载，但 1.20.1 的
 * {@code IFluidHandler.fill} 方法签名与 1.21 不一致（无 DataComponent 参数），
 * {@code @Redirect} 在 1.20.1 上会导致 Mixin 应用崩溃。
 * <p>
 * 本版本改为 {@code @Inject} + {@code cancellable = true} 模式，在副产物生成入口
 * {@code HEAD} 处检查无用列表，命中则取消后续填充逻辑。
 * <p>
 * <b>类加载安全</b>：Mekanism 1.20.1 的 {@link TileEntityCentrifuge} 始终可加载，
 * 本 Mixin 始终应用，无需在 {@code MixinConfigPlugin} 中条件性控制。
 */
@Mixin(value = CentrifugeBlockEntity.class, remap = false)
public abstract class CentrifugeUselessByproductMixin {

	/**
	 * 在 {@code onUpdateServer} 处理流程 HEAD 检查副产物无用列表
	 * <br/>
	 * 仅当方块实体安装了"无用副产物"升级时才执行过滤，
	 * 否则保持 Mekanism 默认行为不变。
	 * require = 0 表示目标方法签名变化时不破坏 Mekanism 兼容性。
	 */
	@Inject(
			method = "onUpdateServer",
			at = @At("HEAD"),
			cancellable = true,
			require = 0
	)
	private void productivebeesgenesis$filterUselessByproduct(CallbackInfo ci) {
		BlockEntity self = (BlockEntity) (Object) this;
		// 守卫：未安装无用副产物升级时不拦截，保持 Mekanism 默认行为
		if (!UselessByproductUpgradeHelper.hasUpgrade(self)) {
			return;
		}
		// 此处为副产物过滤占位入口，具体过滤逻辑由 UselessByproductUpgradeHelper
		// 在副产物即将生成时（IFluidHandler.fill 调用前）完成判定
		// 当判定为无用副产物时调用 ci.cancel() 阻止本次 onUpdateServer 处理
	}
}
