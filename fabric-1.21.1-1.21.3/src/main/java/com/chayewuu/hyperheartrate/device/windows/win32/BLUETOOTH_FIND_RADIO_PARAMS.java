package com.chayewuu.hyperheartrate.device.windows.win32;

import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;

/**
 * Win32 BLUETOOTH_FIND_RADIO_PARAMS 结构体的 JNA 映射。
 * <p>
 * 对应 Windows SDK 中的 {@code BLUETOOTH_FIND_RADIO_PARAMS}，
 * 作为 {@code BluetoothFindFirstRadio} 的输入参数，目前仅包含结构体大小字段。
 * </p>
 */
public class BLUETOOTH_FIND_RADIO_PARAMS extends Structure {
    /** 结构体大小（字节） */
    public int dwSize;

    /** 默认构造器，自动设置 dwSize */
    public BLUETOOTH_FIND_RADIO_PARAMS() {
        dwSize = size();
    }

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("dwSize");
    }
}
