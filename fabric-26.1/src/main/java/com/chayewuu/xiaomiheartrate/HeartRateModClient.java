package com.chayewuu.xiaomiheartrate;

import com.chayewuu.xiaomiheartrate.config.ConfigManager;
import com.chayewuu.xiaomiheartrate.gui.HudRenderer;
import com.chayewuu.xiaomiheartrate.gui.ModKeyBindings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * 心率监测 Mod 客户端入口类。
 * <p>
 * 实现 {@link ClientModInitializer}，在客户端启动时初始化客户端特定逻辑。
 * 注册内容：按键绑定（H 键打开主界面）、HUD 渲染（实时心率显示）。
 * </p>
 *
 * <p>注意：HTTP Server 的启动与关闭已在通用入口 {@link HeartRateMod#onInitialize()}
 * 中统一处理（启动 + 注册 JVM 关闭钩子），此处不再重复启动。</p>
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

        HeartRateMod.LOGGER.info("[HeartRateMod] 客户端初始化完成");
    }
}
