package com.chayewuu.xiaomiheartrate.device.windows;

import com.chayewuu.xiaomiheartrate.device.BleDevice;
import com.chayewuu.xiaomiheartrate.device.DeviceType;

/**
 * Windows 平台 BLE 设备信息实现。
 * <p>
 * 不可变实现，由 {@link WindowsBleScanner} 在扫描到设备时构造，
 * 通过 {@link com.chayewuu.xiaomiheartrate.device.ScanCallback} 上报给上层。
 * </p>
 *
 * <p>设备类型由 {@link com.chayewuu.xiaomiheartrate.device.DeviceFilter} 根据设备名推断。</p>
 */
public class WindowsBleDevice implements BleDevice {
    /** 设备广播名称 */
    private final String name;
    /** MAC 地址（形如 {@code AA:BB:CC:DD:EE:FF}） */
    private final String address;
    /** 信号强度（dBm） */
    private final int rssi;
    /** 电量百分比（0-100），未知为 {@code null} */
    private final Integer batteryLevel;
    /** 设备类型 */
    private final DeviceType type;

    /**
     * 构造设备信息。
     *
     * @param name          设备名称
     * @param address       MAC 地址
     * @param rssi          信号强度
     * @param batteryLevel  电量百分比，未知为 {@code null}
     * @param type          设备类型
     */
    public WindowsBleDevice(String name, String address, int rssi, Integer batteryLevel, DeviceType type) {
        this.name = name == null ? "" : name;
        this.address = address == null ? "" : address;
        this.rssi = rssi;
        this.batteryLevel = batteryLevel;
        this.type = type == null ? DeviceType.UNKNOWN : type;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getAddress() {
        return address;
    }

    @Override
    public int getRssi() {
        return rssi;
    }

    @Override
    public Integer getBatteryLevel() {
        return batteryLevel;
    }

    @Override
    public DeviceType getType() {
        return type;
    }

    @Override
    public String toString() {
        return "WindowsBleDevice{name='" + name + "', address='" + address
                + "', rssi=" + rssi + ", battery=" + batteryLevel + ", type=" + type + '}';
    }
}
