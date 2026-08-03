package com.chayewuu.xiaomiheartrate.device.windows.win32;

import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;

/**
 * Win32 BTH_LE_GATT_CHARACTERISTIC 结构体的 JNA 映射（简化版）。
 * <p>
 * 对应 Windows SDK 中的 {@code BTH_LE_GATT_CHARACTERISTIC}，描述一个 BLE GATT 特征值。
 * 由 {@link BluetoothGattLibrary#BluetoothGATTGetCharacteristics} 填充。
 * </p>
 *
 * <p>字段映射说明：</p>
 * <ul>
 *     <li>{@code ServiceHandle}：所属服务句柄（USHORT）；</li>
 *     <li>{@code CharacteristicUuid}：特征值 UUID，{@link BTH_LE_UUID}；</li>
 *     <li>{@code AttributeHandle}：属性句柄（USHORT）；</li>
 *     <li>{@code CharacteristicValueHandle}：特征值句柄（USHORT）；</li>
 *     <li>{@code IsBroadcastable} 等布尔属性：原始定义为 USHORT，JNA 以 {@code short} 承载
 *         （非零为真）。</li>
 * </ul>
 *
 * <p><b>简化说明：</b>原始结构包含完整的属性位字段，本映射保留核心字段，
 * 省略部分扩展属性，确保编译通过与基本功能可用。</p>
 */
public class BTH_LE_GATT_CHARACTERISTIC extends Structure {
    /** 所属服务句柄 */
    public short ServiceHandle;
    /** 特征值 UUID */
    public BTH_LE_UUID CharacteristicUuid = new BTH_LE_UUID();
    /** 属性句柄 */
    public short AttributeHandle;
    /** 特征值句柄 */
    public short CharacteristicValueHandle;
    /** 是否可广播（非零为真） */
    public short IsBroadcastable;
    /** 是否可读（非零为真） */
    public short IsReadable;
    /** 是否可写（非零为真） */
    public short IsWritable;
    /** 是否可无响应写入（非零为真） */
    public short IsWritableWithoutResponse;
    /** 是否可签名写入（非零为真） */
    public short IsSignedWritable;
    /** 是否可通知（非零为真） */
    public short IsNotifiable;
    /** 是否可指示（非零为真） */
    public short IsIndicatable;
    /** 是否有扩展属性（非零为真） */
    public short HasExtendedProperties;

    /** 默认构造器 */
    public BTH_LE_GATT_CHARACTERISTIC() {
    }

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList(
                "ServiceHandle", "CharacteristicUuid",
                "AttributeHandle", "CharacteristicValueHandle",
                "IsBroadcastable", "IsReadable", "IsWritable",
                "IsWritableWithoutResponse", "IsSignedWritable",
                "IsNotifiable", "IsIndicatable", "HasExtendedProperties");
    }

    /**
     * 获取特征值 UUID 字符串。
     *
     * @return UUID 字符串
     */
    public String getCharacteristicUuidString() {
        return CharacteristicUuid == null ? "0000" : CharacteristicUuid.toUuidString();
    }

    /**
     * 查询是否支持通知。
     *
     * @return {@code true} 表示支持通知
     */
    public boolean notifiable() {
        return IsNotifiable != 0;
    }

    /**
     * 查询是否支持指示。
     *
     * @return {@code true} 表示支持指示
     */
    public boolean indicatable() {
        return IsIndicatable != 0;
    }

    @Override
    public String toString() {
        return "BTH_LE_GATT_CHARACTERISTIC{uuid=" + getCharacteristicUuidString()
                + ", attrHandle=" + AttributeHandle
                + ", valHandle=" + CharacteristicValueHandle
                + ", notify=" + notifiable() + '}';
    }
}
