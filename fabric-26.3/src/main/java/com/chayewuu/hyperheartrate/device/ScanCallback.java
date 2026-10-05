package com.chayewuu.hyperheartrate.device;

/**
 * BLE 扫描结果回调。
 * <p>
 * 由 {@link BleScanner#startScan(ScanCallback)} 注册，每扫描到一个新设备或
 * 已知设备的 RSSI 更新时触发。
 * </p>
 *
 * <p>该接口为函数式接口，可使用 Lambda 表达式实现。</p>
 */
@FunctionalInterface
public interface ScanCallback {
    /**
     * 扫描到设备时调用。
     *
     * @param device 扫描到的设备信息
     */
    void onDeviceFound(BleDevice device);
}
