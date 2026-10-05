package com.chayewuu.hyperheartrate.api;

import com.chayewuu.hyperheartrate.device.BleDevice;

/**
 * 心率变化事件。
 * <p>
 * 心率值更新时产生的事件记录，供事件总线或外部监听器使用。
 * </p>
 *
 * <h2>何时触发</h2>
 * <p>
 * 当 BLE 心率设备上报新的心率测量值时，由 {@link com.chayewuu.hyperheartrate.heart.HeartRateManager#updateHeartRate(int)}
 * 触发。每次心率采样都会产生一个事件实例。
 * </p>
 *
 * <h2>携带数据</h2>
 * <ul>
 *   <li>{@code heartRate}：最新心率值（BPM）</li>
 *   <li>{@code timestamp}：采样时间戳（毫秒，{@code System.currentTimeMillis()}）</li>
 *   <li>{@code device}：产生该数据的 BLE 设备，未连接时可能为 {@code null}</li>
 * </ul>
 *
 * <h2>如何监听</h2>
 * <p>
 * 通过 {@link com.chayewuu.hyperheartrate.heart.HeartRateListener#onHeartRateChanged(int, long)}
 * 接收回调，使用 {@link com.chayewuu.hyperheartrate.api.HeartRateAPI#addListener(HeartRateListener)}
 * 注册监听器：
 * </p>
 * <pre>{@code
 * HeartRateAPI.addListener(new HeartRateListener() {
 *     @Override
 *     public void onHeartRateChanged(int heartRate, long timestamp) {
 *         HeartRateChangedEvent event = new HeartRateChangedEvent(heartRate, timestamp, HeartRateAPI.getCurrentDevice());
 *         // 处理事件...
 *     }
 *     // 其他方法省略...
 * });
 * }</pre>
 *
 * @param heartRate 心率值（BPM）
 * @param timestamp 采样时间戳（毫秒）
 * @param device    产生数据的设备
 */
public record HeartRateChangedEvent(int heartRate, long timestamp, BleDevice device) {
}
