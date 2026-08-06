package com.chayewuu.hyperheartrate.mixin;

import com.chayewuu.hyperheartrate.config.ConfigManager;
import com.chayewuu.hyperheartrate.config.ModConfig;
import com.chayewuu.hyperheartrate.heart.HeartRateManager;
import com.chayewuu.hyperheartrate.gui.MainScreen;
import com.chayewuu.hyperheartrate.network.RemoteHeartRateStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * TAB 列表玩家名旁追加心率显示。
 * <p>
 * 拦截 {@link PlayerListHud#getPlayerName(PlayerListEntry)}，
 * 在返回的 Component 后追加心率文本（颜色支持心动模式）。
 * </p>
 *
 * <p><b>心率数据来源：</b></p>
 * <ul>
 *     <li>本地玩家：从 {@link HeartRateManager} 获取（自己设备实时心率）</li>
 *     <li>远程玩家：从 {@link RemoteHeartRateStore} 获取（网络同步）</li>
 * </ul>
 */
@Mixin(PlayerListHud.class)
public class PlayerTabOverlayMixin {

    /** 自定义像素心字体（与 HUD drawPixelHeart 同款 7x6 像素心） */
    private static final StyleSpriteSource HEART_ICON_FONT =
            new StyleSpriteSource.Font(Identifier.of("hyper-heartrate", "icon"));
    /** 私有区字符，映射到自定义字体的像素心纹理 */
    private static final String HEART_CHAR = "\uE001";

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void hyperheartrate$appendHeartRate(PlayerListEntry playerListEntry,
                                                CallbackInfoReturnable<Text> cir) {
        ModConfig config = ConfigManager.getConfig();
        if (!config.isMultiplayerEnabled() || !config.isMultiplayerTabListEnabled()) {
            return;
        }

        UUID uuid = playerListEntry.getProfile().id();
        if (uuid == null) {
            return;
        }

        // 本地玩家从 HeartRateManager 取，远程从 RemoteHeartRateStore 取
        int hr;
        UUID localUuid = MinecraftClient.getInstance().player != null
                ? MinecraftClient.getInstance().player.getUuid() : null;
        if (uuid.equals(localUuid)) {
            hr = HeartRateManager.getInstance().getCurrentHeartRate();
        } else {
            hr = RemoteHeartRateStore.getInstance().getHeartRate(uuid);
        }
        if (hr <= 0) {
            return;
        }

        Text original = cir.getReturnValue();
        MutableText enriched = Text.empty().append(original);

        boolean colorMode = config.isMultiplayerHeartColorMode();
        int hrColor = colorMode ? MainScreen.getHeartColorForHr(hr) : 0xFFFF4060;
        int defaultColor = 0xFFAAAAAA;

        boolean showIcon = config.isMultiplayerTabListShowIcon();
        boolean showRate = config.isMultiplayerTabListShowRate();
        boolean showBpm = config.isMultiplayerTabListShowBpm();
        if (!showIcon && !showRate && !showBpm) {
            return;
        }

        enriched.append(Text.literal(" "));

        if (showIcon) {
            int iconColor = (colorMode && config.isMultiplayerHeartColorIcon()) ? hrColor : 0xFFFF4060;
            // 使用自定义字体的像素心（与 HUD drawPixelHeart 同款 7x6 像素心）
            enriched.append(Text.literal(HEART_CHAR)
                    .setStyle(Style.EMPTY.withFont(HEART_ICON_FONT).withColor(iconColor)));
        }
        if (showRate) {
            int rateColor = (colorMode && config.isMultiplayerHeartColorRate()) ? hrColor : defaultColor;
            if (showIcon) {
                enriched.append(Text.literal(" "));
            }
            enriched.append(Text.literal(String.valueOf(hr)).setStyle(Style.EMPTY.withColor(rateColor)));
        }
        if (showBpm) {
            int bpmColor = (colorMode && config.isMultiplayerHeartColorBpm()) ? hrColor : defaultColor;
            enriched.append(Text.literal(" BPM").setStyle(Style.EMPTY.withColor(bpmColor)));
        }

        cir.setReturnValue(enriched);
    }
}
