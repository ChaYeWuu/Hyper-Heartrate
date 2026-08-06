package com.chayewuu.hyperheartrate.gui;

import com.chayewuu.hyperheartrate.HeartRateMod;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * HUD 渲染注册器。
 * <p>
 * 通过 Fabric API 的 {@link HudElementRegistry} 注册心率 HUD 渲染回调，
 * 将其附加到原版聊天层之前渲染。
 * </p>
 * <p>当有 Screen 打开时不渲染 HUD，避免遮挡界面内容。
 * 位置调整界面（{@link PositionAdjustScreen}）会手动调用
 * {@link HeartRateHudWidget#render} 渲染 HUD。</p>
 */
public final class HudRenderer {
    private static final Identifier HUD_ID =
            Identifier.fromNamespaceAndPath(HeartRateMod.MOD_ID, "heart_rate_hud");

    private HudRenderer() {
    }

    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, HUD_ID, HudRenderer::renderHud);
    }

    private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null) {
            return;
        }
        // 当有 Screen 打开时不渲染 HUD（位置调整界面会手动调用 render）
        if (minecraft.gui.screen() != null) {
            return;
        }
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        HeartRateHudWidget.getInstance().render(graphics, screenWidth, screenHeight);
    }
}
