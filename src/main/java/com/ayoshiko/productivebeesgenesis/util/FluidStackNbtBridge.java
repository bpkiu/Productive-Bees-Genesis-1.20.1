package com.ayoshiko.productivebeesgenesis.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;

/**
 * Forge 版本兼容桥 — FluidStack NBT 序列化/反序列化。
 *
 * <p>背景：不同 Forge 版本对 FluidStack 的 NBT API 命名差异较大：
 *   <ul>
 *     <li>1.16:   writeToNbt / loadFluidStackFromNbt（实例+静态）</li>
 *     <li>1.18+:  save / load 或 FluidStack(CompoundTag) 构造器</li>
 *     <li>1.20.1: 仅有 CompoundTag 返回的 write 形式名称与 PB 旧实现不匹配。</li>
 *   </ul>
 * 本工具类手写 NBT 三字段(Fluid/Amount/Tag)，100% 兼容 Forge FluidStack 的
 * 标准 NBT 格式，不再依赖任何 FluidStack XxxNbt 公开方法名。
 *
 * <p>输出的 NBT 格式（与 Forge 默认完全一致）：
 * <pre>
 *   {
 *     "Fluid": "minecraft:water"       , // ResourceLocation 字符串（Forge 标准：Fluid = 注册表名）
 *     "Amount": 1000                   , // int (Forge 旧版) 或兼容 long（读取时 int/long 都能读）
 *     "tag": {...}                       // 可选 CompoundTag，流体附加数据
 *   }
 * </pre>
 */
public final class FluidStackNbtBridge {

    private static final String KEY_FLUID = "Fluid";
    private static final String KEY_AMOUNT = "Amount";
    private static final String KEY_TAG = "tag";

    private FluidStackNbtBridge() {}

    /**
     * 把一个非空 FluidStack 写入到新建的 CompoundTag 并返回。
     *
     * @param stack 流体栈，允许空（空则返回空的 CompoundTag）
     * @return 序列化后的 CompoundTag（永远非 null）
     */
    @NotNull
    public static CompoundTag write(@NotNull FluidStack stack) {
        CompoundTag tag = new CompoundTag();
        if (stack.isEmpty()) return tag;
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(stack.getFluid());
        if (fluidId == null) return tag;
        tag.putString(KEY_FLUID, fluidId.toString());
        tag.putInt(KEY_AMOUNT, stack.getAmount());
        if (stack.getTag() != null) {
            tag.put(KEY_TAG, stack.getTag().copy());
        }
        return tag;
    }

    /**
     * 把 FluidStack 写入到给定的父 CompoundTag 指定 key 下（如果为空则不写）。
     */
    public static void writeTo(@NotNull CompoundTag parent, @NotNull String key, @NotNull FluidStack stack) {
        if (stack.isEmpty()) return;
        parent.put(key, write(stack));
    }

    /**
     * 从 CompoundTag 读回 FluidStack。
     *
     * @param tag 源 CompoundTag，允许 null（返回 FluidStack.EMPTY）
     * @return 解析后的流体栈（解析失败或不存在返回 FluidStack.EMPTY）
     */
    @NotNull
    public static FluidStack read(@NotNull CompoundTag tag) {
        if (!tag.contains(KEY_FLUID, net.minecraft.nbt.Tag.TAG_STRING)) return FluidStack.EMPTY;
        ResourceLocation fluidId = ResourceLocation.tryParse(tag.getString(KEY_FLUID));
        if (fluidId == null || !BuiltInRegistries.FLUID.containsKey(fluidId)) return FluidStack.EMPTY;
        int amount = tag.contains(KEY_AMOUNT, net.minecraft.nbt.Tag.TAG_INT)
                ? tag.getInt(KEY_AMOUNT)
                : (tag.contains(KEY_AMOUNT, net.minecraft.nbt.Tag.TAG_LONG) ? (int) tag.getLong(KEY_AMOUNT) : 1);
        if (amount <= 0) amount = 1;
        FluidStack stack = new FluidStack(BuiltInRegistries.FLUID.get(fluidId), amount);
        if (tag.contains(KEY_TAG, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            stack.setTag(tag.getCompound(KEY_TAG));
        }
        return stack;
    }
}
