package com.chayewuu.xiaomiheartrate.device;

/**
 * BLE 连接状态回调。
 * <p>
 * 由 {@link BleConnector#connect(BleDevice, ConnectionCallback)} 注册，
 * 在连接成功、断开、出错时触发对应方法。
 * </p>
 *
 * <p>注意：回调方法可能在 BLE 后台线程被调用，实现方若需更新 UI 必须 marshal 回主线程。</p>
 */
public interface ConnectionCallback {
    /**
     * 连接成功时调用。
     *
     * @param device 已连接的设备
     */
    void onConnected(BleDevice device);

    /**
     * 连接断开时调用（含主动断开与意外掉线）。
     *
     * @param device 断开的设备
     */
    void onDisconnected(BleDevice device);

    /**
     * 连接过程中发生错误时调用。
     *
     * @param message 错误描述
     * @param cause   错误根因异常，可能为 {@code null}
     */
    void onError(String message, Throwable cause);
}
