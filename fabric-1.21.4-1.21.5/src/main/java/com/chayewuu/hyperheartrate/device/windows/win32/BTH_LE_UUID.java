package com.chayewuu.hyperheartrate.device.windows.win32;

import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;

/**
 * Win32 BTH_LE_UUID 结构体的 JNA 映射（简化版）。
 * <p>
 * 对应 Windows SDK 中的 {@code BTH_LE_UUID}，表示 BLE UUID。
 * 原始定义为联合体（union），可为 16 位、32 位或 128 位 UUID。
 * 本映射采用 128 位固定布局（16 字节数组）以简化内存管理，
 * 并提供 {@link #getShortUuid()} / {@link #setShortUuid(short)} 等辅助方法
 * 支持 16 位标准 UUID 的读写。
 * </p>
 *
 * <p><b>简化说明：</b>原始 union 中 {@code ShortUuid}（2 字节）、
 * {@code LongUuid}（4 字节）与 {@code Uuid128}（16 字节）共享内存。
 * 本映射以 16 字节数组承载，16/32 位 UUID 存储于数组起始位置
 * （小端序），与 Windows 实际布局兼容。</p>
 */
public class BTH_LE_UUID extends Structure {
    /** 128 位 UUID 原始字节（小端序） */
    public byte[] Value = new byte[16];

    /** 默认构造器，全零 UUID */
    public BTH_LE_UUID() {
    }

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("Value");
    }

    /**
     * 获取 16 位短 UUID（标准 BLE UUID）。
     * <p>从 128 位 Value 的起始 2 字节按小端序读取。</p>
     *
     * @return 16 位 UUID 值
     */
    public short getShortUuid() {
        return (short) ((Value[0] & 0xFF) | ((Value[1] & 0xFF) << 8));
    }

    /**
     * 设置 16 位短 UUID。
     * <p>将 16 位 UUID 写入 Value 起始 2 字节（小端序），
     * 其余字节清零。</p>
     *
     * @param uuid 16 位 UUID 值
     */
    public void setShortUuid(short uuid) {
        Arrays.fill(Value, (byte) 0);
        Value[0] = (byte) (uuid & 0xFF);
        Value[1] = (byte) ((uuid >> 8) & 0xFF);
    }

    /**
     * 获取 UUID 的标准字符串表示。
     * <p>16 位 UUID 返回 4 位十六进制（如 {@code "0x2A37"}）；
     * 其余返回 128 位 UUID 格式。</p>
     *
     * @return UUID 字符串
     */
    public String toUuidString() {
        short shortUuid = getShortUuid();
        // 若高 14 字节全为 0，视为 16 位 UUID
        boolean isShort = true;
        for (int i = 2; i < 16; i++) {
            if (Value[i] != 0) {
                isShort = false;
                break;
            }
        }
        if (isShort) {
            return String.format("%04X", shortUuid);
        }
        // 128 位 UUID 格式：xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
        return String.format("%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
                Value[3], Value[2], Value[1], Value[0],
                Value[5], Value[4],
                Value[7], Value[6],
                Value[8], Value[9],
                Value[10], Value[11], Value[12], Value[13], Value[14], Value[15]);
    }

    @Override
    public String toString() {
        return toUuidString();
    }
}
