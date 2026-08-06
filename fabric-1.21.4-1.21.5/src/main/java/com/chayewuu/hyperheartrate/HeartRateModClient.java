package com.chayewuu.hyperheartrate;

import com.chayewuu.hyperheartrate.config.ConfigManager;
import com.chayewuu.hyperheartrate.gui.HudRenderer;
import com.chayewuu.hyperheartrate.gui.ModKeyBindings;
import com.chayewuu.hyperheartrate.network.MultiplayerNetworking;
import com.chayewuu.hyperheartrate.network.RemoteHeartRateStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
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
 *
 * <p>注意：HTTP Server 的启动与关闭已在通用入口 {@link HeartRateMod#onInitialize()}
 * 中统一处理（启动 + 注册 JVM 关闭钩子），此处不再重复启动。</p>
 */
@Environment(EnvType.CLIENT)
public class HeartRateModClient implements ClientModInitializer {

    /** 联机心率发送间隔（tick），20 tick = 1 秒 */
    private static final int SEND_INTERVAL_TICKS = 20;

    /** tick 计数器，用于触发周期性发送 */
    private int tickCounter = 0;

    @Override
    public void onInitializeClient() {
        HeartRateMod.LOGGER.info("[HeartRateMod] 客户端初始化中...");

        // 加载配置（懒加载触发，确保后续读取一致）
        ConfigManager.getConfig();

        // 注册按键绑定（H 键打开主界面）
        ModKeyBindings.register();

        // 注册 HUD 渲染（实时心率显示）
        HudRenderer.register();

        // 注册联机心率同步：客户端 S2C 接收器
        MultiplayerNetworking.registerClientReceivers();

        // NameTag 旁心率显示通过 Mixin 劫持 EntityRenderer.getNameTag 实现，无需注册

        // 注册客户端 tick 回调：每秒发送自身心率 + 清理过期远端心率
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        HeartRateMod.LOGGER.info("[HeartRateMod] 客户端初始化完成");
    }

    /**
     * 客户端 tick 回调。
     * <p>每 {@link #SEND_INTERVAL_TICKS} tick（1 秒）发送一次自身心率到服务端，
     * 并清理已断开/离开的远端玩家心率缓存。</p>
     */
    private void onClientTick(MinecraftClient client) {
        tickCounter++;
        if (tickCounter < SEND_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        // 发送自身心率
        try {
            MultiplayerNetworking.sendHeartRateToServer();
        } catch (Throwable t) {
            HeartRateMod.LOGGER.warn("[HeartRateMod] 发送心率失败", t);
        }

        // 清理已离线玩家的心率缓存
        try {
            ClientWorld level = client.world;
            ClientPlayerEntity self = client.player;
            if (level == null || self == null) {
                return;
            }
            // 仅保留当前世界内仍存在的玩家的心率数据
            java.util.Set<java.util.UUID> online = new java.util.HashSet<>();
            for (var entity : level.getPlayers()) {
                if (entity != self) {
                    online.add(entity.getUuid());
                }
            }
            RemoteHeartRateStore.getInstance().retainOnly(online);
        } catch (Throwable t) {
            HeartRateMod.LOGGER.warn("[HeartRateMod] 清理远端心率缓存失败", t);
        }
    }
}
