package com.chayewuu.hyperheartrate.heart;

import com.chayewuu.hyperheartrate.device.BleDevice;

/**
 * 心率监听器接口。
 * <p>
 * 由关注心率与设备状态变化的上层组件（GUI、HTTP Server、外部 Mod）实现，
 * 通过 {@link HeartRateManager#addListener(HeartRateListener)} 注册。
 * </p>
 *
 * <p>注意：回调方法可能在 BLE 后台线程被触发，实现方更新 UI 时需自行 marshal 回主线程。</p>
 */
public interface HeartRateListener {
    /**
     * 心率值变化时调用。
     *
     * @param heartRate 最新心率值（BPM）
     * @param timestamp 采样时间戳（毫秒）
     */
    void onHeartRateChanged(int heartRate, long timestamp);

    /**
     * 设备连接成功时调用。
     *
     * @param device 已连接的设备
     */
    void onDeviceConnected(BleDevice device);

    /**
     * 设备断开时调用。
     *
     * @param device 断开的设备
     */
    void onDeviceDisconnected(BleDevice device);
}
