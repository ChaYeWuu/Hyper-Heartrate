package com.chayewuu.xiaomiheartrate;

import com.chayewuu.xiaomiheartrate.config.ConfigManager;
import com.chayewuu.xiaomiheartrate.device.DeviceManagerHolder;
import com.chayewuu.xiaomiheartrate.device.windows.BleProcessManager;
import com.chayewuu.xiaomiheartrate.gui.HudRenderer;
import com.chayewuu.xiaomiheartrate.gui.ModKeyBindings;
import com.chayewuu.xiaomiheartrate.network.HttpServerManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

/**
 * 心率监测 Mod 客户端入口类。
 * <p>
 * 实现 {@link ClientModInitializer}，在客户端启动时初始化客户端特定逻辑。
 * 注册内容：按键绑定（H 键打开主界面）、HUD 渲染（实时心率显示）、
 * 客户端停止时主动清理 BLE 资源（先于 JVM 关闭钩子，确保线程/进程及时释放）。
 * </p>
 */
@Environment(EnvType.CLIENT)
public class HeartRateModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HeartRateMod.LOGGER.info("[HeartRateMod] 客户端初始化中...");

        // 加载配置（懒加载触发，确保后续读取一致）
        ConfigManager.getConfig();

        // 注册按键绑定（H 键打开主界面）
        ModKeyBindings.register();

        // 注册 HUD 渲染（实时心率显示）
        HudRenderer.register();

        // 注册客户端停止事件：在 Minecraft 关闭时主动停止 BLE 线程/进程
        // 先于 JVM 关闭钩子执行，避免 BLE 后台线程持有资源导致关游戏卡顿
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            HeartRateMod.LOGGER.info("[HeartRateMod] 客户端正在关闭，清理 BLE 资源...");
            try {
                DeviceManagerHolder.shutdown();
            } catch (Throwable t) {
                HeartRateMod.LOGGER.warn("[HeartRateMod] 关闭设备管理器异常", t);
            }
            try {
                BleProcessManager.shutdownAll();
            } catch (Throwable t) {
                HeartRateMod.LOGGER.warn("[HeartRateMod] 清理 BLE 进程异常", t);
            }
            try {
                HttpServerManager.getInstance().stop();
            } catch (Throwable t) {
                HeartRateMod.LOGGER.warn("[HeartRateMod] 关闭 HTTP Server 异常", t);
            }
        });

        HeartRateMod.LOGGER.info("[HeartRateMod] 客户端初始化完成");
    }
}
