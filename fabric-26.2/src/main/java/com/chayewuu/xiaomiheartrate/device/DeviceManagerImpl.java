package com.chayewuu.xiaomiheartrate.device;

import com.chayewuu.xiaomiheartrate.config.ConfigManager;
import com.chayewuu.xiaomiheartrate.config.ModConfig;
import com.chayewuu.xiaomiheartrate.device.windows.DotnetBleConnector;
import com.chayewuu.xiaomiheartrate.device.windows.DotnetBleScanner;
import com.chayewuu.xiaomiheartrate.device.windows.WindowsBleAdapter;
import com.chayewuu.xiaomiheartrate.heart.HeartRateManager;
import com.chayewuu.xiaomiheartrate.util.ModLogger;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 设备管理器默认实现。
 * <p>
 * 上层（GUI / 心率管理器）与 BLE 子系统之间的统一门面。内部组合
 * {@link BleAdapter}、{@link BleScanner}、{@link BleConnector} 完成实际工作，
 * 并在扫描阶段通过 {@link DeviceFilter} 过滤出受支持的小米/Redmi 设备。
 * </p>
 *
 * <p><b>状态机：</b></p>
 * <pre>
 * DISCONNECTED → SCANNING → CONNECTING → CONNECTED → DISCONNECTED
 *                                             ↓
 *                                       RECONNECTING → CONNECTING → CONNECTED
 * </pre>
 *
 * <p><b>线程模型：</b>所有 BLE 操作（扫描、连接、断开、重连）均在后台线程执行，
 * 上层回调方法（{@link ScanCallback} / {@link ConnectionCallback}）
 * 可能在 BLE 后台线程被调用，若需更新 UI 必须 marshal 回主线程。</p>
 *
 * <p><b>线程安全：</b></p>
 * <ul>
 *     <li>{@link #currentDevice}、{@link #connectionState}、{@link #userCallback}
 *         为 {@code volatile}，保证多线程可见性；</li>
 *     <li>连接结果同步使用 {@link AtomicReference}&lt;{@link CountDownLatch}&gt;；</li>
 *     <li>已上报设备地址集合使用 {@link ConcurrentHashMap#newKeySet()}。</li>
 * </ul>
 *
 * <p><b>自动重连：</b>从 {@link ModConfig} 读取 {@code autoReconnect} 与
 * {@code reconnectDelayMs}（默认 5000ms）。设备意外掉线时（非用户主动断开），
 * 状态转为 {@link ConnectionState#RECONNECTING}，等待 {@code reconnectDelayMs} 后
 * 尝试重连，失败则重复，直到成功或用户手动断开。重连在独立后台线程执行。</p>
 */
public class DeviceManagerImpl implements DeviceManager {
    /** 日志前缀 */
    private static final String LOG_TAG = "[DeviceManager]";

    /** 默认重连间隔（毫秒），配置读取失败时使用 */
    private static final int DEFAULT_RECONNECT_DELAY_MS = 5000;

    /** 平台适配器（工厂） */
    private final BleAdapter adapter;

    /** 扫描器实例 */
    private final BleScanner scanner;

    /** 连接器实例 */
    private final BleConnector connector;

    /** 心率通知处理器（接收 BLE 通知 → 解析路由 → 更新 HeartRateManager） */
    private final HeartRateNotificationHandler notificationHandler;

    /** BLE 操作后台线程池（断开等操作） */
    private final ExecutorService bleExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "HeartRateMod-BLE-Operations");
        t.setDaemon(true);
        return t;
    });

    /** 自动重连后台线程池（独立于连接器，避免相互阻塞） */
    private final ExecutorService reconnectExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "HeartRateMod-BLE-Reconnect");
        t.setDaemon(true);
        return t;
    });

    /** 当前已连接（或正在连接）的设备，{@code volatile} 保证多线程可见性 */
    private volatile BleDevice currentDevice;

    /** 当前连接状态，{@code volatile} 保证多线程可见性 */
    private volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;

    /** 用户传入的连接回调（断开/重连时透传事件） */
    private volatile ConnectionCallback userCallback;

    /** 自动重连开关 */
    private volatile boolean autoReconnect = false;

    /** 自动重连间隔（毫秒） */
    private volatile int reconnectDelayMs = DEFAULT_RECONNECT_DELAY_MS;

    /** 是否正在重连中（防止重复触发） */
    private volatile boolean reconnecting = false;

    /** 是否用户主动断开（用户主动断开不触发自动重连） */
    private volatile boolean userInitiatedDisconnect = false;

    /** 连接结果同步锁存器引用，供重连线程等待连接结果 */
    private final AtomicReference<CountDownLatch> connectLatchRef = new AtomicReference<>();

    /** 最近一次上报的设备地址集合，用于扫描去重 */
    private final Set<String> reportedAddresses = ConcurrentHashMap.newKeySet();

    /**
     * 内部状态回调，包装所有 {@link BleConnector#connect} 的回调。
     * <p>统一处理状态流转、{@link HeartRateManager} 通知与自动重连触发。</p>
     */
    private final ConnectionCallback stateCallback = new ConnectionCallback() {
        @Override
        public void onConnected(BleDevice connectedDevice) {
            connectionState = ConnectionState.CONNECTED;
            currentDevice = connectedDevice;
            ModLogger.info("{} 设备已连接: {}", LOG_TAG, connectedDevice.getAddress());
            // 通知心率管理器
            HeartRateManager.getInstance().setDevice(connectedDevice, true);
            // 设置通知处理器关联设备，并启用 0x2A37 心率特征值通知
            notificationHandler.setDevice(connectedDevice);
            enableHeartRateNotifications();
            // 释放重连等待线程
            CountDownLatch latch = connectLatchRef.getAndSet(null);
            if (latch != null) {
                latch.countDown();
            }
            // 透传用户回调
            ConnectionCallback cb = userCallback;
            if (cb != null) {
                try {
                    cb.onConnected(connectedDevice);
                } catch (Throwable t) {
                    ModLogger.error("{} 用户 onConnected 回调异常", t, LOG_TAG);
                }
            }
        }

        @Override
        public void onDisconnected(BleDevice disconnectedDevice) {
            connectionState = ConnectionState.DISCONNECTED;
            ModLogger.info("{} 设备已断开: {}", LOG_TAG,
                    disconnectedDevice == null ? "未知" : disconnectedDevice.getAddress());
            // 通知心率管理器
            HeartRateManager.getInstance().setDevice(null, false);
            // 清空通知处理器的设备引用，避免后续通知误路由
            notificationHandler.setDevice(null);
            // 释放重连等待线程
            CountDownLatch latch = connectLatchRef.getAndSet(null);
            if (latch != null) {
                latch.countDown();
            }
            // 透传用户回调
            ConnectionCallback cb = userCallback;
            if (cb != null) {
                try {
                    cb.onDisconnected(disconnectedDevice);
                } catch (Throwable t) {
                    ModLogger.error("{} 用户 onDisconnected 回调异常", t, LOG_TAG);
                }
            }
            // 触发自动重连（非用户主动断开且未在重连中）
            if (autoReconnect && !userInitiatedDisconnect && !reconnecting) {
                triggerReconnect();
            }
        }

        @Override
        public void onError(String message, Throwable cause) {
            connectionState = ConnectionState.DISCONNECTED;
            ModLogger.error("{} 连接错误: {}", LOG_TAG, message);
            // 释放重连等待线程
            CountDownLatch latch = connectLatchRef.getAndSet(null);
            if (latch != null) {
                latch.countDown();
            }
            // 透传用户回调
            ConnectionCallback cb = userCallback;
            if (cb != null) {
                try {
                    cb.onError(message, cause);
                } catch (Throwable t) {
                    ModLogger.error("{} 用户 onError 回调异常", t, LOG_TAG);
                }
            }
            // 触发自动重连（非用户主动断开且未在重连中）
            if (autoReconnect && !userInitiatedDisconnect && !reconnecting) {
                triggerReconnect();
            }
        }
    };

    /**
     * 默认构造器，使用 {@link WindowsBleAdapter} 作为平台适配器。
     */
    public DeviceManagerImpl() {
        this(new WindowsBleAdapter());
    }

    /**
     * 构造器，允许注入自定义 {@link BleAdapter}（便于测试与多平台扩展）。
     *
     * @param adapter 平台适配器
     */
    public DeviceManagerImpl(BleAdapter adapter) {
        this.adapter = adapter;
        this.scanner = adapter.createScanner();
        this.connector = adapter.createConnector();
        this.notificationHandler = new HeartRateNotificationHandler();
        loadReconnectConfig();
    }

    /**
     * 从 {@link ModConfig} 加载自动重连配置。
     */
    private void loadReconnectConfig() {
        try {
            ModConfig config = ConfigManager.getConfig();
            this.autoReconnect = config.isAutoReconnect();
            this.reconnectDelayMs = config.getReconnectDelayMs();
            ModLogger.info("{} 自动重连配置: enabled={}, delay={}ms", LOG_TAG, autoReconnect, reconnectDelayMs);
        } catch (Throwable t) {
            ModLogger.warn("{} 加载重连配置失败，使用默认值: enabled=false, delay={}ms", LOG_TAG, DEFAULT_RECONNECT_DELAY_MS);
            this.autoReconnect = false;
            this.reconnectDelayMs = DEFAULT_RECONNECT_DELAY_MS;
        }
    }

    /**
     * 刷新自动重连配置（用户在设置界面修改配置后调用）。
     */
    public void refreshConfig() {
        loadReconnectConfig();
        // 若自动重连已关闭，取消正在进行的重连
        if (!autoReconnect) {
            cancelReconnect();
        }
    }

    @Override
    public void startScan(ScanCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("扫描回调不能为 null");
        }
        if (!adapter.isSupported()) {
            ModLogger.warn("{} 当前平台不支持 BLE，无法启动扫描", LOG_TAG);
            return;
        }
        // 清空去重集合，允许新一轮扫描重新上报设备
        reportedAddresses.clear();
        connectionState = ConnectionState.SCANNING;
        ModLogger.info("{} 启动设备扫描", LOG_TAG);

        // 包装回调：仅上报受支持且未重复的设备
        ScanCallback filteredCallback = device -> {
            if (device == null) {
                return;
            }
            if (!DeviceFilter.isSupportedDevice(device)) {
                ModLogger.debug("{} 过滤掉非受支持设备: {}", LOG_TAG, device.getName());
                return;
            }
            String address = device.getAddress();
            if (address != null && !reportedAddresses.add(address)) {
                // 已上报过的设备，跳过（避免重复刷新列表）
                return;
            }
            ModLogger.info("{} 发现受支持设备: {} ({})", LOG_TAG, device.getName(), address);
            try {
                callback.onDeviceFound(device);
            } catch (Throwable t) {
                ModLogger.error("{} 上层扫描回调异常", t, LOG_TAG);
            }
        };

        scanner.startScan(filteredCallback);
    }

    @Override
    public void stopScan() {
        ModLogger.info("{} 停止设备扫描", LOG_TAG);
        scanner.stopScan();
        if (connectionState == ConnectionState.SCANNING) {
            connectionState = ConnectionState.DISCONNECTED;
        }
    }

    @Override
    public void connect(BleDevice device, ConnectionCallback callback) {
        if (device == null) {
            throw new IllegalArgumentException("目标设备不能为 null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("连接回调不能为 null");
        }
        ModLogger.info("{} 请求连接设备: {} ({})", LOG_TAG, device.getName(), device.getAddress());
        // 停止扫描，避免连接与扫描争用蓝牙资源
        stopScan();
        // 取消可能正在进行的重连
        userInitiatedDisconnect = false;
        cancelReconnect();
        // 更新状态
        currentDevice = device;
        userCallback = callback;
        connectionState = ConnectionState.CONNECTING;
        // 发起连接（连接器内部在后台线程执行）
        connector.connect(device, stateCallback);
    }

    @Override
    public void disconnect() {
        ModLogger.info("{} 请求断开当前设备", LOG_TAG);
        // 标记用户主动断开，阻止自动重连
        userInitiatedDisconnect = true;
        // 取消正在进行的重连
        cancelReconnect();
        // 立即更新状态，GUI 可即时响应
        final BleDevice device = currentDevice;
        final ConnectionCallback cb = userCallback;
        connectionState = ConnectionState.DISCONNECTED;
        currentDevice = null;
        userCallback = null;
        // 通知心率管理器
        HeartRateManager.getInstance().setDevice(null, false);
        // 清空通知处理器的设备引用
        notificationHandler.setDevice(null);
        // 在后台线程执行实际 BLE 断开操作，避免阻塞 GUI
        bleExecutor.submit(() -> {
            try {
                connector.disconnect();
            } catch (Throwable t) {
                ModLogger.error("{} BLE 断开操作异常", t, LOG_TAG);
            }
        });
        // 透传用户回调
        if (cb != null) {
            try {
                cb.onDisconnected(device);
            } catch (Throwable t) {
                ModLogger.error("{} 用户断开回调异常", t, LOG_TAG);
            }
        }
    }

    @Override
    public BleDevice getCurrentDevice() {
        return currentDevice;
    }

    @Override
    public ConnectionState getConnectionState() {
        return connectionState;
    }

    @Override
    public void setAutoReconnect(boolean enabled) {
        this.autoReconnect = enabled;
        ModLogger.info("{} 自动重连已{}", LOG_TAG, enabled ? "开启" : "关闭");
        if (!enabled) {
            cancelReconnect();
        }
    }

    @Override
    public String getScanError() {
        if (scanner instanceof DotnetBleScanner dotnetScanner) {
            return dotnetScanner.getLastError();
        }
        return null;
    }

    /**
     * 查询自动重连开关状态。
     *
     * @return {@code true} 表示自动重连已开启
     */
    public boolean isAutoReconnect() {
        return autoReconnect;
    }

    /**
     * 获取自动重连间隔。
     *
     * @return 重连间隔（毫秒）
     */
    public int getReconnectDelayMs() {
        return reconnectDelayMs;
    }

    /**
     * 触发自动重连（后台线程执行）。
     * <p>设备意外掉线时调用，循环等待 {@link #reconnectDelayMs} 后尝试重连，
     * 直到成功、用户手动断开或自动重连被关闭。</p>
     */
    private void triggerReconnect() {
        if (reconnecting) {
            ModLogger.debug("{} 已在重连中，跳过重复触发", LOG_TAG);
            return;
        }
        final BleDevice device = currentDevice;
        if (device == null) {
            ModLogger.warn("{} 无设备信息，无法重连", LOG_TAG);
            return;
        }
        reconnecting = true;
        connectionState = ConnectionState.RECONNECTING;
        ModLogger.info("{} 启动自动重连（设备: {}）", LOG_TAG, device.getAddress());

        reconnectExecutor.submit(() -> {
            try {
                while (reconnecting && autoReconnect && !userInitiatedDisconnect) {
                    // 等待重连间隔
                    ModLogger.info("{} 等待 {}ms 后重连: {}", LOG_TAG, reconnectDelayMs, device.getAddress());
                    if (!sleepInterruptibly(reconnectDelayMs)) {
                        break;
                    }
                    if (!reconnecting || userInitiatedDisconnect || !autoReconnect) {
                        break;
                    }
                    // 尝试重连
                    ModLogger.info("{} 尝试重连设备: {}", LOG_TAG, device.getAddress());
                    connectionState = ConnectionState.CONNECTING;
                    // 设置锁存器，等待连接结果
                    CountDownLatch latch = new CountDownLatch(1);
                    connectLatchRef.set(latch);
                    // 调用连接器（内部后台线程执行）
                    connector.connect(device, stateCallback);
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        ModLogger.info("{} 重连等待被中断", LOG_TAG);
                        break;
                    }
                    // 检查连接结果
                    if (connectionState == ConnectionState.CONNECTED) {
                        ModLogger.info("{} 重连成功: {}", LOG_TAG, device.getAddress());
                        reconnecting = false;
                        return;
                    }
                    ModLogger.warn("{} 重连失败，将在 {}ms 后重试", LOG_TAG, reconnectDelayMs);
                }
            } finally {
                reconnecting = false;
                // 若退出时仍为 RECONNECTING/CONNECTING，重置为 DISCONNECTED
                if (connectionState == ConnectionState.RECONNECTING
                        || connectionState == ConnectionState.CONNECTING) {
                    connectionState = ConnectionState.DISCONNECTED;
                }
                ModLogger.info("{} 自动重连已停止", LOG_TAG);
            }
        });
    }

    /**
     * 取消正在进行的自动重连。
     * <p>释放重连等待线程，设置 {@link #reconnecting} 为 {@code false}。</p>
     */
    private void cancelReconnect() {
        reconnecting = false;
        CountDownLatch latch = connectLatchRef.getAndSet(null);
        if (latch != null) {
            latch.countDown();
        }
    }

    /**
     * 可中断的睡眠（重连等待用）。
     *
     * @param millis 睡眠毫秒数
     * @return {@code true} 表示正常睡眠结束；{@code false} 表示被中断（应退出重连循环）
     */
    private boolean sleepInterruptibly(long millis) {
        long end = System.currentTimeMillis() + millis;
        while (reconnecting && !userInitiatedDisconnect && autoReconnect) {
            long now = System.currentTimeMillis();
            if (now >= end) {
                return true;
            }
            try {
                Thread.sleep(Math.min(100L, end - now));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * 关闭设备管理器，释放底层扫描器与连接器资源。
     * <p>由 Mod 卸载逻辑调用。</p>
     */
    public void shutdown() {
        ModLogger.info("{} 关闭设备管理器", LOG_TAG);
        // 取消重连
        userInitiatedDisconnect = true;
        cancelReconnect();
        try {
            stopScan();
        } catch (Throwable t) {
            ModLogger.warn("{} 关闭扫描器异常", t, LOG_TAG);
        }
        // 同步直接调用 connector.disconnect()，不走 bleExecutor 异步提交
        // （异步任务可能被随后的 shutdownNow 中断，导致 ble-tool 进程残留）
        try {
            connector.disconnect();
        } catch (Throwable t) {
            ModLogger.warn("{} 关闭连接器异常", t, LOG_TAG);
        }
        // 释放 Windows 实现的资源（若为 Windows 实现）
        if (scanner instanceof com.chayewuu.xiaomiheartrate.device.windows.WindowsBleScanner windowsScanner) {
            windowsScanner.shutdown();
        }
        if (connector instanceof com.chayewuu.xiaomiheartrate.device.windows.WindowsBleConnector windowsConnector) {
            windowsConnector.shutdown();
        }
        // 关闭线程池
        bleExecutor.shutdownNow();
        reconnectExecutor.shutdownNow();
    }

    /**
     * 返回平台是否支持 BLE（委托给 {@link BleAdapter#isSupported()}）。
     *
     * @return {@code true} 表示当前平台支持 BLE
     */
    public boolean isSupported() {
        return adapter.isSupported();
    }

    /**
     * 清空已上报设备去重集合，允许下一次扫描重新上报所有设备。
     * <p>GUI 在重新开始扫描时可调用。</p>
     */
    public void clearReportedDevices() {
        reportedAddresses.clear();
    }

    /**
     * 启用心率特征值（0x2A37）通知订阅。
     * <p>在设备连接成功后调用：通过 {@link BleConnector#discoverServices()}
     * 获取特征值列表，查找标准 Heart Rate Measurement 特征值（UUID 含 {@code 2a37}），
     * 并将 {@link #notificationHandler} 注册为通知回调。</p>
     *
     * <p>底层 GATT 通知注册尚未完全实现时（如 WindowsBleConnector 骨架阶段），
     * 该方法仅保存回调引用，不会抛出异常。</p>
     */
    private void enableHeartRateNotifications() {
        try {
            // DotnetBleConnector 内部由 ble-tool 完成服务发现与通知订阅，
            // 这里直接设置通知回调，ble-tool 收到心率数据后转发到 notificationHandler
            if (connector instanceof DotnetBleConnector dotnetConnector) {
                dotnetConnector.setHeartRateNotificationCallback(notificationHandler);
                ModLogger.info("{} 已设置 DotnetBleConnector 心率通知回调", LOG_TAG);
                return;
            }

            // 传统路径：通过特征值订阅
            List<BleCharacteristic> characteristics = connector.discoverServices();
            if (characteristics == null || characteristics.isEmpty()) {
                ModLogger.warn("{} 未发现任何特征值，跳过心率通知订阅", LOG_TAG);
                return;
            }
            for (BleCharacteristic ch : characteristics) {
                String uuid = ch.getUuid();
                if (uuid != null && uuid.toLowerCase(Locale.ROOT).endsWith("2a37")) {
                    ch.enableNotifications(notificationHandler);
                    ModLogger.info("{} 已订阅心率特征值通知: {}", LOG_TAG, uuid);
                    return;
                }
            }
            ModLogger.warn("{} 未找到心率测量特征值（0x2A37），无法订阅通知", LOG_TAG);
        } catch (Throwable t) {
            ModLogger.error("{} 启用心率通知异常", t, LOG_TAG);
        }
    }

    /**
     * 获取心率通知处理器（供外部测试或扩展使用）。
     *
     * @return 通知处理器实例
     */
    public HeartRateNotificationHandler getNotificationHandler() {
        return notificationHandler;
    }
}
