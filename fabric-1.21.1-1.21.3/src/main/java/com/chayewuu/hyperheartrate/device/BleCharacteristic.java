package com.chayewuu.hyperheartrate.device;

/**
 * BLE 特征值（Characteristic）抽象接口。
 * <p>
 * 表示 GATT 服务下的一个特征值，封装对其的读、写、订阅（启用通知）操作。
 * 实现类需要桥接底层平台（如 Windows Bluetooth LE API / JNA）的具体调用。
 * </p>
 *
 * <p>典型用法：连接成功后由 {@link BleConnector#discoverServices()} 返回特征值列表，
 * 上层根据 UUID 选择目标特征值（如心率服务 0x2A37），并 {@link #enableNotifications(NotificationCallback)}
 * 订阅实时心率通知。</p>
 */
public interface BleCharacteristic {
    /**
     * 获取特征值 UUID。
     *
     * @return 标准 128 位 UUID 字符串，如 {@code "00002a37-0000-1000-8000-00805f9b34fb"}
     */
    String getUuid();

    /**
     * 同步读取特征值当前数据。
     * <p>该方法应阻塞至读取完成或超时。</p>
     *
     * @return 读取到的原始字节数据
     */
    byte[] readValue();

    /**
     * 同步写入数据到特征值。
     *
     * @param data 待写入的字节数据
     */
    void writeValue(byte[] data);

    /**
     * 启用该特征值的通知（订阅）。
     * <p>启用后，设备下发数据时将触发 {@code callback}。</p>
     *
     * @param callback 通知回调
     */
    void enableNotifications(NotificationCallback callback);
}
