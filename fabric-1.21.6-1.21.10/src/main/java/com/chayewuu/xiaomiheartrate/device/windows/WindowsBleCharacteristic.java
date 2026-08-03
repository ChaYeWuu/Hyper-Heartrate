package com.chayewuu.xiaomiheartrate.device.windows;

import com.chayewuu.xiaomiheartrate.device.BleCharacteristic;
import com.chayewuu.xiaomiheartrate.device.NotificationCallback;
import com.chayewuu.xiaomiheartrate.util.ModLogger;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Windows 平台 BLE 特征值实现。
 * <p>
 * 封装 GATT 特征值的元数据（UUID、句柄、属性）与基础操作（读、写、订阅）。
 * 当前为骨架实现，实际读写与通知订阅通过 Win32 GATT API 完成的部分
 * 标记为 TODO，待 {@link WindowsBleConnector} 完整实现 GATT 句柄管理后接入。
 * </p>
 *
 * <p>本类为不可变元数据 + 可变通知回调的组合：
 * UUID、句柄等在构造时确定；通知回调通过 {@link AtomicReference} 保证线程安全。</p>
 */
public class WindowsBleCharacteristic implements BleCharacteristic {
    /** 日志前缀 */
    private static final String LOG_TAG = "[WindowsBleCharacteristic]";

    /** 特征值 UUID 字符串 */
    private final String uuid;
    /** 属性句柄 */
    private final short attributeHandle;
    /** 特征值句柄 */
    private final short valueHandle;
    /** 是否支持通知 */
    private final boolean notifiable;
    /** 是否可读 */
    private final boolean readable;
    /** 是否可写 */
    private final boolean writable;
    /** 关联的连接器（用于实际 GATT 操作） */
    private final WindowsBleConnector connector;

    /** 当前通知回调（线程安全），{@code null} 表示未订阅 */
    private final AtomicReference<NotificationCallback> notificationCallback = new AtomicReference<>();

    /**
     * 构造特征值。
     *
     * @param uuid            特征值 UUID 字符串
     * @param attributeHandle 属性句柄
     * @param valueHandle     特征值句柄
     * @param notifiable      是否支持通知
     * @param readable        是否可读
     * @param writable        是否可写
     * @param connector       关联的连接器
     */
    public WindowsBleCharacteristic(String uuid, short attributeHandle, short valueHandle,
                                    boolean notifiable, boolean readable, boolean writable,
                                    WindowsBleConnector connector) {
        this.uuid = uuid == null ? "" : uuid;
        this.attributeHandle = attributeHandle;
        this.valueHandle = valueHandle;
        this.notifiable = notifiable;
        this.readable = readable;
        this.writable = writable;
        this.connector = connector;
    }

    @Override
    public String getUuid() {
        return uuid;
    }

    /**
     * 获取属性句柄。
     *
     * @return 属性句柄
     */
    public short getAttributeHandle() {
        return attributeHandle;
    }

    /**
     * 获取特征值句柄。
     *
     * @return 特征值句柄
     */
    public short getValueHandle() {
        return valueHandle;
    }

    /**
     * 查询是否支持通知。
     *
     * @return {@code true} 表示支持通知
     */
    public boolean isNotifiable() {
        return notifiable;
    }

    /**
     * 查询是否可读。
     *
     * @return {@code true} 表示可读
     */
    public boolean isReadable() {
        return readable;
    }

    /**
     * 查询是否可写。
     *
     * @return {@code true} 表示可写
     */
    public boolean isWritable() {
        return writable;
    }

    @Override
    public byte[] readValue() {
        if (!readable) {
            ModLogger.warn("{} 特征值不可读: {}", LOG_TAG, uuid);
            return new byte[0];
        }
        // TODO: 通过 BluetoothGATTGetCharacteristicValue 读取特征值
        ModLogger.debug("{} readValue 尚未实现（GATT 读取待实现）: {}", LOG_TAG, uuid);
        return new byte[0];
    }

    @Override
    public void writeValue(byte[] data) {
        if (!writable) {
            ModLogger.warn("{} 特征值不可写: {}", LOG_TAG, uuid);
            return;
        }
        if (data == null || data.length == 0) {
            return;
        }
        // TODO: 通过 BluetoothGATTSetCharacteristicValue 写入特征值
        ModLogger.debug("{} writeValue 尚未实现（GATT 写入待实现）: {}", LOG_TAG, uuid);
    }

    @Override
    public void enableNotifications(NotificationCallback callback) {
        if (!notifiable) {
            ModLogger.warn("{} 特征值不支持通知: {}", LOG_TAG, uuid);
            return;
        }
        notificationCallback.set(callback);
        // 实际 GATT 通知注册由 WindowsBleConnector.registerHeartRateNotification 在连接时统一完成
        // 此处仅保存上层回调引用，供 onNotificationReceived 分发数据时使用
        ModLogger.info("{} 已设置通知回调: {}", LOG_TAG, uuid);
    }

    /**
     * 接收底层 GATT 通知数据并转发到上层回调。
     * <p>由 {@link WindowsBleConnector#dispatchHeartRateNotification(Pointer)}
     * 在 Win32 通知线程中调用。数据转发到 {@link #notificationCallback}
     * （即 {@link com.chayewuu.xiaomiheartrate.device.HeartRateNotificationHandler}），
     * 由解析器路由为心率值后更新 {@link com.chayewuu.xiaomiheartrate.heart.HeartRateManager}。</p>
     *
     * @param data 通知原始字节数据
     */
    public void onNotificationReceived(byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }
        NotificationCallback cb = notificationCallback.get();
        if (cb == null) {
            ModLogger.debug("{} 收到通知但未设置回调，丢弃数据: {}", LOG_TAG, uuid);
            return;
        }
        try {
            cb.onNotification(data);
        } catch (Throwable t) {
            ModLogger.error("{} 通知回调抛出异常", t, LOG_TAG);
        }
    }

    /**
     * 获取当前通知回调（供连接器分发通知数据时使用）。
     *
     * @return 通知回调，未订阅时为 {@code null}
     */
    public NotificationCallback getNotificationCallback() {
        return notificationCallback.get();
    }

    /**
     * 清除通知回调（断开连接时调用）。
     */
    public void clearNotificationCallback() {
        notificationCallback.set(null);
    }

    @Override
    public String toString() {
        return "WindowsBleCharacteristic{uuid='" + uuid + "', handle=" + attributeHandle
                + ", notify=" + notifiable + ", read=" + readable + ", write=" + writable + '}';
    }
}
