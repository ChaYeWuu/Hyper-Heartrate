package com.chayewuu.xiaomiheartrate.device.windows;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import com.chayewuu.xiaomiheartrate.device.BleAdapter;
import com.chayewuu.xiaomiheartrate.device.BleConnector;
import com.chayewuu.xiaomiheartrate.device.BleScanner;
import com.chayewuu.xiaomiheartrate.device.windows.win32.BLUETOOTH_FIND_RADIO_PARAMS;
import com.chayewuu.xiaomiheartrate.device.windows.win32.BluetoothLibrary;
import com.chayewuu.xiaomiheartrate.device.windows.win32.Kernel32Library;
import com.chayewuu.xiaomiheartrate.util.ModLogger;

/**
 * Windows 平台 BLE 适配器实现。
 * <p>
 * 实现 {@link BleAdapter} 工厂接口，创建 Windows 平台专用的扫描器与连接器。
 * 上层通过 {@link BleAdapter} 接口与之交互，不直接依赖 Windows 实现类，
 * 便于后续扩展 Linux/macOS 平台实现。
 * </p>
 *
 * <p>{@link #isSupported()} 通过 JNA 调用 {@code BluetoothFindFirstRadio} 检测
 * 系统中是否存在可用的蓝牙 radio，并结合操作系统名称判定是否为 Windows。</p>
 */
public class WindowsBleAdapter implements BleAdapter {
    /** 日志前缀 */
    private static final String LOG_TAG = "[WindowsBleAdapter]";

    @Override
    public BleScanner createScanner() {
        // 使用 .NET ble-tool (WinRT BluetoothLEAdvertisementWatcher) 扫描 BLE 广播设备
        // 可发现未配对的设备（如开启心率广播的小米手环/Redmi Watch）
        // 参考 https://github.com/Tnze/miband-heart-rate
        return new DotnetBleScanner();
    }

    @Override
    public BleConnector createConnector() {
        // 使用 .NET ble-tool (WinRT BluetoothLEDevice) 连接设备
        // 支持未配对设备连接，内部完成服务发现与通知订阅
        return new DotnetBleConnector();
    }

    @Override
    public boolean isSupported() {
        // 1. 操作系统必须为 Windows
        String osName = System.getProperty("os.name", "");
        if (osName == null || osName.toLowerCase().indexOf("win") < 0) {
            ModLogger.debug("{} 当前操作系统 {} 非 Windows，BLE 不支持", LOG_TAG, osName);
            return false;
        }

        // 2. 检测是否存在可用的蓝牙 radio
        // 注意：JNA 的 BluetoothLibrary 加载失败不应阻塞 BLE 功能（ble-tool 用 WinRT，不依赖 JNA）
        try {
            boolean hasRadio = hasBluetoothRadio();
            if (hasRadio) {
                return true;
            }
            ModLogger.warn("{} 未检测到蓝牙 radio，但 ble-tool (WinRT) 仍可尝试工作", LOG_TAG);
            // 即使 JNA 检测失败，也允许 ble-tool 尝试（WinRT 不依赖 JNA）
            return DotnetResolver.resolveDotnetPath() != null;
        } catch (Throwable t) {
            // JNA 加载失败（bthprops.cpl 缺失等）不阻塞，ble-tool 用 WinRT 独立工作
            ModLogger.warn("{} JNA 检测蓝牙 radio 失败（{}），改为依赖 ble-tool (WinRT)",
                    LOG_TAG, t.getMessage());
            return DotnetResolver.resolveDotnetPath() != null;
        }
    }

    /**
     * 通过 Win32 API 检测系统中是否存在蓝牙 radio。
     *
     * @return {@code true} 表示存在至少一个蓝牙 radio
     */
    private boolean hasBluetoothRadio() {
        BLUETOOTH_FIND_RADIO_PARAMS findParams = new BLUETOOTH_FIND_RADIO_PARAMS();
        PointerByReference radioHandleRef = new PointerByReference();

        Pointer hFind = BluetoothLibrary.INSTANCE.BluetoothFindFirstRadio(findParams, radioHandleRef);
        if (hFind == null) {
            int err = Native.getLastError();
            ModLogger.debug("{} 未找到蓝牙 radio（lastError={}）", LOG_TAG, err);
            return false;
        }

        boolean found = radioHandleRef.getValue() != null;
        // 关闭 radio 句柄（通过 Win32 CloseHandle，kernel32.dll）
        Pointer radioHandle = radioHandleRef.getValue();
        if (radioHandle != null) {
            try {
                Kernel32Library.INSTANCE.CloseHandle(radioHandle);
            } catch (Throwable ignored) {
                // 关闭失败不影响判定
            }
        }
        // 关闭枚举句柄
        try {
            BluetoothLibrary.INSTANCE.BluetoothFindRadioClose(hFind);
        } catch (Throwable ignored) {
            // 忽略
        }
        ModLogger.debug("{} 检测到蓝牙 radio: {}", LOG_TAG, found);
        return found;
    }
}
