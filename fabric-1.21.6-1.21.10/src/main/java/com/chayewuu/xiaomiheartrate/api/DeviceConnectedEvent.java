package com.chayewuu.xiaomiheartrate.api;

import com.chayewuu.xiaomiheartrate.device.BleDevice;

/**
 * 设备连接成功事件。
 *
 * <h2>何时触发</h2>
 * <p>
 * 当 BLE 设备由断开状态切换到已连接状态时，由
 * {@link com.chayewuu.xiaomiheartrate.heart.HeartRateManager#setDevice(BleDevice, boolean)}
 * 在 {@code connected=true} 时触发。
 * </p>
 *
 * <h2>携带数据</h2>
 * <ul>
 *   <li>{@code device}：已连接的 BLE 设备，包含名称、地址、信号强度等信息</li>
 * </ul>
 *
 * <h2>如何监听</h2>
 * <p>
 * 通过 {@link com.chayewuu.xiaomiheartrate.heart.HeartRateListener#onDeviceConnected(BleDevice)}
 * 接收回调，使用 {@link com.chayewuu.xiaomiheartrate.api.HeartRateAPI#addListener(HeartRateListener)}
 * 注册监听器：
 * </p>
 * <pre>{@code
 * HeartRateAPI.addListener(new HeartRateListener() {
 *     @Override
 *     public void onDeviceConnected(BleDevice device) {
 *         DeviceConnectedEvent event = new DeviceConnectedEvent(device);
 *         // 处理事件，例如刷新 UI 提示用户...
 *     }
 *     // 其他方法省略...
 * });
 * }</pre>
 *
 * @param device 已连接的设备
 */
public record DeviceConnectedEvent(BleDevice device) {
}
