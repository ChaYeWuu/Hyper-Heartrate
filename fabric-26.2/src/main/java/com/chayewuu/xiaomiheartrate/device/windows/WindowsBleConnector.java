package com.chayewuu.xiaomiheartrate.device.windows;

import com.sun.jna.Callback;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import com.chayewuu.xiaomiheartrate.device.BleCharacteristic;
import com.chayewuu.xiaomiheartrate.device.BleConnector;
import com.chayewuu.xiaomiheartrate.device.BleDevice;
import com.chayewuu.xiaomiheartrate.device.ConnectionCallback;
import com.chayewuu.xiaomiheartrate.device.windows.win32.BTH_LE_GATT_CHARACTERISTIC;
import com.chayewuu.xiaomiheartrate.device.windows.win32.BTH_LE_GATT_SERVICE;
import com.chayewuu.xiaomiheartrate.device.windows.win32.BluetoothGattLibrary;
import com.chayewuu.xiaomiheartrate.device.windows.win32.Kernel32Library;
import com.chayewuu.xiaomiheartrate.device.windows.win32.SP_DEVICE_INTERFACE_DATA;
import com.chayewuu.xiaomiheartrate.device.windows.win32.SP_DEVICE_INTERFACE_DETAIL_DATA;
import com.chayewuu.xiaomiheartrate.device.windows.win32.SetupApiLibrary;
import com.chayewuu.xiaomiheartrate.util.ModLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Windows 平台 BLE 连接器实现。
 * <p>
 * 通过 JNA 调用 Win32 Bluetooth GATT API（{@code BluetoothApis.dll}）与
 * SetupAPI（{@code setupapi.dll}）完成 BLE 设备的连接、断开、服务发现、
 * 通知注册与心率数据接收。所有耗时操作均在后台线程执行，不阻塞 GUI/渲染线程。
 * </p>
 *
 * <p><b>连接流程：</b></p>
 * <ol>
 *     <li>通过 SetupAPI 枚举 BLE 设备接口，匹配目标 MAC 地址获取设备路径；</li>
 *     <li>调用 {@code CreateFileW} 打开设备句柄；</li>
 *     <li>调用 {@code BluetoothGATTConnect} 建立 GATT 连接；</li>
 *     <li>调用 {@code BluetoothGATTGetServices} / {@code BluetoothGATTGetCharacteristics}
 *         枚举服务与特征值；</li>
 *     <li>调用 {@code BluetoothGATTRegisterEvent} 注册心率通知（0x2A37）；</li>
 *     <li>回调 {@link ConnectionCallback#onConnected}。</li>
 * </ol>
 *
 * <p><b>通知数据流：</b></p>
 * <pre>
 * Win32 GATT 通知（JNA Callback）
 *      ↓
 * WindowsBleConnector.gattEventCallback
 *      ↓ 解析 BTH_LE_GATT_CHARACTERISTIC_VALUE
 *      ↓
 * WindowsBleCharacteristic.onNotificationReceived(byte[])
 *      ↓
 * NotificationCallback.onNotification(byte[])（由 DeviceManagerImpl 设置为 HeartRateNotificationHandler）
 *      ↓
 * HeartRateParserRegistry → HeartRateManager.updateHeartRate(int)
 * </pre>
 *
 * <p><b>线程模型：</b>连接、断开操作提交到单线程 {@link ExecutorService}；
 * GATT 通知回调在 Win32 线程中被调用，通过 {@code volatile} 特征值引用分发数据。</p>
 */
public class WindowsBleConnector implements BleConnector {
    /** 日志前缀 */
    private static final String LOG_TAG = "[WindowsBleConnector]";

    /** 心率测量特征值 UUID 后缀（标准 GATT Heart Rate Service） */
    private static final String HEART_RATE_CHAR_UUID_SUFFIX = "2A37";

    /** 连接任务使用的后台线程池（单线程，确保连接操作串行） */
    private final ExecutorService connectExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "HeartRateMod-BLE-Connector");
        t.setDaemon(true);
        return t;
    });

    /** 当前是否已连接标志（线程安全） */
    private final AtomicBoolean connected = new AtomicBoolean(false);

    /** 当前连接的设备 */
    private volatile BleDevice currentDevice;

    /** 当前连接回调（用于断开时通知上层） */
    private volatile ConnectionCallback currentCallback;

    /** GATT 设备句柄（通过 SetupAPI + CreateFileW 获取） */
    private volatile Pointer deviceHandle;

    /** GATT 连接句柄（由 BluetoothGATTConnect 返回） */
    private volatile Pointer gattConnectionHandle;

    /** 通知事件句柄（由 BluetoothGATTRegisterEvent 返回，用于取消订阅） */
    private volatile Pointer notificationEventHandle;

    /** GATT 通知回调实例（JNA Callback，必须保持强引用避免被 GC） */
    private volatile GattEventCallback gattEventCallback;

    /** 心率特征值引用（通知数据分发目标，{@code volatile} 保证回调线程可见性） */
    private volatile WindowsBleCharacteristic heartRateCharacteristic;

    /** 已发现的特征值列表缓存 */
    private volatile List<BleCharacteristic> discoveredCharacteristics = Collections.emptyList();

    @Override
    public void connect(BleDevice device, ConnectionCallback callback) {
        if (device == null) {
            throw new IllegalArgumentException("目标设备不能为 null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("连接回调不能为 null");
        }
        // 若当前已连接，先断开
        if (connected.get()) {
            ModLogger.warn("{} 当前已有连接，先断开再连接新设备", LOG_TAG);
            disconnectInternal(false);
        }
        currentDevice = device;
        currentCallback = callback;
        connectExecutor.submit(() -> doConnect(device, callback));
    }

    /**
     * 实际连接逻辑（后台线程执行）。
     * <p>通过 SetupAPI 获取设备句柄，建立 GATT 连接，发现服务与特征值，
     * 注册心率通知，最后回调上层。</p>
     *
     * @param device   目标设备
     * @param callback 连接回调
     */
    private void doConnect(BleDevice device, ConnectionCallback callback) {
        ModLogger.info("{} 尝试连接设备: {} ({})", LOG_TAG, device.getName(), device.getAddress());
        try {
            // 步骤 1：通过 SetupAPI 获取设备句柄
            Pointer hDevice = acquireDeviceHandle(device);
            if (hDevice == null) {
                int lastError = Kernel32Library.INSTANCE.GetLastError();
                ModLogger.error("{} 获取设备句柄失败（lastError={}），设备可能未配对或不可达: {}",
                        LOG_TAG, lastError, device.getAddress());
                callback.onError("获取设备句柄失败：设备可能未配对或不可达（lastError=" + lastError + "）", null);
                cleanupHandles();
                return;
            }
            this.deviceHandle = hDevice;
            ModLogger.info("{} 已获取设备句柄: {}", LOG_TAG, device.getAddress());

            // 步骤 2：建立 GATT 连接
            PointerByReference connectionRef = new PointerByReference();
            int hr = BluetoothGattLibrary.INSTANCE.BluetoothGATTConnect(
                    hDevice, null, true, connectionRef,
                    BluetoothGattLibrary.BLUETOOTH_GATT_FLAG_DIRECT, null);
            if (hr != 0) {
                int lastError = Native.getLastError();
                ModLogger.error("{} BluetoothGATTConnect 失败: hr={}, lastError={}", LOG_TAG, hr, lastError);
                callback.onError("BluetoothGATTConnect 失败: hr=" + hr, null);
                cleanupHandles();
                return;
            }
            this.gattConnectionHandle = connectionRef.getValue();
            ModLogger.info("{} GATT 连接已建立", LOG_TAG);

            // 步骤 3：发现服务与特征值
            List<BleCharacteristic> characteristics = discoverServicesInternal(hDevice);
            this.discoveredCharacteristics = Collections.unmodifiableList(characteristics);
            ModLogger.info("{} 服务发现完成，特征值数: {}", LOG_TAG, characteristics.size());

            // 步骤 4：注册心率通知（心率特征值 0x2A37）
            boolean notifyRegistered = registerHeartRateNotification(hDevice, characteristics);
            if (!notifyRegistered) {
                ModLogger.warn("{} 心率通知注册失败，连接将保留但可能无法接收实时心率", LOG_TAG);
                // 不中断连接，允许 GUI 显示设备已连接但无心率数据
            }

            // 步骤 5：标记已连接，回调上层
            connected.set(true);
            ModLogger.info("{} 设备连接成功: {} ({})", LOG_TAG, device.getName(), device.getAddress());
            callback.onConnected(device);
        } catch (Throwable t) {
            ModLogger.error("{} 连接设备异常", t, LOG_TAG);
            cleanupHandles();
            callback.onError("连接设备异常: " + t.getMessage(), t);
        }
    }

    /**
     * 通过 SetupAPI 获取 BLE 设备句柄。
     * <p>枚举系统中所有 Bluetooth LE 设备接口，获取设备路径，从路径中匹配
     * 目标 MAC 地址，匹配成功后调用 {@code CreateFileW} 打开设备句柄。</p>
     *
     * <p><b>流程：</b></p>
     * <ol>
     *     <li>{@code SetupDiGetClassDevsW} 获取设备信息集；</li>
     *     <li>循环 {@code SetupDiEnumDeviceInterfaces} 枚举设备接口；</li>
     *     <li>{@code SetupDiGetDeviceInterfaceDetailW} 获取设备路径；</li>
     *     <li>从路径中提取 MAC 并与目标比较；</li>
     *     <li>{@code CreateFileW} 打开设备句柄。</li>
     * </ol>
     *
     * @param device 目标 BLE 设备
     * @return 设备句柄，获取失败返回 {@code null}
     */
    private Pointer acquireDeviceHandle(BleDevice device) {
        String targetMac = normalizeMac(device.getAddress());
        if (targetMac.isEmpty()) {
            ModLogger.error("{} 目标设备 MAC 地址为空", LOG_TAG);
            return null;
        }
        ModLogger.info("{} 开始通过 SetupAPI 查找设备句柄，目标 MAC: {}", LOG_TAG, targetMac);

        Pointer hDevInfo = null;
        try {
            int flags = SetupApiLibrary.DIGCF_PRESENT | SetupApiLibrary.DIGCF_DEVICEINTERFACE;
            hDevInfo = SetupApiLibrary.INSTANCE.SetupDiGetClassDevsW(
                    SetupApiLibrary.GUID_BLUETOOTHLE_DEVICE_INTERFACE_INIT, null, null, flags);
            if (hDevInfo == null) {
                int err = Kernel32Library.INSTANCE.GetLastError();
                ModLogger.error("{} SetupDiGetClassDevsW 失败: lastError={}", LOG_TAG, err);
                return null;
            }

            int index = 0;
            while (true) {
                SP_DEVICE_INTERFACE_DATA ifaceData = new SP_DEVICE_INTERFACE_DATA();
                boolean ok = SetupApiLibrary.INSTANCE.SetupDiEnumDeviceInterfaces(
                        hDevInfo, null, SetupApiLibrary.GUID_BLUETOOTHLE_DEVICE_INTERFACE_INIT,
                        index, ifaceData);
                if (!ok) {
                    // 枚举结束（ERROR_NO_MORE_ITEMS）或其他错误
                    break;
                }
                index++;

                // 第一次调用获取所需缓冲区大小
                int[] requiredSize = new int[1];
                SetupApiLibrary.INSTANCE.SetupDiGetDeviceInterfaceDetailW(
                        hDevInfo, ifaceData, null, 0, requiredSize, null);
                int lastErr = Kernel32Library.INSTANCE.GetLastError();
                // 预期返回 ERROR_INSUFFICIENT_BUFFER (122)
                if (requiredSize[0] == 0) {
                    ModLogger.debug("{} SetupDiGetDeviceInterfaceDetailW 查询大小失败: lastError={}",
                            LOG_TAG, lastErr);
                    continue;
                }

                // 第二次调用填充详情
                // SP_DEVICE_INTERFACE_DETAIL_DATA 的 cbSize 已在构造器中按平台设置（6 或 8）
                // DevicePath 预分配 1024 字节，足够容纳常见 BLE 设备路径
                SP_DEVICE_INTERFACE_DETAIL_DATA detailData = new SP_DEVICE_INTERFACE_DETAIL_DATA();
                // 传入缓冲区大小取 requiredSize 与 detailData 实际内存大小的较大值
                int bufferSize = Math.max(requiredSize[0], detailData.size());
                boolean detailOk = SetupApiLibrary.INSTANCE.SetupDiGetDeviceInterfaceDetailW(
                        hDevInfo, ifaceData, detailData, bufferSize, null, null);
                if (!detailOk) {
                    int err = Kernel32Library.INSTANCE.GetLastError();
                    ModLogger.debug("{} SetupDiGetDeviceInterfaceDetailW 填充失败: lastError={}",
                            LOG_TAG, err);
                    continue;
                }

                String devicePath = detailData.getDevicePath();
                if (devicePath == null || devicePath.isEmpty()) {
                    continue;
                }
                ModLogger.debug("{} 发现设备路径: {}", LOG_TAG, devicePath);

                // 从设备路径中匹配 MAC
                if (!macMatchesPath(targetMac, devicePath)) {
                    continue;
                }
                ModLogger.info("{} MAC 匹配成功，打开设备句柄", LOG_TAG);

                // 用 CreateFileW 打开设备
                Pointer hDevice = Kernel32Library.INSTANCE.CreateFileW(
                        devicePath,
                        Kernel32Library.GENERIC_READ | Kernel32Library.GENERIC_WRITE,
                        Kernel32Library.FILE_SHARE_READ | Kernel32Library.FILE_SHARE_WRITE,
                        null,
                        Kernel32Library.OPEN_EXISTING,
                        Kernel32Library.FILE_ATTRIBUTE_NORMAL,
                        null);
                if (hDevice == null) {
                    int err = Kernel32Library.INSTANCE.GetLastError();
                    // 共享冲突时，尝试只读模式
                    if (err == Kernel32Library.ERROR_SHARING_VIOLATION) {
                        ModLogger.warn("{} 设备句柄被占用（共享冲突），尝试只读模式", LOG_TAG);
                        hDevice = Kernel32Library.INSTANCE.CreateFileW(
                                devicePath,
                                Kernel32Library.GENERIC_READ,
                                Kernel32Library.FILE_SHARE_READ | Kernel32Library.FILE_SHARE_WRITE,
                                null,
                                Kernel32Library.OPEN_EXISTING,
                                Kernel32Library.FILE_ATTRIBUTE_NORMAL,
                                null);
                        if (hDevice == null) {
                            err = Kernel32Library.INSTANCE.GetLastError();
                            ModLogger.error("{} 只读模式打开仍失败: lastError={}", LOG_TAG, err);
                            continue;
                        }
                    } else {
                        ModLogger.error("{} CreateFileW 失败: lastError={}", LOG_TAG, err);
                        continue;
                    }
                }
                return hDevice;
            }
            ModLogger.warn("{} 未找到匹配的 BLE 设备接口（枚举 {} 个）", LOG_TAG, index);
            return null;
        } catch (Throwable t) {
            ModLogger.error("{} acquireDeviceHandle 异常", t, LOG_TAG);
            return null;
        } finally {
            if (hDevInfo != null) {
                try {
                    SetupApiLibrary.INSTANCE.SetupDiDestroyDeviceInfoList(hDevInfo);
                } catch (Throwable t) {
                    ModLogger.warn("{} 销毁设备信息集异常", t, LOG_TAG);
                }
            }
        }
    }

    /**
     * 规范化 MAC 地址为 12 位小写十六进制字符串（无分隔符）。
     * <p>例如 {@code "AA:BB:CC:DD:EE:FF"} → {@code "aabbccddeeff"}。</p>
     *
     * @param mac 原始 MAC 地址字符串
     * @return 规范化后的 MAC，无效返回空串
     */
    private static String normalizeMac(String mac) {
        if (mac == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < mac.length(); i++) {
            char c = mac.charAt(i);
            if (c == ':' || c == '-' || c == '_' || c == ' ' || c == '{' || c == '}') {
                continue;
            }
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    /**
     * 检查设备路径是否包含目标 MAC 地址。
     * <p>设备路径形如：
     * {@code \\?\BTHLEDevice#{GUID}_{MAC_HEX}#...}，其中 {@code MAC_HEX}
     * 为 12 位十六进制（无分隔符，通常小写）。</p>
     *
     * <p>同时支持正向字节序与反向字节序匹配（部分 Windows 版本可能使用反字节序）。</p>
     *
     * @param targetMac 目标 MAC（已规范化，12 位小写十六进制）
     * @param devicePath 设备路径
     * @return {@code true} 表示匹配
     */
    private static boolean macMatchesPath(String targetMac, String devicePath) {
        if (targetMac == null || targetMac.length() != 12 || devicePath == null) {
            return false;
        }
        String pathLower = devicePath.toLowerCase(Locale.ROOT);
        // 正向匹配
        if (pathLower.contains(targetMac)) {
            return true;
        }
        // 反向字节序匹配（每两字节反转）
        String reversedMac = reverseMacBytes(targetMac);
        return pathLower.contains(reversedMac);
    }

    /**
     * 反转 MAC 字节序（每两字节反转）。
     * <p>例如 {@code "aabbccddeeff"} → {@code "ffeeddccbbaa"}。</p>
     *
     * @param mac 12 位十六进制 MAC
     * @return 反转后的 MAC
     */
    private static String reverseMacBytes(String mac) {
        if (mac == null || mac.length() != 12) {
            return mac;
        }
        StringBuilder sb = new StringBuilder(12);
        for (int i = 10; i >= 0; i -= 2) {
            sb.append(mac.charAt(i));
            sb.append(mac.charAt(i + 1));
        }
        return sb.toString();
    }

    /**
     * 发现 GATT 服务与特征值（内部方法）。
     *
     * @param hDevice 设备句柄
     * @return 特征值列表，失败时返回空列表
     */
    private List<BleCharacteristic> discoverServicesInternal(Pointer hDevice) {
        List<BleCharacteristic> result = new ArrayList<>();
        try {
            short[] serviceCount = new short[1];
            int hr = BluetoothGattLibrary.INSTANCE.BluetoothGATTGetServices(
                    hDevice, (short) 0, null, serviceCount, 0);
            if (hr != 0 || serviceCount[0] == 0) {
                ModLogger.warn("{} BluetoothGATTGetServices(查询数量) 失败: hr={}, count={}",
                        LOG_TAG, hr, serviceCount[0]);
                return result;
            }

            BTH_LE_GATT_SERVICE[] services = (BTH_LE_GATT_SERVICE[])
                    new BTH_LE_GATT_SERVICE().toArray(serviceCount[0]);
            BTH_LE_GATT_SERVICE firstService = services[0];
            hr = BluetoothGattLibrary.INSTANCE.BluetoothGATTGetServices(
                    hDevice, serviceCount[0], firstService, serviceCount, 0);
            if (hr != 0) {
                ModLogger.warn("{} BluetoothGATTGetServices(获取服务) 失败: hr={}", LOG_TAG, hr);
                return result;
            }

            ModLogger.info("{} 发现 GATT 服务数: {}", LOG_TAG, serviceCount[0]);

            for (int i = 0; i < serviceCount[0]; i++) {
                BTH_LE_GATT_SERVICE service = services[i];
                collectCharacteristics(hDevice, service, result);
            }
        } catch (Throwable t) {
            ModLogger.error("{} 服务发现异常", t, LOG_TAG);
        }
        return result;
    }

    /**
     * 枚举单个服务下的特征值并加入结果列表。
     */
    private void collectCharacteristics(Pointer hDevice, BTH_LE_GATT_SERVICE service,
                                        List<BleCharacteristic> result) {
        try {
            short[] charCount = new short[1];
            int hr = BluetoothGattLibrary.INSTANCE.BluetoothGATTGetCharacteristics(
                    hDevice, service, (short) 0, null, charCount, 0);
            if (hr != 0 || charCount[0] == 0) {
                return;
            }

            BTH_LE_GATT_CHARACTERISTIC[] chars = (BTH_LE_GATT_CHARACTERISTIC[])
                    new BTH_LE_GATT_CHARACTERISTIC().toArray(charCount[0]);
            BTH_LE_GATT_CHARACTERISTIC firstChar = chars[0];
            hr = BluetoothGattLibrary.INSTANCE.BluetoothGATTGetCharacteristics(
                    hDevice, service, charCount[0], firstChar, charCount, 0);
            if (hr != 0) {
                ModLogger.warn("{} BluetoothGATTGetCharacteristics 失败: hr={}", LOG_TAG, hr);
                return;
            }

            for (int i = 0; i < charCount[0]; i++) {
                BTH_LE_GATT_CHARACTERISTIC ch = chars[i];
                String uuid = ch.getCharacteristicUuidString();
                WindowsBleCharacteristic bleCh = new WindowsBleCharacteristic(
                        uuid, ch.AttributeHandle, ch.CharacteristicValueHandle,
                        ch.notifiable(), ch.IsReadable != 0, ch.IsWritable != 0, this);
                result.add(bleCh);
                ModLogger.debug("{} 特征值: {}", LOG_TAG, bleCh);
            }
        } catch (Throwable t) {
            ModLogger.error("{} 枚举特征值异常", t, LOG_TAG);
        }
    }

    /**
     * 注册心率通知（特征值 0x2A37）。
     * <p>查找心率测量特征值，创建 JNA Callback，调用
     * {@code BluetoothGATTRegisterEvent} 注册通知事件。
     * 通知触发时通过 {@link #dispatchHeartRateNotification(Pointer)} 解析数据
     * 并分发到 {@link WindowsBleCharacteristic}。</p>
     *
     * @param hDevice        设备句柄
     * @param characteristics 特征值列表
     * @return {@code true} 表示注册成功
     */
    private boolean registerHeartRateNotification(Pointer hDevice,
                                                  List<BleCharacteristic> characteristics) {
        try {
            BTH_LE_GATT_CHARACTERISTIC heartRateChar = null;
            WindowsBleCharacteristic heartRateBleChar = null;
            for (BleCharacteristic ch : characteristics) {
                String uuid = ch.getUuid();
                if (uuid != null && uuid.toUpperCase(Locale.ROOT).endsWith(HEART_RATE_CHAR_UUID_SUFFIX)) {
                    heartRateBleChar = (WindowsBleCharacteristic) ch;
                    break;
                }
            }
            if (heartRateBleChar == null) {
                ModLogger.warn("{} 未找到心率测量特征值（0x2A37），跳过通知注册", LOG_TAG);
                return false;
            }
            this.heartRateCharacteristic = heartRateBleChar;

            // 查找原始 BTH_LE_GATT_CHARACTERISTIC 结构用于注册事件
            // 由于我们缓存的是 WindowsBleCharacteristic 包装类，需要重新获取原始结构
            BTH_LE_GATT_CHARACTERISTIC nativeChar = findNativeCharacteristic(
                    hDevice, heartRateBleChar.getAttributeHandle());
            if (nativeChar == null) {
                ModLogger.warn("{} 无法获取心率特征值原始结构，跳过通知注册", LOG_TAG);
                return false;
            }

            // 创建 JNA Callback（必须保持强引用避免被 GC）
            this.gattEventCallback = new GattEventCallback();

            PointerByReference eventHandleRef = new PointerByReference();
            int hr = BluetoothGattLibrary.INSTANCE.BluetoothGATTRegisterEvent(
                    hDevice,
                    BluetoothGattLibrary.CharacteristicValueChangedEvent,
                    nativeChar.getPointer(),
                    this.gattEventCallback,
                    null,
                    eventHandleRef,
                    0);
            if (hr != 0) {
                int lastError = Native.getLastError();
                ModLogger.error("{} BluetoothGATTRegisterEvent 失败: hr={}, lastError={}",
                        LOG_TAG, hr, lastError);
                return false;
            }
            this.notificationEventHandle = eventHandleRef.getValue();
            ModLogger.info("{} 心率通知注册成功（特征值: {}）", LOG_TAG, heartRateBleChar.getUuid());
            return true;
        } catch (Throwable t) {
            ModLogger.error("{} 注册心率通知异常", t, LOG_TAG);
            return false;
        }
    }

    /**
     * 按属性句柄查找原始 BTH_LE_GATT_CHARACTERISTIC 结构。
     * <p>由于 {@link WindowsBleCharacteristic} 仅保存元数据，通知注册需要
     * 传入原始结构指针，此方法重新枚举特征值并返回匹配项。</p>
     *
     * @param hDevice         设备句柄
     * @param attributeHandle 目标属性句柄
     * @return 匹配的特征值结构，未找到返回 {@code null}
     */
    private BTH_LE_GATT_CHARACTERISTIC findNativeCharacteristic(Pointer hDevice, short attributeHandle) {
        try {
            short[] serviceCount = new short[1];
            int hr = BluetoothGattLibrary.INSTANCE.BluetoothGATTGetServices(
                    hDevice, (short) 0, null, serviceCount, 0);
            if (hr != 0 || serviceCount[0] == 0) {
                return null;
            }
            BTH_LE_GATT_SERVICE[] services = (BTH_LE_GATT_SERVICE[])
                    new BTH_LE_GATT_SERVICE().toArray(serviceCount[0]);
            BluetoothGattLibrary.INSTANCE.BluetoothGATTGetServices(
                    hDevice, serviceCount[0], services[0], serviceCount, 0);

            for (int i = 0; i < serviceCount[0]; i++) {
                BTH_LE_GATT_SERVICE service = services[i];
                short[] charCount = new short[1];
                int hrChar = BluetoothGattLibrary.INSTANCE.BluetoothGATTGetCharacteristics(
                        hDevice, service, (short) 0, null, charCount, 0);
                if (hrChar != 0 || charCount[0] == 0) {
                    continue;
                }
                BTH_LE_GATT_CHARACTERISTIC[] chars = (BTH_LE_GATT_CHARACTERISTIC[])
                        new BTH_LE_GATT_CHARACTERISTIC().toArray(charCount[0]);
                BluetoothGattLibrary.INSTANCE.BluetoothGATTGetCharacteristics(
                        hDevice, service, charCount[0], chars[0], charCount, 0);
                for (int j = 0; j < charCount[0]; j++) {
                    if (chars[j].AttributeHandle == attributeHandle) {
                        return chars[j];
                    }
                }
            }
        } catch (Throwable t) {
            ModLogger.error("{} findNativeCharacteristic 异常", t, LOG_TAG);
        }
        return null;
    }

    /**
     * 分发心率通知数据。
     * <p>由 {@link GattEventCallback} 在 Win32 通知线程中调用，
     * 解析 {@code EventOutParameter} 中的 {@code BTH_LE_GATT_CHARACTERISTIC_VALUE}
     * 并转发到 {@link WindowsBleCharacteristic#onNotificationReceived(byte[])}。</p>
     *
     * @param eventOutParameter 事件输出参数指针
     */
    private void dispatchHeartRateNotification(Pointer eventOutParameter) {
        try {
            if (eventOutParameter == null) {
                return;
            }
            // EventOutParameter 指向 BTH_LE_GATT_CHARACTERISTIC_VALUE_EVENT
            // 该结构第一个字段是 ChangedCharacteristicValue 指针（PBTH_LE_GATT_CHARACTERISTIC_VALUE）
            Pointer changedValuePtr = eventOutParameter.getPointer(0);
            if (changedValuePtr == null) {
                return;
            }
            // BTH_LE_GATT_CHARACTERISTIC_VALUE: ULONG DataSize (4 字节) + UCHAR Data[]
            int dataSize = changedValuePtr.getInt(0);
            if (dataSize <= 0 || dataSize > 512) {
                ModLogger.debug("{} 通知数据大小异常: {}, 忽略", LOG_TAG, dataSize);
                return;
            }
            byte[] data = changedValuePtr.getByteArray(4, dataSize);
            WindowsBleCharacteristic target = heartRateCharacteristic;
            if (target != null) {
                target.onNotificationReceived(data);
            }
        } catch (Throwable t) {
            ModLogger.error("{} 分发心率通知数据异常", t, LOG_TAG);
        }
    }

    @Override
    public void disconnect() {
        disconnectInternal(true);
    }

    /**
     * 内部断开连接实现。
     *
     * @param notifyCallback 是否回调通知上层断开事件
     */
    private void disconnectInternal(boolean notifyCallback) {
        boolean wasConnected = connected.getAndSet(false);
        BleDevice device = currentDevice;
        ConnectionCallback callback = currentCallback;

        ModLogger.info("{} 断开设备连接: {}", LOG_TAG,
                device == null ? "未知" : device.getAddress());

        // 1. 取消通知订阅
        if (notificationEventHandle != null) {
            try {
                BluetoothGattLibrary.INSTANCE.BluetoothGATTUnregisterEvent(
                        notificationEventHandle, 0);
                ModLogger.debug("{} 已取消通知订阅", LOG_TAG);
            } catch (Throwable t) {
                ModLogger.warn("{} 取消通知订阅异常", t, LOG_TAG);
            }
            notificationEventHandle = null;
        }

        // 2. 清除特征值通知回调
        for (BleCharacteristic ch : discoveredCharacteristics) {
            if (ch instanceof WindowsBleCharacteristic windowsCh) {
                windowsCh.clearNotificationCallback();
            }
        }

        // 3. 关闭 GATT 连接
        if (deviceHandle != null) {
            try {
                BluetoothGattLibrary.INSTANCE.BluetoothGATTDisconnect(deviceHandle, 0);
                ModLogger.debug("{} 已关闭 GATT 连接", LOG_TAG);
            } catch (Throwable t) {
                ModLogger.warn("{} BluetoothGATTDisconnect 异常", t, LOG_TAG);
            }
        }

        // 4. 关闭设备句柄
        if (deviceHandle != null) {
            try {
                Kernel32Library.INSTANCE.CloseHandle(deviceHandle);
            } catch (Throwable t) {
                ModLogger.warn("{} CloseHandle 异常", t, LOG_TAG);
            }
        }

        cleanupHandles();

        // 5. 回调通知上层
        if (notifyCallback && wasConnected && callback != null) {
            try {
                callback.onDisconnected(device);
            } catch (Throwable t) {
                ModLogger.error("{} 断开回调异常", t, LOG_TAG);
            }
        }

        currentDevice = null;
        currentCallback = null;
        discoveredCharacteristics = Collections.emptyList();
        heartRateCharacteristic = null;
    }

    /**
     * 清理 GATT 句柄引用（不调用 Win32 API，仅清空字段）。
     */
    private void cleanupHandles() {
        deviceHandle = null;
        gattConnectionHandle = null;
        notificationEventHandle = null;
        gattEventCallback = null;
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public List<BleCharacteristic> discoverServices() {
        return discoveredCharacteristics;
    }

    /**
     * 关闭连接器，释放后台线程池。
     * <p>由 {@link com.chayewuu.xiaomiheartrate.device.DeviceManagerImpl} 在 Mod 卸载时调用。</p>
     */
    public void shutdown() {
        disconnect();
        connectExecutor.shutdownNow();
        ModLogger.info("{} 连接器已关闭", LOG_TAG);
    }

    /**
     * GATT 事件回调接口（JNA Callback）。
     * <p>对应 Win32 {@code PFNBLUETOOTH_GATT_EVENT_CALLBACK}：
     * <pre>void Callback(BTH_LE_GATT_EVENT_TYPE EventType, PVOID EventOutParameter, PVOID Context)</pre>
     * JNA 通过反射自动将实例注册为 C 函数指针。<b>必须保持强引用</b>
     * （存于 {@link #gattEventCallback}），否则实例被 GC 后回调将引发崩溃。</p>
     */
    private class GattEventCallback implements Callback {
        /**
         * GATT 事件回调方法（由 Win32 在通知线程调用）。
         *
         * @param eventType         事件类型（1 = 特征值变化）
         * @param eventOutParameter 事件输出参数指针
         * @param context           回调上下文（注册时传入，此处为 {@code null}）
         */
        @SuppressWarnings("unused")
        public void callback(int eventType, Pointer eventOutParameter, Pointer context) {
            if (eventType == BluetoothGattLibrary.CharacteristicValueChangedEvent) {
                dispatchHeartRateNotification(eventOutParameter);
            }
        }
    }
}
