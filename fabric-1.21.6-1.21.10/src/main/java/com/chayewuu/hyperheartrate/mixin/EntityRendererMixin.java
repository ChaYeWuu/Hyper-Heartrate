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
import net.minecraft.client.render.entity.state.EntityRenderState;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 劫持原版 NameTag 渲染，在玩家 NameTag 上方/内部/下方显示心率。
 * inside：displayName 合并；above/below：手动额外绘制心率行。
 * 通过 HashMap<EntityRenderState, Entity> 建立 state→实体 映射，
 * 每帧由 getAndUpdateRenderState 更新，renderLabelIfPresent 读取。
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {

    private static final Identifier HEART_ICON_FONT = Identifier.of("hyper-heartrate", "icon");
    private static final String HEART_CHAR = "\uE001";

    /** 每帧 EntityRenderState → 实体 映射。普通 HashMap，不用 WeakHashMap 避免 GC 回收 */
    private static final Map<EntityRenderState, Entity> stateToEntity = new HashMap<>();

    @Inject(method = "getAndUpdateRenderState(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/client/render/entity/state/EntityRenderState;",
            at = @At("RETURN"))
    private void hyperheartrate$captureStateEntity(T entity, float partialTicks, CallbackInfoReturnable<S> cir) {
        S returnState = cir.getReturnValue();
        if (returnState != null) {
            stateToEntity.put(returnState, entity);
        }
    }

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/entity/state/EntityRenderState;F)V",
            at = @At("RETURN"))
    private void hyperheartrate$injectHeartRateNametag(T entity, S state, float partialTicks, CallbackInfo ci) {
        ModConfig config = ConfigManager.getConfig();
        if (!config.isMultiplayerEnabled()) return;
        if (!(entity instanceof PlayerEntity player)) return;
        Text originalDisplayName = state.displayName;
        if (originalDisplayName == null) return;

        UUID uuid = player.getUuid();
        int hr = getHeartRate(player);
        if (hr <= 0) return;

        Text heartRateComp = buildHeartRateComponent(hr, config);
        if (heartRateComp == null) return;

        String position = config.getMultiplayerNametagPosition();
        if ("inside".equals(position)) {
            state.displayName = Text.empty().append(originalDisplayName).append(" ").append(heartRateComp);
        } else if ("above".equals(position) || "below".equals(position)) {
            // 下方模式：整体 nametag 上移
            if ("below".equals(position) && state.nameLabelPos != null) {
                state.nameLabelPos = state.nameLabelPos.add(0, 0.3, 0);
            }
        }
    }

    @Inject(method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;pop()V"))
    private void hyperheartrate$injectHeartRateLine(S state, Text text, MatrixStack matrices,
                                                    VertexConsumerProvider vertexConsumers, int light,
                                                    CallbackInfo ci) {
        Entity entity = stateToEntity.get(state);
        if (entity == null) return;
        UUID uuid = entity.getUuid();
        int hr = getHeartRateFromUuid(uuid);
        if (hr <= 0) return;

        ModConfig config = ConfigManager.getConfig();
        String position = config.getMultiplayerNametagPosition();
        if (!"above".equals(position) && !"below".equals(position)) return;

        Text hrComp = buildHeartRateComponent(hr, config);
        if (hrComp == null) return;

        TextRenderer textRenderer = ((EntityRenderer<?, ?>) (Object) this).getTextRenderer();
        int width = textRenderer.getWidth(hrComp);
        float x = -width / 2.0f;
        float y = "above".equals(position) ? -11.0f : 11.0f;

        int bgAlpha = (int) (MinecraftClient.getInstance().options.getTextBackgroundOpacity(0.25f) * 255.0f);
        int bgColor = (bgAlpha & 0xFF) << 24;

        matrices.push();
        // SEE_THROUGH 图层避免被玩家模型深度遮挡导致字消失，带背景色
        textRenderer.draw(hrComp, x, y, 0xFFFFFFFF, false,
                matrices.peek().getPositionMatrix(), vertexConsumers,
                net.minecraft.client.font.TextRenderer.TextLayerType.SEE_THROUGH, bgColor, light);
        matrices.pop();
    }

    private static int getHeartRate(PlayerEntity player) {
        return getHeartRateFromUuid(player.getUuid());
    }

    private static int getHeartRateFromUuid(UUID uuid) {
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