package com.chayewuu.xiaomiheartrate.heart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 心率历史存储（环形缓冲区）。
 * <p>
 * 使用定长环形缓冲区保存最近若干次心率采样，避免无限增长内存。
 * 默认容量 600（约对应 10 分钟 @ 1Hz 采样），超出容量后新数据覆盖最旧数据。
 * </p>
 *
 * <p>线程安全：所有读写方法均 {@code synchronized}，可被 BLE 接收线程、
 * HTTP Server 线程、GUI 渲染线程并发访问。</p>
 */
public class HeartRateStorage {
    /** 默认容量，约 10 分钟 @ 1Hz */
    public static final int DEFAULT_CAPACITY = 600;

    private final int capacity;
    private final int[] heartRates;
    private final long[] timestamps;
    private int head = 0;
    private int size = 0;

    /**
     * 使用默认容量（600）构造存储。
     */
    public HeartRateStorage() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * 指定容量构造存储。
     *
     * @param capacity 最大记录数，必须为正数
     */
    public HeartRateStorage(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity 必须为正数: " + capacity);
        }
        this.capacity = capacity;
        this.heartRates = new int[capacity];
        this.timestamps = new long[capacity];
    }

    /**
     * 添加一条心率记录。
     *
     * @param heartRate 心率值（BPM）
     * @param timestamp 采样时间戳（毫秒）
     */
    public synchronized void add(int heartRate, long timestamp) {
        heartRates[head] = heartRate;
        timestamps[head] = timestamp;
        head = (head + 1) % capacity;
        if (size < capacity) {
            size++;
        }
    }

    /**
     * 获取最近 {@code count} 条历史记录，按时间从旧到新排序。
     *
     * @param count 期望获取的记录数；若大于当前存储量则返回全部
     * @return 历史记录列表（不可变视图）
     */
    public synchronized List<HeartRateRecord> getHistory(int count) {
        if (size == 0 || count <= 0) {
            return Collections.emptyList();
        }
        int n = Math.min(count, size);
        List<HeartRateRecord> result = new ArrayList<>(n);
        // 返回最近 n 条记录，按时间从旧到新排列
        // 最旧记录下标 = (head - n + capacity) % capacity
        int oldestStart = (head - n + capacity) % capacity;
        for (int i = 0; i < n; i++) {
            int idx = (oldestStart + i) % capacity;
            result.add(new HeartRateRecord(heartRates[idx], timestamps[idx]));
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 获取最新一条心率值。
     *
     * @return 最新心率值；无数据时返回 0
     */
    public synchronized int getLatest() {
        if (size == 0) {
            return 0;
        }
        int latestIndex = (head - 1 + capacity) % capacity;
        return heartRates[latestIndex];
    }

    /**
     * 获取最近一次心率记录。
     *
     * @return 最新心率记录；无数据时返回 {@code null}
     */
    public synchronized HeartRateRecord getLatestRecord() {
        if (size == 0) {
            return null;
        }
        int latestIndex = (head - 1 + capacity) % capacity;
        return new HeartRateRecord(heartRates[latestIndex], timestamps[latestIndex]);
    }

    /**
     * 清空所有历史记录。
     */
    public synchronized void clear() {
        head = 0;
        size = 0;
    }

    /**
     * 获取当前已存储的记录数。
     *
     * @return 当前记录数
     */
    public synchronized int size() {
        return size;
    }

    /**
     * 获取容量上限。
     *
     * @return 容量
     */
    public int getCapacity() {
        return capacity;
    }
}
