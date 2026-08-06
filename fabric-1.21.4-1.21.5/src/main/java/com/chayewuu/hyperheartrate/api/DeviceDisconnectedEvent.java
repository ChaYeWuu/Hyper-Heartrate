package com.chayewuu.hyperheartrate.api;

import com.chayewuu.hyperheartrate.device.BleDevice;

/**
 * 设备断开事件。
 *
 * <h2>何时触发</h2>
 * <p>
 * 当 BLE 设备由已连接状态切换到断开状态时，由
 * {@link com.chayewuu.hyperheartrate.heart.HeartRateManager#setDevice(BleDevice, boolean)}
 * 在 {@code connected=false} 时触发。常见场景包括设备主动断开、连接超时、信号丢失等。
 * </p>
 *
 * <h2>携带数据</h2>
 * <ul>
 *   <li>{@code device}：断开的 BLE 设备，可用于在 UI 上回显设备名称</li>
 *   <li>{@code reason}：断开原因描述，便于日志记录与用户提示，可能为 {@code null}</li>
 * </ul>
 *
 * <h2>如何监听</h2>
 * <p>
 * 通过 {@link com.chayewuu.hyperheartrate.heart.HeartRateListener#onDeviceDisconnected(BleDevice)}
 * 接收回调，使用 {@link com.chayewuu.hyperheartrate.api.HeartRateAPI#addListener(HeartRateListener)}
 * 注册监听器：
 * </p>
 * <pre>{@code
 * HeartRateAPI.addListener(new HeartRateListener() {
 *     @Override
 *     public void onDeviceDisconnected(BleDevice device) {
 *         DeviceDisconnectedEvent event = new DeviceDisconnectedEvent(device, "连接丢失");
 *         // 处理事件，例如提示用户重新连接...
 *     }
 *     // 其他方法省略...
 * });
 * }</pre>
 *
 * @param device 断开的设备
 * @param reason 断开原因描述，可能为 {@code null}
 */
public record DeviceDisconnectedEvent(BleDevice device, String reason) {
}
