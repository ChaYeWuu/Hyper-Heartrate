package com.chayewuu.hyperheartrate.device.windows.win32;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;

/**
 * Win32 BLUETOOTH_DEVICE_SEARCH_PARAMS 结构体的 JNA 映射。
 * <p>
 * 对应 Windows SDK 中的 {@code BLUETOOTH_DEVICE_SEARCH_PARAMS}，
 * 作为 {@code BluetoothFindFirstDevice} 的输入参数，控制设备搜索行为。
 * </p>
 *
 * <p>字段映射说明：</p>
 * <ul>
 *     <li>{@code dwSize}：结构体大小；</li>
 *     <li>{@code fReturnAuthenticated} / {@code fReturnRemembered} /
 *         {@code fReturnUnknown} / {@code fReturnConnected} / {@code fIssueInquiry}：
 *         BOOL（4 字节），JNA 以 {@code boolean} 承载；</li>
 *     <li>{@code cTimeoutMultiplier}：UCHAR（1 字节），查询超时倍率，
 *         实际超时 = 该值 × 1.28 秒，范围 0-48；</li>
 *     <li>{@code hRadio}：HANDLE（指针），指定使用的蓝牙 radio，
 *         为 {@code null} 表示使用默认 radio。JNA 以 {@link Pointer} 承载。</li>
 * </ul>
 */
public class BLUETOOTH_DEVICE_SEARCH_PARAMS extends Structure {
    /** 结构体大小（字节） */
    public int dwSize;
    /** 是否返回已认证设备 */
    public boolean fReturnAuthenticated;
    /** 是否返回已记忆设备 */
    public boolean fReturnRemembered;
    /** 是否返回未知设备（新设备） */
    public boolean fReturnUnknown;
    /** 是否返回已连接设备 */
    public boolean fReturnConnected;
    /** 是否发起一次新的查询（inquiry） */
    public boolean fIssueInquiry;
    /** 查询超时倍率（× 1.28 秒，0-48） */
    public byte cTimeoutMultiplier;
    /** 使用的蓝牙 radio 句柄，{@code null} 表示默认 */
    public Pointer hRadio;

    /** 默认构造器，自动设置 dwSize */
    public BLUETOOTH_DEVICE_SEARCH_PARAMS() {
        dwSize = size();
    }

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList(
                "dwSize",
                "fReturnAuthenticated", "fReturnRemembered", "fReturnUnknown",
                "fReturnConnected", "fIssueInquiry",
                "cTimeoutMultiplier", "hRadio");
    }
}
