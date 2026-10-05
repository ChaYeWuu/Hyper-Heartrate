package com.chayewuu.hyperheartrate.device.windows.win32;

import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;

/**
 * Win32 SYSTEMTIME 结构体的 JNA 映射。
 * <p>
 * 对应 Windows SDK 中的 {@code SYSTEMTIME}，用于表示日期与时间，
 * 在 {@link BLUETOOTH_DEVICE_INFO} 中作为 {@code stLastSeen} / {@code stLastUsed} 字段类型。
 * </p>
 *
 * <p>字段均为 16 位无符号整数（WORD），Java 中以 {@code short} 承载。</p>
 */
public class SYSTEMTIME extends Structure {
    /** 年份 */
    public short wYear;
    /** 月份（1-12） */
    public short wMonth;
    /** 星期几（0=周日，1=周一，...，6=周六） */
    public short wDayOfWeek;
    /** 日（1-31） */
    public short wDay;
    /** 小时（0-23） */
    public short wHour;
    /** 分钟（0-59） */
    public short wMinute;
    /** 秒（0-59） */
    public short wSecond;
    /** 毫秒（0-999） */
    public short wMilliseconds;

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList(
                "wYear", "wMonth", "wDayOfWeek", "wDay",
                "wHour", "wMinute", "wSecond", "wMilliseconds");
    }
}
