package com.chayewuu.hyperheartrate.device;

import com.chayewuu.hyperheartrate.heart.HeartRateManager;
import com.chayewuu.hyperheartrate.util.ModLogger;

/**
 * 心率通知处理器。
 * <p>
 * 衔接 BLE 特征值通知与 {@link HeartRateManager} 的中间层：实现
 * {@link NotificationCallback} 接收底层 GATT 通知数据，通过
 * {@link HeartRateParserRegistry} 路由到合适的 {@link HeartRateParser}
 * 解析为心率值，并最终调用 {@link HeartRateManager#updateHeartRate(int)}
 * 更新全局心率状态、写入 {@link com.chayewuu.hyperheartrate.heart.HeartRateStorage}
 * 环形缓冲并通知所有 {@link com.chayewuu.hyperheartrate.heart.HeartRateListener}。
 * </p>
 *
 * <p><b>数据流：</b></p>
 * <pre>
 * BLE 特征值通知（0x2A37）
 *      ↓
 * HeartRateNotificationHandler.onNotification(byte[])
 *      ↓
 * HeartRateParserRegistry.getParser(device)
 *      ↓
 * HeartRateParser.parseHeartRate(byte[])
 *      ↓
 * HeartRateManager.updateHeartRate(int)
 *      ↓
 * HeartRateStorage.add() + HeartRateListener 回调
 *      ↓
 * HUD / GUI 实时刷新
 * </pre>
 *
 * <p><b>线程安全：</b>设备引用使用 {@code volatile} 保护（连接/断开与通知
 * 可能在不同线程并发触发）；解析器注册表与 {@link HeartRateManager}
 * 自身均为线程安全实现。解析器无状态，可被多线程并发调用。</p>
 */
public class HeartRateNotificationHandler implements NotificationCallback {
    /** 日志前缀 */
    private static final String LOG_TAG = "[HeartRateNotificationHandler]";

    /** 解析器注册表（线程安全） */
    private final HeartRateParserRegistry registry;

    /** 当前关联设备，{@code volatile} 保证多线程可见性 */
    private volatile BleDevice currentDevice;

    /**
     * 构造处理器，使用默认的 {@link HeartRateParserRegistry}。
     */
    public HeartRateNotificationHandler() {
        this(new HeartRateParserRegistry());
    }

    /**
     * 构造处理器，注入自定义解析器注册表（便于测试与扩展）。
     *
     * @param registry 解析器注册表
     */
    public HeartRateNotificationHandler(HeartRateParserRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("解析器注册表不能为 null");
        }
        this.registry = registry;
    }

    /**
     * 设置当前关联设备。
     * <p>设备连接成功后调用，供解析器路由使用；断开时传 {@code null} 清空。</p>
     *
     * @param device 当前设备，{@code null} 表示断开
     */
    public void setDevice(BleDevice device) {
        this.currentDevice = device;
    }

    /**
     * 获取当前关联设备。
     *
     * @return 当前设备，未连接时为 {@code null}
     */
    public BleDevice getDevice() {
        return currentDevice;
    }

    /**
     * 获取解析器注册表（供外部查询或动态注册）。
     *
     * @return 注册表实例
     */
    public HeartRateParserRegistry getRegistry() {
        return registry;
    }

    @Override
    public void onNotification(byte[] data) {
        if (data == null || data.length == 0) {
            ModLogger.debug("{} 收到空通知数据，忽略", LOG_TAG);
            return;
        }
        BleDevice device = currentDevice;
        HeartRateParser parser = registry.getParser(device);
        if (parser == null) {
            ModLogger.warn("{} 未找到可用的心率解析器，丢弃通知数据", LOG_TAG);
            return;
        }
        int heartRate;
        try {
            heartRate = parser.parseHeartRate(data);
        } catch (Throwable t) {
            ModLogger.error("{} 心率数据解析异常", t, LOG_TAG);
            return;
        }
        if (heartRate <= 0) {
            ModLogger.debug("{} 解析得到无效心率值: {}，忽略", LOG_TAG, heartRate);
            return;
        }
        // 委托 HeartRateManager 更新当前值、存入历史并通知监听器
        HeartRateManager.getInstance().updateHeartRate(heartRate);
        ModLogger.debug("{} 心率已更新: {} BPM（设备: {}）",
                LOG_TAG, heartRate,
                device == null ? "未知" : device.getAddress());
    }
}
