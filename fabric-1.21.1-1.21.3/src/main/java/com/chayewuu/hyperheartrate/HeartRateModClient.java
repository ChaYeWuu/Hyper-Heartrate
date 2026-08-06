package com.chayewuu.hyperheartrate;

import com.chayewuu.hyperheartrate.config.ConfigManager;
import com.chayewuu.hyperheartrate.device.DeviceManagerHolder;
import com.chayewuu.hyperheartrate.device.windows.BleProcessManager;
import com.chayewuu.hyperheartrate.gui.HudRenderer;
import com.chayewuu.hyperheartrate.gui.ModKeyBindings;
import com.chayewuu.hyperheartrate.network.MultiplayerNetworking;
import com.chayewuu.hyperheartrate.network.RemoteHeartRateStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;

/**
 * 心率监测 Mod 客户端入口类。
 * <p>
 * 实现 {@link ClientModInitializer}，在客户端启动时初始化客户端特定逻辑。
 * 注册内容：按键绑定（H 键打开主界面）、HUD 渲染（实时心率显示）、
 * 联机心率同步（客户端接收器 + 每秒发送任务）。
 * </p>
 */
@Environment(EnvType.CLIENT)
public class HeartRateModClient implements ClientModInitializer {

    private static final int SEND_INTERVAL_TICKS = 20;
    private int tickCounter = 0;

    @Override
    public void onInitializeClient() {
        HeartRateMod.LOGGER.info("[HeartRateMod] 客户端初始化中...");

        ConfigManager.getConfig();
        ModKeyBindings.register();
        HudRenderer.register();
        MultiplayerNetworking.registerClientReceivers();
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        // 客户端停止时立即释放 BLE 设备，比 JVM 关闭钩子更可靠
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            HeartRateMod.LOGGER.info("[HeartRateMod] 客户端停止，释放 BLE 设备...");
            forceShutdownBle();
        });

        HeartRateMod.LOGGER.info("[HeartRateMod] 客户端初始化完成");
    }

    /** 强制释放 BLE 设备（断开连接 + 杀掉进程树） */
    private static void forceShutdownBle() {
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
        // 兜底：直接杀所有 dotnet.exe 进程树（仅杀本 Mod 启动的）
        try {
            Runtime.getRuntime().exec("taskkill /F /IM dotnet.exe");
        } catch (Throwable ignored) {
        }
    }

    private void onClientTick(MinecraftClient client) {
        tickCounter++;
        if (tickCounter < SEND_INTERVAL_TICKS) return;
        tickCounter = 0;

        try {
            MultiplayerNetworking.sendHeartRateToServer();
        } catch (Throwable t) {
            HeartRateMod.LOGGER.warn("[HeartRateMod] 发送心率失败", t);
        }

        try {
            ClientWorld level = client.world;
            ClientPlayerEntity self = client.player;
            if (level == null || self == null) return;
            java.util.Set<java.util.UUID> online = new java.util.HashSet<>();
            for (var entity : level.getPlayers()) {
                if (entity != self) online.add(entity.getUuid());
            }
            RemoteHeartRateStore.getInstance().retainOnly(online);
        } catch (Throwable t) {
            HeartRateMod.LOGGER.warn("[HeartRateMod] 清理远端心率缓存失败", t);
        }
    }
}