package com.chayewuu.hyperheartrate.device;

/**
 * BLE 设备信息接口。
 * <p>
 * 表示扫描到的或已连接的 BLE 设备的元数据快照。实现应当是不可变的，
 * 同一设备多次回调返回的实例字段值可能因扫描刷新（如 RSSI、电量）而不同。
 * </p>
 *
 * <p>主要用途：</p>
 * <ul>
 *     <li>由 {@link BleScanner} 在扫描回调中向上层报告设备；</li>
 *     <li>由 {@link BleConnector} 在连接成功后回传给上层；</li>
 *     <li>作为 {@link HeartRateParser#canParse(BleDevice)} 的判定依据。</li>
 * </ul>
 */
public interface BleDevice {
    /**
     * 获取设备名称。
     *
     * @return 设备广播名称，若设备未广播名称则可能为空字符串
     */
    String getName();

    /**
     * 获取设备 MAC 地址。
     *
     * @return 形如 {@code "AA:BB:CC:DD:EE:FF"} 的 MAC 地址字符串
     */
    String getAddress();

    /**
     * 获取最近一次扫描的信号强度（RSSI），单位 dBm。
     *
     * @return 信号强度值，通常为负数（如 -60）
     */
    int getRssi();

    /**
     * 获取设备电量百分比。
     *
     * @return 电量值 0~100；若设备不支持电量上报则返回 {@code null}
     */
    Integer getBatteryLevel();

    /**
     * 获取设备类型。
     *
     * @return 设备类型枚举，未知类型返回 {@link DeviceType#UNKNOWN}
     */
    DeviceType getType();
}
