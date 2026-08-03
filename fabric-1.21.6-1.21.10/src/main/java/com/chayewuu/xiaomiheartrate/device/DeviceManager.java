package com.chayewuu.xiaomiheartrate.device;

/**
 * 设备管理器接口。
 * <p>
 * 上层（GUI / 心率管理器）与 BLE 子系统之间的统一门面，封装扫描、连接、
 * 状态查询与自动重连等高层语义。实现类内部组合 {@link BleAdapter}、
 * {@link BleScanner}、{@link BleConnector} 完成实际工作。
 * </p>
 *
 * <p>该接口屏蔽底层平台差异，使上层逻辑不感知具体 BLE 后端实现。</p>
 */
public interface DeviceManager {
    /**
     * 启动设备扫描。
     *
     * @param callback 扫描结果回调
     */
    void startScan(ScanCallback callback);

    /**
     * 停止设备扫描。
     */
    void stopScan();

    /**
     * 连接指定设备。
     *
     * @param device   目标设备
     * @param callback 连接状态回调
     */
    void connect(BleDevice device, ConnectionCallback callback);

    /**
     * 断开当前连接。
     */
    void disconnect();

    /**
     * 获取当前已连接（或正在连接）的设备。
     *
     * @return 当前设备，无设备时返回 {@code null}
     */
    BleDevice getCurrentDevice();

    /**
     * 获取当前连接状态。
     *
     * @return 连接状态枚举
     */
    ConnectionState getConnectionState();

    /**
     * 开启或关闭自动重连。
     * <p>开启后，设备意外掉线时管理器将按内部策略尝试重连。</p>
     *
     * @param enabled 是否启用自动重连
     */
    void setAutoReconnect(boolean enabled);

    /**
     * 获取最近一次扫描的错误信息。
     * <p>供 GUI 显示扫描失败原因（如未安装 dotnet 运行时等）。</p>
     *
     * @return 错误信息，无错误返回 {@code null}
     */
    String getScanError();
}
