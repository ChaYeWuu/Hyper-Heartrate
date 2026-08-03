package com.chayewuu.xiaomiheartrate.device.windows.win32;

import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;

/**
 * Win32 BLUETOOTH_RADIO_INFO 结构体的 JNA 映射。
 * <p>
 * 对应 Windows SDK 中的 {@code BLUETOOTH_RADIO_INFO}，
 * 由 {@code BluetoothGetRadioInfo} 填充，描述本机蓝牙适配器（radio）的信息。
 * 用于 {@link com.chayewuu.xiaomiheartrate.device.windows.WindowsBleAdapter#isSupported()}
 * 检测系统中是否存在可用的蓝牙 radio。
 * </p>
 *
 * <p>字段映射说明：</p>
 * <ul>
 *     <li>{@code dwSize}：结构体大小；</li>
 *     <li>{@code address}：{@code BTH_ADDR}（ULONGLONG，8 字节），映射为 Java {@code long}；</li>
 *     <li>{@code szName}：{@code WCHAR[248]}，固定长度宽字符数组，映射为 {@code char[]}；</li>
 *     <li>{@code ulClassofDevice}：设备类，ULONG（4 字节）；</li>
 *     <li>{@code lmpSubversion}：{@code LUID}（8 字节），映射为 Java {@code long}；</li>
 *     <li>{@code mfg}：{@code USHORT}（2 字节），映射为 Java {@code short}。</li>
 * </ul>
 */
public class BLUETOOTH_RADIO_INFO extends Structure {
    /** 结构体大小（字节） */
    public int dwSize;
    /** 蓝牙 radio 地址 */
    public long address;
    /** radio 名称（固定 248 个 WCHAR） */
    public char[] szName = new char[248];
    /** 设备类 */
    public int ulClassofDevice;
    /** LMP 子版本（LUID，8 字节） */
    public long lmpSubversion;
    /** 厂商 ID */
    public short mfg;

    /** 默认构造器，自动设置 dwSize */
    public BLUETOOTH_RADIO_INFO() {
        dwSize = size();
    }

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList(
                "dwSize", "address", "szName",
                "ulClassofDevice", "lmpSubversion", "mfg");
    }

    /**
     * 获取 radio 名称字符串。
     *
     * @return radio 名称
     */
    public String getRadioName() {
        return new String(szName).trim();
    }
}
