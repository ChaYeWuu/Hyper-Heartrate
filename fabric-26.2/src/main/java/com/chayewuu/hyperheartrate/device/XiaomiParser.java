package com.chayewuu.hyperheartrate.device;

/**
 * 小米手环/手表心率解析器。
 * <p>
 * 小米设备（Mi Band / Mi Watch 系列）大多兼容标准 GATT Heart Rate Service（0x180D），
 * 心率数据通过标准 0x2A37 特征值通知上报。本解析器优先复用标准解析逻辑，
 * 并为后续可能的小米私有协议扩展预留扩展点。
 * </p>
 *
 * <p><b>路由优先级：</b>高于 {@link StandardGattHeartRateParser}，
 * 当设备类型为 {@link DeviceType#XIAOMI_BAND} 或 {@link DeviceType#XIAOMI_WATCH} 时命中。</p>
 *
 * <p><b>线程安全：</b>解析器无状态，内部持有的 {@link StandardGattHeartRateParser}
 * 同样无状态，可被多线程并发调用。</p>
 */
public class XiaomiParser implements HeartRateParser {
    /** 标准 GATT 解析器（无状态，可安全共享） */
    private final StandardGattHeartRateParser fallback = new StandardGattHeartRateParser();

    @Override
    public int parseHeartRate(byte[] data) {
        // 小米设备大多兼容标准 GATT 心率服务，直接复用标准解析逻辑
        // 若后续需要支持小米私有协议，可在此处根据数据特征分支处理
        return fallback.parseHeartRate(data);
    }

    @Override
    public boolean canParse(BleDevice device) {
        if (device == null) {
            return false;
        }
        return device.getType() == DeviceType.XIAOMI_BAND
                || device.getType() == DeviceType.XIAOMI_WATCH;
    }
}
