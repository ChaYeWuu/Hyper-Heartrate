package com.chayewuu.xiaomiheartrate.device.windows.win32;

import com.sun.jna.Native;
import com.sun.jna.Structure;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Win32 SP_DEVICE_INTERFACE_DETAIL_DATA_W 结构的 JNA 实现。
 * <p>
 * 包含设备接口的详细路径（{@code DevicePath}），由
 * {@link SetupApiLibrary#SetupDiGetDeviceInterfaceDetailW} 填充。
 * {@code DevicePath} 为 UTF-16LE 编码的宽字符串，调用前需分配足够大的缓冲区。
 * </p>
 *
 * <p><b>cbSize 说明：</b></p>
 * <ul>
 *     <li>32 位 Windows：{@code sizeof(DWORD) + sizeof(WCHAR)} = 4 + 2 = 6</li>
 *     <li>64 位 Windows：由于 8 字节对齐，{@code cbSize = 8}</li>
 * </ul>
 *
 * <p><b>DevicePath 字段：</b>预分配 1024 字节缓冲区（512 个 WCHAR），
 * 足以容纳常见 BLE 设备路径（通常约 100~200 字节）。</p>
 */
public class SP_DEVICE_INTERFACE_DETAIL_DATA extends Structure {
    /** 结构体大小（按平台 32/64 位确定） */
    public int cbSize;
    /** 设备路径缓冲区（UTF-16LE 编码，预分配 1024 字节 = 512 个 WCHAR） */
    public byte[] DevicePath = new byte[1024];

    /**
     * 默认构造器，自动设置 {@code cbSize}。
     */
    public SP_DEVICE_INTERFACE_DETAIL_DATA() {
        super();
        // 64 位上 cbSize = 8，32 位上 cbSize = 6
        this.cbSize = Native.getNativeSize(com.sun.jna.Pointer.class) == 8 ? 8 : 6;
    }

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("cbSize", "DevicePath");
    }

    /**
     * 提取设备路径字符串。
     * <p>从 {@link #DevicePath} 字节缓冲区中读取以双字节 null 结尾的
     * UTF-16LE 字符串。</p>
     *
     * @return 设备路径字符串，失败返回空串
     */
    public String getDevicePath() {
        if (DevicePath == null) {
            return "";
        }
        // 查找 UTF-16LE 双字节 null 终止符
        int end = 0;
        while (end + 1 < DevicePath.length) {
            if (DevicePath[end] == 0 && DevicePath[end + 1] == 0) {
                break;
            }
            end += 2;
        }
        if (end == 0) {
            return "";
        }
        return new String(DevicePath, 0, end, StandardCharsets.UTF_16LE);
    }
}
