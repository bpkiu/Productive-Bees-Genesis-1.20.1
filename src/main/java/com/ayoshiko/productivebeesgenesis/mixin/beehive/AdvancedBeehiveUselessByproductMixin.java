package com.ayoshiko.productivebeesgenesis.mixin.beehive;

import com.ayoshiko.productivebeesgenesis.util.UselessByproductUpgradeHelper;
import cy.jdkdigital.productivebees.common.block.entity.AdvancedBeehiveBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies useless-byproduct filtering to Productive Bees advanced hives.
 * <p>
 * Note: The @Redirect for getBeeProduce was removed because in PB 1.20.1 the call
 * is inside a lambda (lambda$beeReleasePostAction$7), not directly in beeReleasePostAction.
 * The byproduct filtering feature is disabled until a compatible injection strategy is found.
 */
@Mixin(value = AdvancedBeehiveBlockEntity.class, remap = false)
public abstract class AdvancedBeehiveUselessByproductMixin {

	@Inject(method = "beeReleasePostAction", at = @At("TAIL"))
	private void productivebeesgenesis$restoreHoneyLevel(Level level, net.minecraft.world.entity.animal.Bee bee, BlockState state,
			BeehiveBlockEntity.BeeReleaseStatus releaseStatus, CallbackInfo ci) {
		AdvancedBeehiveBlockEntity blockEntity = (AdvancedBeehiveBlockEntity) (Object) this;
		if (releaseStatus != BeehiveBlockEntity.BeeReleaseStatus.HONEY_DELIVERED
				|| !UselessByproductUpgradeHelper.hasUpgrade(blockEntity)
				|| !state.hasProperty(BeehiveBlock.HONEY_LEVEL)) {
			return;
		}
		BlockPos pos = blockEntity.getBlockPos();
		BlockState current = level.getBlockState(pos);
		if (current.hasProperty(BeehiveBlock.HONEY_LEVEL)) {
			int originalLevel = state.getValue(BeehiveBlock.HONEY_LEVEL);
			if (current.getValue(BeehiveBlock.HONEY_LEVEL) != originalLevel) {
				level.setBlockAndUpdate(pos, current.setValue(BeehiveBlock.HONEY_LEVEL, originalLevel));
			}
		}
	}
}
