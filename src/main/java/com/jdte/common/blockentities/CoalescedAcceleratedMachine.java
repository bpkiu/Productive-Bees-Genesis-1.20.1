package com.jdte.common.blockentities;
// Stub for compile-only (real jdte jar is built with Java 21 class v65, incompatible with Java 17 MC 1.20.1)
// In production 1.20.1 this interface never loads; mixin plugin disables these mixins.
// 方法签名与 jdte-0.5.9-Fix.jar 中真实接口一致（javap 核实），
// 缺失会使 Jdte*CoalescedMixin 的 @Override 报"方法不会覆盖或实现超类型的方法"。
public interface CoalescedAcceleratedMachine {

	void accumulateAcceleratedTicks(int ticks);

	void flushAcceleratedTicks();
}
