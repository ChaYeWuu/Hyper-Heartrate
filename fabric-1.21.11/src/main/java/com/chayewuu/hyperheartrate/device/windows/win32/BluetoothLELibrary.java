package com.chayewuu.hyperheartrate.device.windows.win32;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

/**
 * Win32 Bluetooth LE / WinRT 相关 API 的 JNA 库接口。
 * <p>
 * Windows 上实时 BLE 广播扫描（发现未配对的新 BLE 设备）的官方途径是 WinRT
 * {@code Windows.Devices.Bluetooth.Advertisement.BluetoothLEAdvertisementWatcher}，
 * 该 API 通过 COM 激活方式调用。本接口声明 WinRT 激活所需的底层函数
 * （{@code combase.dll}），为后续实现真正的 BLE 广播扫描预留入口。
 * </p>
 *
 * <p><b>当前状态（骨架）：</b>仅声明 WinRT 激活 API，未实现完整的
 * {@code BluetoothLEAdvertisementWatcher} 调用链。实际 BLE 设备发现仍依赖
 * {@link BluetoothLibrary#BluetoothFindFirstDevice} 枚举已配对/记忆的设备。</p>
 *
 * <p><b>TODO（后续完善）：</b>通过以下步骤实现实时 BLE 广播扫描：</p>
 * <ol>
 *     <li>调用 {@link #RoInitialize} 初始化 WinRT 环境；</li>
 *     <li>调用 {@link #WindowsCreateString} 创建
 *         {@code "Windows.Devices.Bluetooth.Advertisement.BluetoothLEAdvertisementWatcher"}
 *         的 HSTRING；</li>
 *     <li>调用 {@link #RoActivateInstance} 激活 watcher 实例；</li>
 *     <li>通过 {@link #RoGetActivationFactory} 获取 IInspectable 接口，
 *         进而 QueryInterface 到 watcher 的事件接口；</li>
 *     <li>注册 {@code Received} 事件回调，处理广播包。</li>
 * </ol>
 *
 * <p>上述流程涉及大量 COM 接口定义与 GUID，复杂度较高，暂以骨架形式保留。</p>
 */
public interface BluetoothLELibrary extends Library {
    /** 单例实例，加载 {@code combase}（WinRT 运行时） */
    BluetoothLELibrary INSTANCE = Native.load("combase", BluetoothLELibrary.class);

    /** WinRT 初始化类型：单线程 */
    int RO_INIT_SINGLETHREADED = 0;
    /** WinRT 初始化类型：多线程 */
    int RO_INIT_MULTITHREADED = 1;

    /**
     * 初始化 WinRT 环境。
     *
     * @param initType 初始化类型（{@link #RO_INIT_SINGLETHREADED} 或 {@link #RO_INIT_MULTITHREADED}）
     * @return HRESULT，{@code 0}（S_OK）表示成功
     */
    int RoInitialize(int initType);

    /**
     * 创建 WinRT HSTRING。
     *
     * @param sourceString 源字符串（UTF-16）
     * @param length       字符串长度
     * @param hString      接收创建的 HSTRING
     * @return HRESULT，{@code 0} 表示成功
     */
    int WindowsCreateString(String sourceString, int length, PointerByReference hString);

    /**
     * 销毁 WinRT HSTRING。
     *
     * @param hString 待销毁的 HSTRING
     * @return HRESULT，{@code 0} 表示成功
     */
    int WindowsDeleteString(Pointer hString);

    /**
     * 激活 WinRT 类实例。
     *
     * @param classId 类 ID（HSTRING）
     * @param instance 接收激活的实例
     * @return HRESULT，{@code 0} 表示成功
     */
    int RoActivateInstance(Pointer classId, PointerByReference instance);

    /**
     * 获取 WinRT 类的激活工厂。
     *
     * @param classId 类 ID（HSTRING）
     * @param iid     接口 GUID 指针
     * @param factory 接收工厂实例
     * @return HRESULT，{@code 0} 表示成功
     */
    int RoGetActivationFactory(Pointer classId, Pointer iid, PointerByReference factory);
}
