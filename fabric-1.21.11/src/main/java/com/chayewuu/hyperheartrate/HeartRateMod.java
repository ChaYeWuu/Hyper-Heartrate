package com.chayewuu.hyperheartrate;

import com.chayewuu.hyperheartrate.config.ConfigManager;
import com.chayewuu.hyperheartrate.config.ModConfig;
import com.chayewuu.hyperheartrate.device.DeviceManagerHolder;
import com.chayewuu.hyperheartrate.device.windows.BleProcessManager;
import com.chayewuu.hyperheartrate.network.HttpServerManager;
import com.chayewuu.hyperheartrate.network.MultiplayerNetworking;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 心率监测 Mod 主入口类。
 * <p>
 * 实现 {@link ModInitializer}，在游戏启动时初始化通用逻辑。
 * 客户端特定逻辑由 {@link HeartRateModClient} 处理。
 * </p>
 *
 * <p>初始化流程：</p>
 * <ol>
 *     <li>加载配置（{@link ConfigManager#load()}）；</li>
 *     <li>启动 HTTP Server（{@link HttpServerManager#start()}），后台线程运行，不阻塞主线程；</li>
 *     <li>注册 JVM 关闭钩子，确保 Mod 卸载/JVM 退出时关闭 HTTP Server 与配置执行器。</li>
 * </ol>
 *
 * <p>Mod ID: {@value #MOD_ID}</p>
 */
public class HeartRateMod implements ModInitializer {
    /** Mod ID，用于注册表、资源路径等 */
    public static final String MOD_ID = "hyper-heartrate";

    /** 统一日志器，前缀 [HeartRateMod] */
    public static final Logger LOGGER = LoggerFactory.getLogger("HeartRateMod");

    /** Mod 版本号 */
    public static final String MOD_VERSION = "1.3.0";

    /** 关闭钩子是否已注册（避免重复注册） */
    private static volatile boolean shutdownHookRegistered = false;

    @Override
    public void onInitialize() {
        LOGGER.info("[HeartRateMod] 心率监测 Mod 正在初始化...");

        // 最早期清理上次残留的 ble-tool 进程（游戏崩溃后 .NET Host 可能残留）
        BleProcessManager.init();

        // 加载配置（懒加载兜底，但此处显式触发一次：文件不存在时自动创建默认文件）
        ConfigManager.load();
        ModConfig config = ConfigManager.getConfig();
        LOGGER.info("[HeartRateMod] 配置加载完成，HTTP 端口: {}, 自动重连: {}",
                config.getHttpPort(), config.isAutoReconnect());

        // 启动 HTTP Server（仅当配置启用 HTTP API 时启动）
        // 后台线程，HttpServer 自带 executor，不阻塞主线程
        if (config.isHttpApiEnabled()) {
            HttpServerManager.getInstance().start();
        } else {
            LOGGER.info("[HeartRateMod] HTTP API 已禁用，跳过启动 HTTP Server");
        }

        // 注册 JVM 关闭钩子，确保卸载时关闭 HTTP Server 与配置执行器
        registerShutdownHook();

        // 注册联机心率同步：payload 类型 + 服务端 C2S 接收器
        // 客户端 S2C 接收器与发送任务在 HeartRateModClient 中注册
        MultiplayerNetworking.register();
        MultiplayerNetworking.registerServerReceivers();

        LOGGER.info("[HeartRateMod] 初始化完成，版本: {}", MOD_VERSION);
    }

    /**
     * 注册 JVM 关闭钩子。
     * <p>在 JVM 退出时关闭 HTTP Server、配置执行器等资源，避免端口占用与数据丢失。
     * 仅注册一次（通过 {@link #shutdownHookRegistered} 标志保证）。</p>
     */
    private static synchronized void registerShutdownHook() {
        if (shutdownHookRegistered) {
            return;
        }
        shutdownHookRegistered = true;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("[HeartRateMod] JVM 关闭，正在清理资源...");
            // 必须先关闭 DeviceManager，强制销毁所有 ble-tool 子进程，
            // 否则 .NET Host 进程会残留，导致下次启动连接失败
            try {
                DeviceManagerHolder.shutdown();
            } catch (Throwable t) {
                LOGGER.warn("[HeartRateMod] 关闭设备管理器异常", t);
            }
            // 兜底：杀掉所有已注册的 ble-tool 进程树（taskkill /T /F），
            // 确保 .NET Host 不残留。即使 DeviceManagerHolder.shutdown 已处理，
            // 这里也作为最终保障。
            try {
                BleProcessManager.shutdownAll();
            } catch (Throwable t) {
                LOGGER.warn("[HeartRateMod] 清理 BLE 进程异常", t);
            }
            try {
                HttpServerManager.getInstance().stop();
            } catch (Throwable t) {
                LOGGER.warn("[HeartRateMod] 关闭 HTTP Server 异常", t);
            }
            try {
                ConfigManager.shutdown();
            } catch (Throwable t) {
                LOGGER.warn("[HeartRateMod] 关闭配置执行器异常", t);
            }
        }, "HeartRateMod-ShutdownHook"));
    }
}
