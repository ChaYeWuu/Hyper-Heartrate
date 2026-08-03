package com.chayewuu.xiaomiheartrate.device.windows.win32;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

/**
 * Win32 经典蓝牙 API 的 JNA 库接口。
 * <p>
 * 对应 {@code bthprops.cpl}，提供蓝牙 radio 枚举与设备发现能力。
 * 本接口既可用于经典蓝牙设备发现，也能枚举系统中已配对/记忆的 BLE 设备
 * （{@code BluetoothFindFirstDevice} 会返回所有已配对的蓝牙设备，包括 BLE）。
 * </p>
 *
 * <p>实时 BLE 广播扫描（发现未配对的新 BLE 设备）需要 WinRT
 * {@code BluetoothLEAdvertisementWatcher}，由 {@link BluetoothLELibrary} 处理。</p>
 *
 * <p>使用方式：{@code BluetoothLibrary.INSTANCE.BluetoothFindFirstDevice(...)}</p>
 */
public interface BluetoothLibrary extends Library {
    /** 单例实例，由 JNA 在首次访问时加载 {@code bthprops.cpl} */
    BluetoothLibrary INSTANCE = Native.load("bthprops.cpl", BluetoothLibrary.class);

    /**
     * 开始设备枚举，返回第一个设备并打开枚举句柄。
     *
     * @param pbtsp 搜索参数
     * @param pbtdi 接收第一个设备信息（调用前需设置 {@code dwSize}）
     * @return 枚举句柄；返回 {@code null} 表示失败或无设备
     */
    Pointer BluetoothFindFirstDevice(BLUETOOTH_DEVICE_SEARCH_PARAMS pbtsp, BLUETOOTH_DEVICE_INFO pbtdi);

    /**
     * 枚举下一个设备。
     *
     * @param hFind 枚举句柄
     * @param pbtdi 接收设备信息
     * @return {@code true} 表示成功获取到下一个设备
     */
    boolean BluetoothFindNextDevice(Pointer hFind, BLUETOOTH_DEVICE_INFO pbtdi);

    /**
     * 关闭设备枚举句柄。
     *
     * @param hFind 枚举句柄
     * @return {@code true} 表示成功关闭
     */
    boolean BluetoothFindDeviceClose(Pointer hFind);

    /**
     * 开始 radio 枚举，返回第一个 radio 句柄并打开枚举句柄。
     *
     * @param pbtfrp 搜索参数
     * @param phRadio 接收第一个 radio 句柄
     * @return 枚举句柄；返回 {@code null} 表示失败或无 radio
     */
    Pointer BluetoothFindFirstRadio(BLUETOOTH_FIND_RADIO_PARAMS pbtfrp, PointerByReference phRadio);

    /**
     * 枚举下一个 radio。
     *
     * @param hFind   枚举句柄
     * @param phRadio 接收 radio 句柄
     * @return {@code true} 表示成功
     */
    boolean BluetoothFindNextRadio(Pointer hFind, PointerByReference phRadio);

    /**
     * 关闭 radio 枚举句柄。
     *
     * @param hFind 枚举句柄
     * @return {@code true} 表示成功
     */
    boolean BluetoothFindRadioClose(Pointer hFind);

    /**
     * 获取指定 radio 的详细信息。
     *
     * @param hRadio     radio 句柄
     * @param pRadioInfo 接收 radio 信息（调用前需设置 {@code dwSize}）
     * @return {@code ERROR_SUCCESS}（0）表示成功
     */
    int BluetoothGetRadioInfo(Pointer hRadio, BLUETOOTH_RADIO_INFO pRadioInfo);
}
