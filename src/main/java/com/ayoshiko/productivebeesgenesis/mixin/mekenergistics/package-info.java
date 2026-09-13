/**
 * MekEnergistics 兼容 Mixin 包
 * <br/>
 * 与 MekEnergistics 模组的兼容性修复 Mixin：
 * <ol>
 *   <li>{@code MekEnergisticsInstallerGuardMixin} — 守护 MekEnergistics 安装流程，
 *   防止对本模组自定义方块实体初始化时抛出 NPE</li>
 *   <li>{@code MekEnergisticsTargetResolverGuardMixin} — 守护 MekEnergistics 目标解析流程，
 *   防止解析本模组自定义方块实体时抛出 ClassCastException</li>
 * </ol>
 * <p>
 * 所有 Mixin 使用 {@link org.spongepowered.asm.mixin.Pseudo @Pseudo} 注解 +
 * {@code @Mixin(targets = "...")} 字符串目标，在 MekEnergistics 未加载时
 * 不会触发类加载，由 mixins.json 静态声明 +
 * 主 {@link com.ayoshiko.productivebeesgenesis.mixin.MixinConfigPlugin} 放行。
 */
@ParametersAreNonnullByDefault
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
package com.ayoshiko.productivebeesgenesis.mixin.mekenergistics;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;
