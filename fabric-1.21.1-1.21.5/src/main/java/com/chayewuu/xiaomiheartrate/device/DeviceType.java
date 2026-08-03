package com.chayewuu.xiaomiheartrate.device;

/**
 * BLE 心率设备类型枚举。
 * <p>
 * 用于标识扫描到的设备所属类型，以便选择对应的 {@link HeartRateParser} 解析心率数据。
 * </p>
 */
public enum DeviceType {
    /** 小米手环（如小米手环 8/9 等） */
    XIAOMI_BAND,
    /** 小米手表（如小米 Watch S 系列等） */
    XIAOMI_WATCH,
    /** Redmi Watch 系列 */
    REDMI_WATCH,
    /** 标准 GATT 心率服务（UUID 0x180D）设备 */
    STANDARD_GATT,
    /** 未知设备类型 */
    UNKNOWN
}
