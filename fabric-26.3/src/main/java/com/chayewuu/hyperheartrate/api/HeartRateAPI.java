package com.chayewuu.hyperheartrate.api;

import com.chayewuu.hyperheartrate.device.BleDevice;
import com.chayewuu.hyperheartrate.heart.HeartRateListener;
import com.chayewuu.hyperheartrate.heart.HeartRateManager;
import com.chayewuu.hyperheartrate.heart.HeartRateRecord;

import java.util.List;

/**
 * 心率监测 Mod 对外 API。
 * <p>
 * 供其它 Fabric Mod 调用，获取实时心率数据。所有方法均为静态方法，
 * 通过 {@link HeartRateManager} 单例实现，线程安全。
 * </p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 1. 获取当前心率
 * int heartRate = HeartRateAPI.getHeartRate();
 *
 * // 2. 检查设备是否已连接
 * if (HeartRateAPI.isConnected()) {
 *     BleDevice device = HeartRateAPI.getCurrentDevice();
 *     System.out.println("设备: " + device.getName());
 * }
 *
 * // 3. 监听心率变化
 * HeartRateAPI.addListener(new HeartRateListener() {
 *     @Override
 *     public void onHeartRateChanged(int heartRate, long timestamp) {
 *         // 心率越高怪物越强
 *         if (heartRate > 140) {
 *             // 触发警报
 *         }
 *         // 心率低于 50 获得特殊 Buff
 *         if (heartRate < 50) {
 *             // 给予 Buff
 *         }
 *     }
 *
 *     @Override
 *     public void onDeviceConnected(BleDevice device) {
 *         System.out.println("设备已连接: " + device.getName());
 *     }
 *
 *     @Override
 *     public void onDeviceDisconnected(BleDevice device) {
 *         System.out.println("设备已断开");
 *     }
 * });
 *
 * // 4. 获取心率历史
 * List<HeartRateRecord> history = HeartRateAPI.getHistory(60);
 * for (HeartRateRecord record : history) {
 *     System.out.println("心率: " + record.heartRate() + " 时间: " + record.timestamp());
 * }
 * }</pre>
 *
 * <h2>玩法示例</h2>
 * <ul>
 *   <li>心率越高怪物越强（监听 onHeartRateChanged，动态调整怪物属性）</li>
 *   <li>心率超过 140 自动播放警报（在 onHeartRateChanged 中播放音效）</li>
 *   <li>心率低于 50 获得特殊 Buff（给予玩家药水效果）</li>
 *   <li>根据心率调整游戏音效、粒子或环境效果</li>
 * </ul>
 */
public final class HeartRateAPI {
    /** 私有构造器，禁止实例化 */
    private HeartRateAPI() {
    }

    /**
     * 获取当前心率值。
     *
     * @return 当前心率（BPM），无数据或未连接时为 0
     */
    public static int getHeartRate() {
        return HeartRateManager.getInstance().getCurrentHeartRate();
    }

    /**
     * 查询是否已连接设备。
     *
     * @return {@code true} 表示已连接
     */
    public static boolean isConnected() {
        return HeartRateManager.getInstance().isConnected();
    }

    /**
     * 获取当前已连接设备。
     *
     * @return 当前设备，无设备时为 {@code null}
     */
    public static BleDevice getCurrentDevice() {
        return HeartRateManager.getInstance().getCurrentDevice();
    }

    /**
     * 注册心率监听器。
     *
     * @param listener 监听器
     */
    public static void addListener(HeartRateListener listener) {
        HeartRateManager.getInstance().addListener(listener);
    }

    /**
     * 移除心率监听器。
     *
     * @param listener 监听器
     */
    public static void removeListener(HeartRateListener listener) {
        HeartRateManager.getInstance().removeListener(listener);
    }

    /**
     * 获取最近 {@code count} 条心率历史记录，按时间从旧到新排序。
     *
     * @param count 期望获取的记录数；若大于当前存储量则返回全部
     * @return 历史记录列表（不可变视图）
     */
    public static List<HeartRateRecord> getHistory(int count) {
        return HeartRateManager.getInstance().getStorage().getHistory(count);
    }

    /**
     * 获取最近一次心率记录。
     *
     * @return 最新心率记录；无数据时为 {@code null}
     */
    public static HeartRateRecord getLatestRecord() {
        return HeartRateManager.getInstance().getStorage().getLatestRecord();
    }
}
