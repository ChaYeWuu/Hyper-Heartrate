package com.chayewuu.hyperheartrate.device.windows.win32;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

/**
 * Win32 Bluetooth GATT API 的 JNA 库接口。
 * <p>
 * 对应 {@code BluetoothApis.dll}，提供 BLE GATT 连接、断开、服务发现、
 * 特征值枚举与通知注册等能力。本接口为 {@link com.chayewuu.hyperheartrate.device.windows.WindowsBleConnector}
 * 的底层依赖。
 * </p>
 *
 * <p><b>API 说明：</b></p>
 * <ul>
 *     <li>{@link #BluetoothGATTConnect} — 建立 BLE GATT 连接，返回连接句柄；</li>
 *     <li>{@link #BluetoothGATTDisconnect} — 关闭 GATT 连接；</li>
 *     <li>{@link #BluetoothGATTGetServices} — 枚举设备 GATT 服务；</li>
 *     <li>{@link #BluetoothGATTGetCharacteristics} — 枚举服务下的特征值；</li>
 *     <li>{@link #BluetoothGATTRegisterEvent} — 注册特征值通知事件；</li>
 *     <li>{@link #BluetoothGATTUnregisterEvent} — 取消特征值通知事件。</li>
 * </ul>
 *
 * <p><b>当前状态：</b>JNA 方法声明完整，可被 {@code WindowsBleConnector} 调用。
 * 但实际调用需要先通过 SetupAPI 获取设备句柄（{@code hDevice}），
 * 该流程较复杂（涉及 {@code SetupDiGetClassDevs} 等多个 API），
 * 暂以骨架形式保留，详见 {@code WindowsBleConnector} 中的 TODO 注释。</p>
 *
 * <p>使用方式：{@code BluetoothGattLibrary.INSTANCE.BluetoothGATTConnect(...)}</p>
 */
public interface BluetoothGattLibrary extends Library {
    /** 单例实例，加载 {@code BluetoothApis} */
    BluetoothGattLibrary INSTANCE = Native.load("BluetoothApis", BluetoothGattLibrary.class);

    /** GATT 事件类型：特征值变化（用于启用通知） */
    int CharacteristicValueChangedEvent = 1;

    /** GATT 操作标志：直接连接（不使用缓存） */
    int BLUETOOTH_GATT_FLAG_DIRECT = 0x00000001;
    /** GATT 操作标志：使用缓存连接 */
    int BLUETOOTH_GATT_FLAG_BACKGROUND = 0x00000002;

    /**
     * 建立 BLE GATT 连接。
     *
     * @param hDevice       设备句柄（通过 SetupAPI 获取）
     * @param hRadio        蓝牙 radio 句柄，可为 {@code null}
     * @param isDirect      是否直接连接（{@code true} 不使用缓存）
     * @param leConnection  接收 GATT 连接句柄
     * @param flags         操作标志
     * @param reserved      保留参数，必须为 {@code null}
     * @return HRESULT，{@code 0}（S_OK）表示成功
     */
    int BluetoothGATTConnect(Pointer hDevice, Pointer hRadio, boolean isDirect,
                             PointerByReference leConnection, int flags, Pointer reserved);

    /**
     * 关闭 BLE GATT 连接。
     *
     * @param hDevice 设备句柄
     * @param flags   操作标志
     * @return HRESULT，{@code 0}（S_OK）表示成功
     */
    int BluetoothGATTDisconnect(Pointer hDevice, int flags);

    /**
     * 枚举设备 GATT 服务。
     * <p>调用时 {@code servicesBufferCount} 为 0 且 {@code servicesBuffer} 为 {@code null}，
     * 可通过 {@code servicesBufferActual} 获取服务数量，再分配缓冲区二次调用。</p>
     *
     * @param hDevice              设备句柄
     * @param servicesBufferCount  服务缓冲区可容纳的服务数（首次查询传 0）
     * @param servicesBuffer       服务缓冲区（首次查询可为 {@code null}）
     * @param servicesBufferActual 接收实际服务数（长度至少 1 的数组）
     * @param flags                操作标志
     * @return HRESULT，{@code 0}（S_OK）表示成功
     */
    int BluetoothGATTGetServices(Pointer hDevice, short servicesBufferCount,
                                 BTH_LE_GATT_SERVICE servicesBuffer,
                                 short[] servicesBufferActual, int flags);

