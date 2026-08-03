package com.chayewuu.xiaomiheartrate.device;

/**
 * BLE 设备连接状态枚举。
 * <p>
 * 由 {@link DeviceManager#getConnectionState()} 返回，用于描述当前设备连接生命周期的阶段。
 * </p>
 */
public enum ConnectionState {
    /** 已断开（未连接） */
    DISCONNECTED,
    /** 正在扫描设备 */
    SCANNING,
    /** 正在建立连接 */
    CONNECTING,
    /** 已连接 */
    CONNECTED,
    /** 连接断开后正在自动重连 */
    RECONNECTING
}
