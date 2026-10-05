package com.chayewuu.hyperheartrate.mixin;

import com.chayewuu.hyperheartrate.config.ConfigManager;
import com.chayewuu.hyperheartrate.config.ModConfig;
import com.chayewuu.hyperheartrate.gui.MainScreen;
import com.chayewuu.hyperheartrate.heart.HeartRateManager;
import com.chayewuu.hyperheartrate.network.RemoteHeartRateStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * 劫持原版 NameTag 渲染，在玩家 NameTag 上方/内部/下方显示心率。
 * <p>
 * 拦截 {@link EntityRenderer#extractNameTags} 的 5 参数版本
 * （{@code extractNameTags(Entity, EntityRenderState, float, double, double)}，
 * 这是所有实体渲染 NameTag 的最终公共路径，且为 final 方法不会被 override），
 * 在原版设置 nameTag/scoreText 后，根据 {@link ModConfig#getMultiplayerNametagPosition()} 决定心率显示位置：
 * <ul>
 *     <li><b>"above"</b>（上方）：nameTag 设为心率 Component，scoreText 设为原版名字</li>
 *     <li><b>"inside"</b>（内部）：nameTag 设为"原版名字 + 心率"合并 Component，scoreText 保持原版</li>
 *     <li><b>"below"</b>（下方）：覆盖 scoreText 为心率 Component，nameTag 保持原版名字</li>
 * </ul>
 * 利用原版 submitNameDisplay 的渲染顺序（scoreText 在下、nameTag 在上）实现位置切换。
 * </p>
 *
 * <p><b>完全继承原版 NameTag 行为：</b>位置、可见性（蹲下/隐身/距离）、缩放、F1 隐藏
 * 均由原版 extractNameTags/submitNameDisplay 控制，不额外干预。</p>
 *
 * <p>仅对 {@link Player} 生效。</p>
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {

    /** 自定义像素心字体（与 HUD drawPixelHeart 同款 7x6 像素心） */
    private static final FontDescription HEART_ICON_FONT =
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("hyper-heartrate", "icon"));
    /** 私有区字符，映射到自定义字体的像素心纹理 */
    private static final String HEART_CHAR = "\uE001";

    @Inject(method = "extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V",
            at = @At("RETURN"))
    private void hyperheartrate$injectHeartRateNametag(T entity, S state, float partialTicks, double maxNameTagDistance, double maxScoreTextDistance, CallbackInfo ci) {
        ModConfig config = ConfigManager.getConfig();
        if (!config.isMultiplayerEnabled()) {
            return;
        }
        if (!(entity instanceof Player player)) {
            return;
        }
        // 原版已将 nameTag 设为玩家显示名（或 null，如距离过远/隐身）
        Component originalNameTag = state.nameTag;
        if (originalNameTag == null) {
            return;
        }

        // 获取心率：本地玩家用 HeartRateManager，远程用 RemoteHeartRateStore
        UUID uuid = player.getUUID();
        UUID localUuid = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.getUUID() : null;
        int hr;
        if (uuid.equals(localUuid)) {
            hr = HeartRateManager.getInstance().getCurrentHeartRate();
        } else {
            hr = RemoteHeartRateStore.getInstance().getHeartRate(uuid);
        }
        if (hr <= 0) {
            return;
        }

        Component heartRateComp = buildHeartRateComponent(hr, config);
        if (heartRateComp == null) {
            return;
        }

        // 根据位置设置：原版 submitNameDisplay 先画 scoreText（下方），translate up，再画 nameTag（上方）
        String position = config.getMultiplayerNametagPosition();
        if ("above".equals(position)) {
            // 心率在上方：nameTag = 心率，scoreText = 原版名字
            state.nameTag = heartRateComp;
            state.scoreText = originalNameTag;
        } else if ("inside".equals(position)) {
            // 心率在内部：nameTag = 原版名字 + 心率（同一行），scoreText 保持原版
            state.nameTag = Component.empty().append(originalNameTag).append(" ").append(heartRateComp);
        } else {
            // 心率在下方：nameTag = 原版名字（不变），scoreText = 心率
            state.scoreText = heartRateComp;
        }
    }

    /**
     * 构建心率显示 Component（像素心图标 + 心率值 + 可选 BPM）。
     *
     * @return 心率 Component，如果无需显示则返回 null
     */
    private static Component buildHeartRateComponent(int hr, ModConfig config) {
        boolean showIcon = config.isMultiplayerShowIcon();
        boolean showBpm = config.isMultiplayerShowBpm();
        if (!showIcon && !showBpm) {
            return null;
        }

        boolean colorMode = config.isMultiplayerHeartColorMode();
        int hrColor = colorMode ? MainScreen.getHeartColorForHr(hr) : 0xFFFF4060;
        int defaultColor = 0xFFFFFFFF;
        int iconColor = (colorMode && config.isMultiplayerHeartColorIcon()) ? hrColor : 0xFFFF4060;
        int rateColor = (colorMode && config.isMultiplayerHeartColorRate()) ? hrColor : defaultColor;
        int bpmColor = (colorMode && config.isMultiplayerHeartColorBpm()) ? hrColor : defaultColor;

        MutableComponent comp = Component.empty();
        if (showIcon) {
            comp.append(Component.literal(HEART_CHAR)
                    .withStyle(Style.EMPTY.withFont(HEART_ICON_FONT).withColor(iconColor)));
            comp.append(Component.literal(" "));
        }
        comp.append(Component.literal(String.valueOf(hr)).withColor(rateColor));
        if (showBpm) {
            comp.append(Component.literal(" BPM").withColor(bpmColor));
        }
        return comp;
    }
}
