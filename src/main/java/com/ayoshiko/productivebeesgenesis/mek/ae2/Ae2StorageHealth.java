package com.ayoshiko.productivebeesgenesis.mek.ae2;

/**
 * AE2 存储操作健康度判定。
 * <br/>
 * 根据单次操作的纳秒耗时判断是否处于病态（如 EnderDrives WAL fsync），
 * 用于触发退避或降级扫描策略。
 */
final class Ae2StorageHealth {

	/** 病态操作耗时阈值（纳秒），2 毫秒 */
	static final long PATHOLOGICAL_OPERATION_NANOS = 2_000_000L; // 2ms

	private Ae2StorageHealth() {
	}

	/**
	 * 判断给定的操作耗时是否构成病态。
	 *
	 * @param operationNanos 单次存储操作的纳秒耗时
	 * @return 超过阈值返回 true，表示应进入退避或降级路径
	 */
	static boolean isPathological(long operationNanos) {
		return operationNanos > PATHOLOGICAL_OPERATION_NANOS;
	}
}
