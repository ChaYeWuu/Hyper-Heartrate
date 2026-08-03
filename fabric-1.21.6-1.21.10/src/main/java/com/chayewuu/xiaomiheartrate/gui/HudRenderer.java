package com.chayewuu.xiaomiheartrate.gui;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/**
 * HUD 渲染注册器。
 * <p>
 * 通过 Fabric API 的 {@link HudRenderCallback} 注册心率 HUD 渲染回调，
 * 在 HUD 渲染阶段绘制心率显示。
 * </p>
 * <p>当有 Screen 打开时不渲染 HUD，避免遮挡界面内容。
 * 位置调整界面（{@link PositionAdjustScreen}）会手动调用
 * {@link HeartRateHudWidget#render} 渲染 HUD。</p>
 */
public final class HudRenderer {
    private HudRenderer() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register(HudRenderer::renderHud);
    }

    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.inGameHud == null) {
            return;
        }
        // 当有 Screen 打开时不渲染 HUD（位置调整界面会手动调用 render）
        if (client.currentScreen != null) {
            return;
        }
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        HeartRateHudWidget.getInstance().render(context, screenWidth, screenHeight);
    }
}
