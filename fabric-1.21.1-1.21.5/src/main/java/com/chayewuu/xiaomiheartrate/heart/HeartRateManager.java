package com.chayewuu.xiaomiheartrate.heart;

import com.chayewuu.xiaomiheartrate.device.BleDevice;
import com.chayewuu.xiaomiheartrate.util.ModLogger;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 心率管理器（单例）。
 * <p>
 * 作为 BLE 设备层与上层（GUI、HTTP Server、外部 Mod API）之间的协调中枢，
 * 维护当前心率值、当前设备、连接状态，并通过 {@link HeartRateListener} 通知监听器。
 * </p>
 *
 * <p>线程安全：</p>
 * <ul>
 *     <li>监听器列表使用 {@link CopyOnWriteArrayList}，遍历与增删可并发；</li>
 *     <li>当前心率/设备/连接状态使用 {@code volatile} 保证可见性；</li>
 *     <li>内部 {@link HeartRateStorage} 自身线程安全。</li>
 * </ul>
 */
public class HeartRateManager {
    private static volatile HeartRateManager instance;

    private final HeartRateStorage storage;
    private final List<HeartRateListener> listeners;

    private volatile int currentHeartRate = 0;
    private volatile BleDevice currentDevice = null;
    private volatile boolean connected = false;

    /**
     * 私有构造器（单例）。
     */
    private HeartRateManager() {
        this.storage = new HeartRateStorage();
        this.listeners = new CopyOnWriteArrayList<>();
    }

    /**
     * 获取单例实例。
     * <p>双重检查锁定，懒加载。</p>
     *
     * @return 全局唯一实例
     */
    public static HeartRateManager getInstance() {
        if (instance == null) {
            synchronized (HeartRateManager.class) {
                if (instance == null) {
                    instance = new HeartRateManager();
                }
            }
        }
        return instance;
    }

    /**
     * 注册心率监听器。
     *
     * @param listener 监听器
     */
    public void addListener(HeartRateListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * 移除心率监听器。
     *
     * @param listener 监听器
     */
    public void removeListener(HeartRateListener listener) {
        listeners.remove(listener);
    }

    /**
     * 更新当前心率值。
     * <p>存入历史存储，并通知所有监听器。</p>
     *
     * @param heartRate 新心率值（BPM）
     */
    public void updateHeartRate(int heartRate) {
        long timestamp = System.currentTimeMillis();
        this.currentHeartRate = heartRate;
        storage.add(heartRate, timestamp);
        for (HeartRateListener listener : listeners) {
            try {
                listener.onHeartRateChanged(heartRate, timestamp);
            } catch (Exception e) {
                ModLogger.error("心率监听器回调异常: " + listener, e);
            }
        }
    }

    /**
     * 更新设备连接状态。
     * <p>通知所有监听器设备连接/断开事件。</p>
     *
     * @param device    目标设备
     * @param connected {@code true} 表示已连接，{@code false} 表示已断开
     */
    public void setDevice(BleDevice device, boolean connected) {
        this.currentDevice = device;
        boolean wasConnected = this.connected;
        this.connected = connected;
        for (HeartRateListener listener : listeners) {
            try {
                if (connected && !wasConnected) {
                    listener.onDeviceConnected(device);
                } else if (!connected && wasConnected) {
                    listener.onDeviceDisconnected(device);
                }
            } catch (Exception e) {
                ModLogger.error("设备状态监听器回调异常: " + listener, e);
            }
        }
    }

    /**
     * 获取当前心率值。
     *
     * @return 当前心率（BPM），无数据时为 0
     */
    public int getCurrentHeartRate() {
        return currentHeartRate;
    }

    /**
     * 获取当前设备。
     *
     * @return 当前设备，无设备时为 {@code null}
     */
    public BleDevice getCurrentDevice() {
        return currentDevice;
    }

    /**
     * 查询是否已连接设备。
     *
     * @return {@code true} 表示已连接
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * 获取心率历史存储。
     *
     * @return 存储实例
     */
    public HeartRateStorage getStorage() {
        return storage;
    }
}
