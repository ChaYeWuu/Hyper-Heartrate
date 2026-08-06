package com.chayewuu.hyperheartrate.device.windows.win32;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid.GUID;
import com.chayewuu.hyperheartrate.device.windows.win32.SP_DEVICE_INTERFACE_DATA;
import com.chayewuu.hyperheartrate.device.windows.win32.SP_DEVICE_INTERFACE_DETAIL_DATA;

/**
 * Win32 SetupAPI 的 JNA 库接口（BLE 设备枚举子集）。
 * <p>
 * 对应 {@code setupapi.dll}，仅声明通过设备接口 GUID 枚举 BLE 设备并获取
 * 设备路径所需的最小 API 子集。用于 {@link com.chayewuu.hyperheartrate.device.windows.WindowsBleConnector}
 * 在 GATT 连接前通过 SetupAPI 流程获取设备句柄。
 * </p>
 *
 * <p><b>典型流程：</b></p>
 * <ol>
 *     <li>{@link #SetupDiGetClassDevsW} 获取设备信息集；</li>
 *     <li>循环 {@link #SetupDiEnumDeviceInterfaces} 枚举设备接口；</li>
 *     <li>{@link #SetupDiGetDeviceInterfaceDetailW} 获取设备路径；</li>
 *     <li>从设备路径中匹配目标 MAC 地址；</li>
 *     <li>用 {@link Kernel32Library#CreateFileW} 打开设备句柄。</li>
 * </ol>
 *
 * <p><b>GUID 常量：</b>{@link #GUID_BLUETOOTHLE_DEVICE_INTERFACE}
 * ({@code EBE9D6E4-3D36-4B78-A7F6-2F14F0B3E3D6}) 为系统预定义的
 * Bluetooth LE 设备接口类。</p>
 */
public interface SetupApiLibrary extends Library {
    /** 单例实例，加载 {@code setupapi} */
    SetupApiLibrary INSTANCE = Native.load("setupapi", SetupApiLibrary.class);

    /** 仅返回已存在设备，不触发新枚举 */
    int DIGCF_PRESENT = 0x00000002;
    /** 返回设备接口 */
    int DIGCF_DEVICEINTERFACE = 0x00000010;

    /**
     * Bluetooth LE 设备接口类 GUID：{@code EBE9D6E4-3D36-4B78-A7F6-2F14F0B3E3D6}。
     */
    GUID GUID_BLUETOOTHLE_DEVICE_INTERFACE = new GUID();

    /**
     * 获取设备信息集。
     *
     * @param ClassGuid       设备接口类 GUID（与 Flags 配合）
     * @param Enumerator      枚举器名称，可为 {@code null}
     * @param hwndParent      父窗口句柄，可为 {@code null}
     * @param Flags           控制标志（如 {@link #DIGCF_PRESENT} | {@link #DIGCF_DEVICEINTERFACE}）
     * @return 设备信息集句柄，失败返回 {@code null}（调用 {@code GetLastError} 获取错误）
     */
    Pointer SetupDiGetClassDevsW(GUID ClassGuid, String Enumerator, Pointer hwndParent, int Flags);

    /**
     * 枚举设备接口。
     *
     * @param DeviceInfoSet   设备信息集句柄
     * @param DeviceInfoData   设备信息元素，可为 {@code null}
     * @param InterfaceClassGuid 接口类 GUID
     * @param MemberIndex     成员索引（从 0 开始递增）
     * @param DeviceInterfaceData 接收接口数据（调用前需设置 cbSize）
     * @return {@code true} 表示成功枚举；{@code false} 表示无更多接口
     */
    boolean SetupDiEnumDeviceInterfaces(Pointer DeviceInfoSet,
                                        Pointer DeviceInfoData,
                                        GUID InterfaceClassGuid,
                                        int MemberIndex,
                                        SP_DEVICE_INTERFACE_DATA DeviceInterfaceData);

    /**
     * 获取设备接口详细信息（设备路径）。
     * <p>首次调用时 DeviceInterfaceDetailData 传 {@code null}、RequiredSize 传非空数组，
     * 可获取所需缓冲区大小；分配缓冲区后二次调用填充路径。</p>
     *
     * @param DeviceInfoSet          设备信息集句柄
     * @param DeviceInterfaceData    接口数据
     * @param DeviceInterfaceDetailData 详情数据缓冲区（首次可为 {@code null}）
     * @param DeviceInterfaceDetailDataSize 缓冲区大小（字节）
     * @param RequiredSize           接收所需大小
     * @param DeviceInfoData          接收设备信息，可为 {@code null}
     * @return {@code true} 表示成功；{@code false} 表示失败或缓冲区不足
     */
    boolean SetupDiGetDeviceInterfaceDetailW(Pointer DeviceInfoSet,
                                             SP_DEVICE_INTERFACE_DATA DeviceInterfaceData,
                                             SP_DEVICE_INTERFACE_DETAIL_DATA DeviceInterfaceDetailData,
                                             int DeviceInterfaceDetailDataSize,
                                             int[] RequiredSize,
                                             Pointer DeviceInfoData);

    /**
     * 销毁设备信息集，释放资源。
     *
     * @param DeviceInfoSet 设备信息集句柄
     * @return {@code true} 表示成功
     */
    boolean SetupDiDestroyDeviceInfoList(Pointer DeviceInfoSet);

    /** 静态初始化：填充 Bluetooth LE 设备接口类 GUID 字节序 */
    GUID GUID_BLUETOOTHLE_DEVICE_INTERFACE_INIT = initBluetoothLeGuid();

    /**
     * 初始化 Bluetooth LE 设备接口 GUID。
     * <p>GUID {@code EBE9D6E4-3D36-4B78-A7F6-2F14F0B3E3D6}：
     * Data1=0xEBE9D6E4, Data2=0x3D36, Data3=0x4B78, Data4=[0xA7,0xF6,0x2F,0x14,0xF0,0xB3,0xE3,0xD6]。</p>
     *
     * @return 已填充的 GUID 实例
     */
    private static GUID initBluetoothLeGuid() {
        GUID g = new GUID();
        g.Data1 = 0xEBE9D6E4;
        g.Data2 = 0x3D36;
        g.Data3 = 0x4B78;
        g.Data4 = new byte[]{(byte) 0xA7, (byte) 0xF6, (byte) 0x2F, (byte) 0x14,
                (byte) 0xF0, (byte) 0xB3, (byte) 0xE3, (byte) 0xD6};
        return g;
    }
}
