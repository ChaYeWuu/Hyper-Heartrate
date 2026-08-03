package com.chayewuu.xiaomiheartrate.device.windows.win32;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * Win32 Kernel32 API 的 JNA 库接口（最小子集）。
 * <p>
 * 对应 {@code kernel32.dll}，声明 BLE 模块需要的句柄管理、文件打开
 * 与错误查询函数，避免直接依赖 {@code jna-platform} 的 {@code Kernel32}
 * （其 {@code CloseHandle} 参数为 {@code WinNT.HANDLE}，与本模块使用的
 * 裸 {@link Pointer} 不直接兼容）。
 * </p>
 *
 * <p><b>常量说明：</b></p>
 * <ul>
 *     <li>{@link #GENERIC_READ} / {@link #GENERIC_WRITE}：文件访问权限；</li>
 *     <li>{@link #OPEN_EXISTING}：打开已存在设备（BLE 设备句柄获取必须用此值）；</li>
 *     <li>{@link #FILE_SHARE_READ} / {@link #FILE_SHARE_WRITE}：共享模式。</li>
 * </ul>
 */
public interface Kernel32Library extends Library {
    /** 单例实例，加载 {@code kernel32} */
    Kernel32Library INSTANCE = Native.load("kernel32", Kernel32Library.class);

    // === 访问权限 ===
    /** 通用读权限 */
    int GENERIC_READ = 0x80000000;
    /** 通用写权限 */
    int GENERIC_WRITE = 0x40000000;

    // === 共享模式 ===
    /** 共享读 */
    int FILE_SHARE_READ = 0x00000001;
    /** 共享写 */
    int FILE_SHARE_WRITE = 0x00000002;

    // === 创建方式 ===
    /** 仅打开已存在设备（不存在则失败） */
    int OPEN_EXISTING = 3;

    // === 属性标志 ===
    /** 普通属性（无重叠 I/O） */
    int FILE_ATTRIBUTE_NORMAL = 0x80;

    // === 错误码 ===
    /** 操作成功完成 */
    int ERROR_SUCCESS = 0;
    /** 共享冲突 */
    int ERROR_SHARING_VIOLATION = 32;
    /** 文件未找到 */
    int ERROR_FILE_NOT_FOUND = 2;

    /**
     * 关闭一个打开的对象句柄。
     *
     * @param hObject 待关闭的句柄
     * @return {@code true} 表示成功关闭
     */
    boolean CloseHandle(Pointer hObject);

    /**
     * 打开或创建文件/设备（宽字符版本）。
     * <p>用于通过设备路径打开 BLE 设备句柄，返回的句柄供
     * {@link BluetoothGattLibrary#BluetoothGATTConnect} 使用。</p>
     *
     * @param lpFileName       文件/设备路径（宽字符串）
     * @param dwDesiredAccess  期望访问权限（如 {@link #GENERIC_READ} | {@link #GENERIC_WRITE}）
     * @param dwShareMode       共享模式
     * @param lpSecurityAttributes 安全属性，可为 {@code null}
     * @param dwCreationDisposition 创建方式（BLE 设备用 {@link #OPEN_EXISTING}）
     * @param dwFlagsAndAttributes 文件属性与标志
     * @param hTemplateFile    模板文件句柄，可为 {@code null}
     * @return 设备句柄；失败返回 {@code null}（调用 {@link #GetLastError()} 获取错误）
     */
    Pointer CreateFileW(String lpFileName, int dwDesiredAccess, int dwShareMode,
                       Pointer lpSecurityAttributes, int dwCreationDisposition,
                       int dwFlagsAndAttributes, Pointer hTemplateFile);

    /**
     * 获取当前线程的最近一次错误码。
     *
     * @return Win32 错误码（{@link #ERROR_SUCCESS} 表示无错误）
     */
    int GetLastError();
}
