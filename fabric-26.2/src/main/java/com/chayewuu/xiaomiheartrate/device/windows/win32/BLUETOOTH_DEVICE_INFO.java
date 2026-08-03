package com.chayewuu.xiaomiheartrate.device.windows.win32;

import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;

/**
 * Win32 BLUETOOTH_DEVICE_INFO 结构体的 JNA 映射。
 * <p>
 * 对应 Windows SDK 中的 {@code BLUETOOTH_DEVICE_INFO}，由
 * {@code BluetoothFindFirstDevice} / {@code BluetoothFindNextDevice} 填充，
 * 描述一个蓝牙设备（含经典蓝牙与已配对的 BLE 设备）的元数据。
 * </p>
 *
 * <p>字段映射说明：</p>
 * <ul>
 *     <li>{@code dwSize}：结构体大小，调用前必须赋值为 {@link #size()}；</li>
 *     <li>{@code Address}：{@code BTH_ADDR}（ULONGLONG，8 字节），映射为 Java {@code long}；</li>
 *     <li>{@code ulClassofDevice}：设备类，DWORD（4 字节）；</li>
 *     <li>{@code fConnected} / {@code fRemembered} / {@code fAuthenticated}：
 *         Windows BOOL（4 字节），JNA 中以 {@code boolean} 承载；</li>
 *     <li>{@code stLastSeen} / {@code stLastUsed}：{@link SYSTEMTIME}；</li>
 *     <li>{@code szName}：{@code WCHAR[248]} 固定长度宽字符数组，
 *         JNA 中必须使用 {@code char[]}（Java char 为 16 位，与 WCHAR 一致），
 *         不能使用 {@code String}（String 在 JNA 中映射为指针，仅占 4/8 字节，
 *         会破坏结构体内存布局）。通过 {@link #getName()} 获取字符串。</li>
 * </ul>
 */
public class BLUETOOTH_DEVICE_INFO extends Structure {
    /** 结构体大小（字节），调用 API 前必须设置 */
    public int dwSize;
    /** 设备蓝牙地址（BTH_ADDR，64 位无符号） */
    public long Address;
    /** 设备类 */
    public int ulClassofDevice;
    /** 是否已连接 */
    public boolean fConnected;
    /** 是否已记忆（曾配对） */
    public boolean fRemembered;
    /** 是否已认证 */
    public boolean fAuthenticated;
    /** 最后一次被发现的时间 */
    public SYSTEMTIME stLastSeen;
    /** 最后一次被使用的时间 */
    public SYSTEMTIME stLastUsed;
    /** 设备名称（固定 248 个 WCHAR） */
    public char[] szName = new char[248];

    /** 默认构造器，自动设置 dwSize 为结构体实际大小 */
    public BLUETOOTH_DEVICE_INFO() {
        // 字段初始化（szName）在 super() 之后、构造器体之前完成
        dwSize = size();
    }

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList(
                "dwSize", "Address", "ulClassofDevice",
                "fConnected", "fRemembered", "fAuthenticated",
                "stLastSeen", "stLastUsed", "szName");
    }

    /**
     * 获取设备名称字符串（去除尾部的空字符填充）。
     *
     * @return 设备名称；若未广播名称则返回空字符串
     */
    public String getName() {
        return new String(szName).trim();
    }

    /**
     * 获取格式化的 MAC 地址字符串（形如 {@code AA:BB:CC:DD:EE:FF}）。
     *
     * @return 大写冒号分隔的 MAC 地址
     */
    public String getMacAddress() {
        long addr = Address;
        return String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                (addr >> 40) & 0xFF,
                (addr >> 32) & 0xFF,
                (addr >> 24) & 0xFF,
                (addr >> 16) & 0xFF,
                (addr >> 8) & 0xFF,
                addr & 0xFF);
    }
}