    /**
     * 枚举指定服务下的特征值。
     * <p>调用时 {@code characteristicsBufferCount} 为 0 且 {@code characteristicsBuffer} 为 {@code null}，
     * 可通过 {@code characteristicsBufferActual} 获取特征值数量，再分配缓冲区二次调用。</p>
     *
     * @param hDevice                       设备句柄
     * @param service                       目标服务（{@code null} 表示所有服务）
     * @param characteristicsBufferCount    特征值缓冲区可容纳的数量（首次查询传 0）
     * @param characteristicsBuffer         特征值缓冲区（首次查询可为 {@code null}）
     * @param characteristicsBufferActual   接收实际特征值数（长度至少 1 的数组）
     * @param flags                         操作标志
     * @return HRESULT，{@code 0}（S_OK）表示成功
     */
    int BluetoothGATTGetCharacteristics(Pointer hDevice, BTH_LE_GATT_SERVICE service,
                                        short characteristicsBufferCount,
                                        BTH_LE_GATT_CHARACTERISTIC characteristicsBuffer,
                                        short[] characteristicsBufferActual, int flags);

    /**
     * 注册特征值通知事件。
     * <p>注册后，设备下发通知数据时将触发 {@code callback}。</p>
     *
     * @param hDevice           设备句柄
     * @param eventType         事件类型（{@link #CharacteristicValueChangedEvent}）
     * @param eventParameterIn  事件参数（指向特征值结构）
     * @param callback          事件回调函数指针
     * @param callbackContext   回调上下文（透传给回调）
     * @param pEventHandle      接收事件句柄（用于后续取消注册）
     * @param flags             操作标志
     * @return HRESULT，{@code 0}（S_OK）表示成功
     */
    int BluetoothGATTRegisterEvent(Pointer hDevice, int eventType, Pointer eventParameterIn,
                                   Callback callback, Pointer callbackContext,
                                   PointerByReference pEventHandle, int flags);

    /**
     * 取消特征值通知事件注册。
     *
     * @param hEventHandle 事件句柄（由 {@link #BluetoothGATTRegisterEvent} 返回）
     * @param flags        操作标志
     * @return HRESULT，{@code 0}（S_OK）表示成功
     */
    int BluetoothGATTUnregisterEvent(Pointer hEventHandle, int flags);

    /**
     * 读取特征值当前数据。
     * <p>用于读取电量（0x2A19）、设备信息等特征值的当前值。
     * 调用时 {@code characteristicValueBufferActual} 为 0 且
     * {@code characteristicValueBuffer} 为 {@code null}，可通过
     * {@code characteristicValueBufferActual} 获取所需缓冲区大小，
     * 再分配缓冲区二次调用。</p>
     *
     * @param hDevice                           设备句柄
     * @param characteristic                    目标特征值
     * @param characteristicValueBufferCount    缓冲区可容纳的数据字节数（首次传 0）
     * @param characteristicValueBuffer         缓冲区（可为 {@link Pointer#NULL} 首次查询大小）
     * @param characteristicValueBufferActual   接收实际所需字节数（长度至少 1 的 int 数组）
     * @param flags                             操作标志
     * @return HRESULT，{@code 0}（S_OK）表示成功
     */
    int BluetoothGATTGetCharacteristicValue(Pointer hDevice,
                                           BTH_LE_GATT_CHARACTERISTIC characteristic,
                                           int characteristicValueBufferCount,
                                           Pointer characteristicValueBuffer,
                                           int[] characteristicValueBufferActual,
                                           int flags);

    /**
     * 写入特征值（启用通知的客户端特性配置描述符写入）。
     *
     * @param hDevice                设备句柄
     * @param characteristicValue     待写入的特征值数据缓冲区指针
     * @param characteristicValueSize 数据大小（字节）
     * @param reliableWriteContext    可靠写入上下文，可为 {@code null}
     * @param flags                  操作标志
     * @return HRESULT，{@code 0}（S_OK）表示成功
     */
    int BluetoothGATTSetCharacteristicValue(Pointer hDevice,
                                            Pointer characteristicValue,
                                            int characteristicValueSize,
                                            Pointer reliableWriteContext,
                                            int flags);
}
