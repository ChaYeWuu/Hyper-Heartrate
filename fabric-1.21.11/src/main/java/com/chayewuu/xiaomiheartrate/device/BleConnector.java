package com.chayewuu.xiaomiheartrate.device;

import java.util.List;

/**
 * BLE 连接器接口。
 * <p>
 * 封装对单个 {@link BleDevice} 的连接、断开、服务发现等 GATT 操作。
 * 实现类应保证耗时操作在后台线程执行。
 * </p>
 *
 * <p>同一时刻一个连接器仅维护一个连接。</p>
 */
public interface BleConnector {
    /**
     * 发起连接。
     * <p>连接过程在后台线程进行，结果通过 {@code callback} 异步回调。</p>
     *
     * @param device   目标设备
     * @param callback 连接状态回调
     */
    void connect(BleDevice device, ConnectionCallback callback);

    /**
     * 主动断开当前连接。
     * <p>若当前未连接则为空操作。</p>
     */
    void disconnect();

    /**
     * 查询是否已连接。
     *
     * @return {@code true} 表示当前处于已连接状态
     */
    boolean isConnected();

    /**
     * 发现服务并返回所有可用特征值。
     * <p>应在 {@link ConnectionCallback#onConnected(BleDevice)} 之后调用。</p>
     *
     * @return 该设备所有特征值列表，连接未建立时返回空列表
     */
    List<BleCharacteristic> discoverServices();
}
