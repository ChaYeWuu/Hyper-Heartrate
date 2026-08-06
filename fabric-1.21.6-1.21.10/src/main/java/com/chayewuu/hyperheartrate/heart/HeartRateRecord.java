package com.chayewuu.hyperheartrate.heart;

/**
 * 心率历史记录数据类。
 * <p>
 * 不可变记录，保存某次心率采样的数值与时间戳。
 * 由 {@link HeartRateStorage} 在查询历史时返回。
 * </p>
 *
 * @param heartRate 心率值（BPM）
 * @param timestamp 采样时间戳（毫秒，{@code System.currentTimeMillis()}）
 */
public record HeartRateRecord(int heartRate, long timestamp) {
}
