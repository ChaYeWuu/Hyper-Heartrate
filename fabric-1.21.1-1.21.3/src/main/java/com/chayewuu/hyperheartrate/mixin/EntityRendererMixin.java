package com.chayewuu.hyperheartrate.mixin;

import com.chayewuu.hyperheartrate.config.ConfigManager;
import com.chayewuu.hyperheartrate.config.ModConfig;
import com.chayewuu.hyperheartrate.gui.MainScreen;
import com.chayewuu.hyperheartrate.heart.HeartRateManager;
import com.chayewuu.hyperheartrate.network.RemoteHeartRateStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * 1.21.1-1.21.3：renderLabelIfPresent(T entity, Text, MatrixStack, VertexConsumerProvider, int, float)
 * 直接传入实体，可直接取心率。
 * inside：@ModifyVariable 劫持 text 参数追加心率，原版渲染（带背景板）。
 * above/below：手动额外绘制（pop 前）。
 * 下方模式：整体 nametag 上移（HEAD 处 translate）。
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin<T extends Entity> {

    private static final Identifier HEART_ICON_FONT = Identifier.of("hyper-heartrate", "icon");
    private static final String HEART_CHAR = "\uE001";

    @Inject(method = "renderLabelIfPresent(Lnet/minecraft/entity/Entity;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IF)V",
            at = @At("HEAD"))
    private void hyperheartrate$beforeRenderLabel(T entity, Text text, MatrixStack matrices,
                                                  VertexConsumerProvider vertexConsumers, int light,
                                                  float tickDelta, CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity)) return;
        if (!"below".equals(ConfigManager.getConfig().getMultiplayerNametagPosition())) return;
        matrices.translate(0, 0.3, 0);
    }

    @ModifyVariable(method = "renderLabelIfPresent(Lnet/minecraft/entity/Entity;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IF)V",
            at = @At("HEAD"), index = 2, argsOnly = true)
    private Text hyperheartrate$modifyTextForInside(Text text, T entity) {
        if (!(entity instanceof PlayerEntity)) return text;
        ModConfig config = ConfigManager.getConfig();
        if (!config.isMultiplayerEnabled()) return text;
        if (!"inside".equals(config.getMultiplayerNametagPosition())) return text;

        int hr = getHeartRate((PlayerEntity) entity);
        if (hr <= 0) return text;
        Text hrComp = buildHeartRateComponent(hr, config);
        if (hrComp == null) return text;

        // 返回"名字 + 心率"合并文本，由原版完全渲染（带背景板、图层、阴影描边）
        return Text.empty().append(text).append(" ").append(hrComp);
    }

    @Inject(method = "renderLabelIfPresent(Lnet/minecraft/entity/Entity;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;pop()V"))
    private void hyperheartrate$injectHeartRateLine(T entity, Text text, MatrixStack matrices,
                                                    VertexConsumerProvider vertexConsumers, int light,
                                                    float tickDelta, CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity player)) return;

        ModConfig config = ConfigManager.getConfig();
        if (!config.isMultiplayerEnabled()) return;
        String position = config.getMultiplayerNametagPosition();
        if (position == null || "inside".equals(position)) return;

        int hr = getHeartRate(player);
        if (hr <= 0) return;
        Text hrComp = buildHeartRateComponent(hr, config);
        if (hrComp == null) return;

        TextRenderer textRenderer = ((EntityRenderer<?>) (Object) this).getTextRenderer();
        float x = -textRenderer.getWidth(hrComp) / 2.0f;
        float y = "above".equals(position) ? -11.0f : 11.0f;

        boolean seeThrough = !entity.isSneaky();
        int bgAlpha = (int) (MinecraftClient.getInstance().options.getTextBackgroundOpacity(0.25f) * 255.0f);
        int bgColor = (bgAlpha & 0xFF) << 24;

        matrices.push();
        // 只画一次 SEE_THROUGH 带背景色，避免二次绘制在云层产生重影
        textRenderer.draw(hrComp, x, y, 0xFFFFFFFF, false,
                matrices.peek().getPositionMatrix(), vertexConsumers,
                seeThrough
                    ? net.minecraft.client.font.TextRenderer.TextLayerType.SEE_THROUGH
                    : net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL,
                bgColor, light);
        matrices.pop();
    }

    private static int getHeartRate(PlayerEntity player) {
        UUID uuid = player.getUuid();
        UUID localUuid = MinecraftClient.getInstance().player != null
                ? MinecraftClient.getInstance().player.getUuid() : null;
        if (uuid.equals(localUuid)) return HeartRateManager.getInstance().getCurrentHeartRate();
        return RemoteHeartRateStore.getInstance().getHeartRate(uuid);
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