/**
 * Building Gadgets 兼容 Mixin 包
 * <br/>
 * 与 Building Gadgets 模组的兼容性修复 Mixin：
 * <ol>
 *   <li>{@code RenderBlockBeLoadFixMixin} — 修复 Building Gadgets 假方块加载
 *   本模组自定义方块实体时的 ClassCastException / NPE 崩溃</li>
 * </ol>
 * <p>
 * 所有 Mixin 使用 {@link org.spongepowered.asm.mixin.Pseudo @Pseudo} 注解 +
 * {@code @Mixin(targets = "...")} 字符串目标，在 Building Gadgets 未加载时
 * 不会触发类加载，由 mixins.json 静态声明 +
 * 主 {@link com.ayoshiko.productivebeesgenesis.mixin.MixinConfigPlugin} 放行。
 */
@ParametersAreNonnullByDefault
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
package com.ayoshiko.productivebeesgenesis.mixin.buildinggadgets;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;
