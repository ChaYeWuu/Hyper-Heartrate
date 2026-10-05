package com.chayewuu.hyperheartrate.device.windows.win32;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.Guid.GUID;

/**
 * Win32 SP_DEVICE_INTERFACE_DATA 结构的 JNA 实现。
 * <p>
 * 表示设备接口信息，由 {@link SetupApiLibrary#SetupDiEnumDeviceInterfaces}
 * 返回。调用前必须设置 {@link #cbSize} 字段为结构体大小。
 * </p>
 *
 * <p><b>字段布局（64 位）：</b></p>
 * <ul>
 *     <li>{@code cbSize} ({@code DWORD}, 4 字节)</li>
 *     <li>{@code InterfaceClassGuid} ({@link GUID}, 16 字节)</li>
 *     <li>{@code Flags} ({@code DWORD}, 4 字节)</li>
 *     <li>{@code Reserved} ({@code ULONG_PTR}, 8 字节)</li>
 * </ul>
 */
@Structure.FieldOrder({"cbSize", "InterfaceClassGuid", "Flags", "Reserved"})
public class SP_DEVICE_INTERFACE_DATA extends Structure {
    /** 结构体大小（调用前必须赋值为 {@link #size()}） */
    public int cbSize;
    /** 设备接口类 GUID */
    public GUID InterfaceClassGuid;
    /** 接口标志 */
    public int Flags;
    /** 保留字段 */
    public Pointer Reserved;

    /**
     * 默认构造器，自动设置 {@code cbSize}。
     */
    public SP_DEVICE_INTERFACE_DATA() {
        super();
        this.cbSize = size();
    }
}
