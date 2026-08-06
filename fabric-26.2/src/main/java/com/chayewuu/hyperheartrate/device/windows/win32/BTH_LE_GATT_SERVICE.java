package com.chayewuu.hyperheartrate.device.windows.win32;

import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;

/**
 * Win32 BTH_LE_GATT_SERVICE 结构体的 JNA 映射（简化版）。
 * <p>
 * 对应 Windows SDK 中的 {@code BTH_LE_GATT_SERVICE}，描述一个 BLE GATT 服务。
 * 由 {@link BluetoothGattLibrary#BluetoothGATTGetServices} 填充。
 * </p>
 *
 * <p>字段映射说明：</p>
 * <ul>
 *     <li>{@code ServiceInterval}：服务句柄（USHORT，2 字节），映射为 Java {@code short}；</li>
 *     <li>{@code ServiceUuid}：服务 UUID，{@link BTH_LE_UUID}。</li>
 * </ul>
 */
public class BTH_LE_GATT_SERVICE extends Structure {
    /** 服务句柄 */
    public short ServiceHandle;
    /** 服务 UUID */
    public BTH_LE_UUID ServiceUuid = new BTH_LE_UUID();

    /** 默认构造器 */
    public BTH_LE_GATT_SERVICE() {
    }

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("ServiceHandle", "ServiceUuid");
    }

    /**
     * 获取服务 UUID 字符串。
     *
     * @return UUID 字符串
     */
    public String getServiceUuidString() {
        return ServiceUuid == null ? "0000" : ServiceUuid.toUuidString();
    }

    @Override
    public String toString() {
        return "BTH_LE_GATT_SERVICE{handle=" + ServiceHandle + ", uuid=" + getServiceUuidString() + '}';
    }
}
