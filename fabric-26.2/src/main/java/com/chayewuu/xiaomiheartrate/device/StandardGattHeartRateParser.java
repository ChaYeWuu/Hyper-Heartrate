package com.chayewuu.xiaomiheartrate.device;

/**
 * 标准 GATT Heart Rate Service（0x180D）心率解析器。
 * <p>
 * 解析蓝牙标准 Heart Rate Measurement 特征值（UUID 0x2A37）通知上报的字节数据，
 * 适用于任何兼容标准 GATT 心率服务的设备（包括大部分小米/Redmi 设备）。
 * </p>
 *
 * <p><b>Heart Rate Measurement 数据格式（蓝牙规范）：</b></p>
 * <pre>
 * Byte 0 (Flags):
 *   bit 0   : 心率值格式（0=uint8, 1=uint16）
 *   bit 1-2 : 传感器接触状态
 *   bit 3   : 能量消耗状态（0=不含, 1=含）
 *   bit 4   : RR-Interval 存在与否（0=不含, 1=含）
 *   bit 5-7 : 保留
 * Byte 1+  : 心率值（根据 bit 0，uint8 或 uint16，小端序）
 * 后续     : 可选的能量消耗（uint16）与 RR-Interval 数据
 * </pre>
 *
 * <p><b>线程安全：</b>解析器无状态，可被多线程并发调用。</p>
 */
public class StandardGattHeartRateParser implements HeartRateParser {
    /** Heart Rate Service UUID（0x180D） */
    private static final String HR_SERVICE_UUID = "0000180d-0000-1000-8000-00805f9b34fb";

    /** Heart Rate Measurement 特征值 UUID（0x2A37） */
    private static final String HR_MEASUREMENT_UUID = "00002a37-0000-1000-8000-00805f9b34fb";

    /** 标志位 bit 0 掩码：心率值格式（0=uint8, 1=uint16） */
    private static final int FLAG_HEART_RATE_FORMAT_UINT16 = 0x01;

    /**
     * 获取 Heart Rate Service UUID（0x180D）。
     *
     * @return 标准 128 位 UUID 字符串
     */
    public static String getHrServiceUuid() {
        return HR_SERVICE_UUID;
    }

    /**
     * 获取 Heart Rate Measurement 特征值 UUID（0x2A37）。
     *
     * @return 标准 128 位 UUID 字符串
     */
    public static String getHrMeasurementUuid() {
        return HR_MEASUREMENT_UUID;
    }

    @Override
    public int parseHeartRate(byte[] data) {
        if (data == null || data.length < 2) {
            return 0;
        }
        int flags = data[0] & 0xFF;
        boolean isUInt16 = (flags & FLAG_HEART_RATE_FORMAT_UINT16) != 0;
        if (isUInt16) {
            if (data.length < 3) {
                return 0;
            }
            // 小端序 uint16
            return ((data[2] & 0xFF) << 8) | (data[1] & 0xFF);
        } else {
            return data[1] & 0xFF;
        }
    }

    @Override
    public boolean canParse(BleDevice device) {
        // 任何设备都可能支持标准 GATT 心率服务，作为兜底解析器（优先级最低）
        return device != null;
    }
}
