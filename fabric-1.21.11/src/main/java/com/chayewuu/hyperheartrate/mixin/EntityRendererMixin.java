package com.chayewuu.hyperheartrate.mixin;

import com.chayewuu.hyperheartrate.config.ConfigManager;
import com.chayewuu.hyperheartrate.config.ModConfig;
import com.chayewuu.hyperheartrate.gui.MainScreen;
import com.chayewuu.hyperheartrate.heart.HeartRateManager;
import com.chayewuu.hyperheartrate.network.RemoteHeartRateStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * 1.21.11 队列提交式 nametag。
 * inside：displayName 合并"名字 + 心率"。
 * above/below：在 render 的 RETURN 处额外 submitLabel 提交心率行，实现两行分离。
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {

    private static final StyleSpriteSource HEART_ICON_FONT =
            new StyleSpriteSource.Font(Identifier.of("hyper-heartrate", "icon"));
    private static final String HEART_CHAR = "\uE001";

    @Unique
    private Text hyperheartrate$pendingHeartRate = null;

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/entity/state/EntityRenderState;F)V",
            at = @At("RETURN"))
    private void hyperheartrate$injectHeartRateNametag(T entity, S state, float partialTicks, CallbackInfo ci) {
        ModConfig config = ConfigManager.getConfig();
        if (!config.isMultiplayerEnabled()) return;
        if (!(entity instanceof PlayerEntity player)) return;
        Text originalDisplayName = state.displayName;
        if (originalDisplayName == null) return;

        UUID uuid = player.getUuid();
        UUID localUuid = MinecraftClient.getInstance().player != null
                ? MinecraftClient.getInstance().player.getUuid() : null;
        int hr;
        if (uuid.equals(localUuid)) {
            hr = HeartRateManager.getInstance().getCurrentHeartRate();
        } else {
            hr = RemoteHeartRateStore.getInstance().getHeartRate(uuid);
        }
        if (hr <= 0) return;

        Text heartRateComp = buildHeartRateComponent(hr, config);
        if (heartRateComp == null) return;

        String position = config.getMultiplayerNametagPosition();
        if ("inside".equals(position)) {
            state.displayName = Text.empty().append(originalDisplayName).append(" ").append(heartRateComp);
        } else if ("above".equals(position) || "below".equals(position)) {
            this.hyperheartrate$pendingHeartRate = heartRateComp;
            // 下方模式：整体 nametag（名字+心率）上移，避免被皮肤挡住
            if ("below".equals(position) && state.nameLabelPos != null) {
                state.nameLabelPos = state.nameLabelPos.add(0, 0.5, 0);
            }
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("RETURN"))
    private void hyperheartrate$injectAtRenderReturn(S state, net.minecraft.client.util.math.MatrixStack matrices,
                                                     net.minecraft.client.render.command.OrderedRenderCommandQueue queue,
                                                     net.minecraft.client.render.state.CameraRenderState camera,
                                                     CallbackInfo ci) {
        if (this.hyperheartrate$pendingHeartRate == null) return;

        String position = ConfigManager.getConfig().getMultiplayerNametagPosition();
        if (!"above".equals(position) && !"below".equals(position)) return;
        if (state.nameLabelPos == null) return;

        double yOff = "above".equals(position) ? 0.3 : -0.3;
        Vec3d hrPos = state.nameLabelPos.add(0, yOff, 0);

        queue.submitLabel(matrices, hrPos, 0, this.hyperheartrate$pendingHeartRate,
                !state.sneaking, state.light, state.squaredDistanceToCamera, camera);
    }

    private static Text buildHeartRateComponent(int hr, ModConfig config) {
        boolean showIcon = config.isMultiplayerShowIcon();
        boolean showBpm = config.isMultiplayerShowBpm();
        if (!showIcon && !showBpm) return null;

        boolean colorMode = config.isMultiplayerHeartColorMode();
        int hrColor = colorMode ? MainScreen.getHeartColorForHr(hr) : 0xFFFF4060;
        int defaultColor = 0xFFFFFFFF;
        int iconColor = (colorMode && config.isMultiplayerHeartColorIcon()) ? hrColor : 0xFFFF4060;
        int rateColor = (colorMode && config.isMultiplayerHeartColorRate()) ? hrColor : defaultColor;
        int bpmColor = (colorMode && config.isMultiplayerHeartColorBpm()) ? hrColor : defaultColor;

        MutableText comp = Text.empty();
        if (showIcon) {
            comp.append(Text.literal(HEART_CHAR).setStyle(Style.EMPTY.withFont(HEART_ICON_FONT).withColor(iconColor)));
            comp.append(Text.literal(" "));
        }
        comp.append(Text.literal(String.valueOf(hr)).setStyle(Style.EMPTY.withColor(rateColor)));
        if (showBpm) comp.append(Text.literal(" BPM").setStyle(Style.EMPTY.withColor(bpmColor)));
        return comp;
    }
}