package com.chayewuu.hyperheartrate.device;

/**
 * 手动输入的 BLE 设备信息实现。
 * <p>
 * 当用户在设备选择界面手动输入 MAC 地址时构造此实例，
 * 用于发起连接。设备名称默认为 "Manual Device"，
 * 设备类型由 {@link DeviceFilter} 根据名称推断（手动输入时为 UNKNOWN）。
 * </p>
 *
 * <p>该类为不可变实现。</p>
 */
public class ManualBleDevice implements BleDevice {
    /** 设备名称 */
    private final String name;
    /** MAC 地址 */
    private final String address;
    /** 设备类型 */
    private final DeviceType type;

    /**
     * 构造手动设备。
     *
     * @param address MAC 地址（形如 {@code AA:BB:CC:DD:EE:FF}）
     */
    public ManualBleDevice(String address) {
        this("Manual Device", address, DeviceType.UNKNOWN);
    }

    /**
     * 构造手动设备（指定名称与类型）。
     *
     * @param name    设备名称
     * @param address MAC 地址
     * @param type    设备类型
     */
    public ManualBleDevice(String name, String address, DeviceType type) {
        this.name = name == null ? "Manual Device" : name;
        this.address = address == null ? "" : address;
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
        return 0;
    }

    @Override
    public Integer getBatteryLevel() {
        return null;
    }

    @Override
    public DeviceType getType() {
        return type;
    }

    @Override
    public String toString() {
        return "ManualBleDevice{name='" + name + "', address='" + address + "', type=" + type + '}';
    }
}
