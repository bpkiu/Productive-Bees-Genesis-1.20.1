package com.ayoshiko.productivebeesgenesis.mek.ae2;

import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;

/**
 * AE2 输出推送上下文（不可变值载体，Java 17 record）。
 * <br/>
 * 将单次推送所需的所有依赖与状态打包为不可变记录，避免长参数列表与方法间反复传参。
 * 宿主、状态持有者、缓冲区、ME 存储、退避注册表等均由调用方在构造时注入。
 *
 * @param host           输出宿主基础接口
 * @param holder         输出状态持有者
 * @param buffers        推送缓冲区
 * @param meStorage      AE2 ME 存储
 * @param keyBackoff     按 AEItemKey 维度的退避注册表
 * @param itemBackoff    每台机器的物品推送退避
 * @param gameTick       当前游戏刻
 * @param nowNanos       当前 System.nanoTime() 值
 * @param scanStart      本轮扫描起始索引
 * @param flatSlotCount  扁平化后的总槽位数
 */
final record Ae2OutputPushContext(
		IAe2OutputHostBase host,
		Ae2OutputStateHolder holder,
		Ae2PushBuffers buffers,
		MEStorage meStorage,
		Ae2KeyBackoffRegistry<AEItemKey> keyBackoff,
		Ae2PushBackoff itemBackoff,
		long gameTick,
		long nowNanos,
		int scanStart,
		int flatSlotCount
) {
}
