package com.chayewuu.xiaomiheartrate.device;

import com.chayewuu.xiaomiheartrate.config.ConfigManager;
import com.chayewuu.xiaomiheartrate.config.ModConfig;
import com.chayewuu.xiaomiheartrate.device.windows.WindowsBleAdapter;
import com.chayewuu.xiaomiheartrate.util.ModLogger;

/**
 * 设备管理器单例持有者。
 * <p>
 * 进程级单例持有 {@link DeviceManagerImpl} 实例，供 GUI、Mod API、HTTP Server
 * 等模块共享同一设备管理器实例。首次调用 {@link #get()} 时按当前平台创建
 * 适配器（Windows 上为 {@link WindowsBleAdapter}）并构造 {@link DeviceManagerImpl}。
 * </p>
 *
 * <p><b>线程安全：</b>使用双重检查锁定（DCL）保证单例初始化的线程安全；
 * {@link DeviceManagerImpl} 自身为线程安全实现。</p>
 *
 * <p><b>生命周期：</b>Mod 卸载或 JVM 退出时调用 {@link #shutdown()} 释放
 * BLE 资源（扫描器、连接器后台线程池等）。</p>
 */
public final class DeviceManagerHolder {
    /** 日志前缀 */
    private static final String LOG_TAG = "[DeviceManagerHolder]";

    /** 单例实例（volatile 保证多线程可见性） */
    private static volatile DeviceManager instance;

    /** 单例初始化锁 */
    private static final Object LOCK = new Object();

    /** 私有构造器，禁止实例化 */
    private DeviceManagerHolder() {
    }

    /**
     * 获取设备管理器单例实例。
     * <p>首次调用时按平台创建适配器并构造 {@link DeviceManagerImpl}。
     * 后续调用直接返回已存在实例。</p>
     *
     * @return 设备管理器实例
     */
    public static DeviceManager get() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = createDefault();
                }
            }
        }
        return instance;
    }

    /**
     * 创建默认设备管理器实例。
     * <p>当前默认使用 {@link WindowsBleAdapter}（Windows 平台 BLE 实现）。
     * 后续可按平台自动选择适配器。</p>
     *
     * @return 设备管理器实例
     */
    private static DeviceManager createDefault() {
        try {
            // 触发配置加载，确保自动重连等参数已就绪
            ModConfig config = ConfigManager.getConfig();
            ModLogger.info("{} 创建设备管理器（自动重连={}, 重连延迟={}ms）",
                    LOG_TAG, config.isAutoReconnect(), config.getReconnectDelayMs());
            return new DeviceManagerImpl(new WindowsBleAdapter());
        } catch (Throwable t) {
            ModLogger.error("{} 创建设备管理器失败，回退到无操作实现", t, LOG_TAG);
            // 极端情况下回退到无操作实现，避免 Mod 启动失败
            return new NoOpDeviceManager();
        }
    }

    /**
     * 关闭设备管理器，释放底层资源。
     * <p>由 Mod 卸载逻辑或 JVM 关闭钩子调用。</p>
     */
    public static void shutdown() {
        synchronized (LOCK) {
            if (instance != null) {
                try {
                    if (instance instanceof DeviceManagerImpl impl) {
                        impl.shutdown();
                    }
                } catch (Throwable t) {
                    ModLogger.warn("{} 关闭设备管理器异常", t, LOG_TAG);
                }
                instance = null;
            }
        }
    }

    /**
     * 无操作设备管理器（兜底实现）。
     * <p>当真实 BLE 适配器创建失败时使用，所有操作均为空操作或返回失败状态，
     * 保证 Mod 仍可加载，GUI 可正常显示（仅无法连接设备）。</p>
     */
    private static final class NoOpDeviceManager implements DeviceManager {
        @Override
        public void startScan(ScanCallback callback) {
            ModLogger.warn("{} 当前平台无可用 BLE 适配器，扫描请求被忽略", LOG_TAG);
        }

        @Override
        public void stopScan() {
            // 无操作
        }

        @Override
        public void connect(BleDevice device, ConnectionCallback callback) {
            if (callback != null) {
                callback.onError("当前平台无可用 BLE 适配器", null);
            }
        }

        @Override
        public void disconnect() {
            // 无操作
        }

        @Override
        public BleDevice getCurrentDevice() {
            return null;
        }

        @Override
        public ConnectionState getConnectionState() {
            return ConnectionState.DISCONNECTED;
        }

        @Override
        public void setAutoReconnect(boolean enabled) {
            // 无操作
        }

        @Override
        public String getScanError() {
            return "BLE 不可用（平台不支持或初始化失败）";
        }
    }
}
