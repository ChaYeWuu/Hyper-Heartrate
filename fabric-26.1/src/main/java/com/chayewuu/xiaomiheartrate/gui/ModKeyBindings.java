package com.chayewuu.xiaomiheartrate.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.chayewuu.xiaomiheartrate.HeartRateMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Mod 按键绑定注册。
 * <p>
 * 注册打开主界面的 {@code H} 键，并在客户端 tick 事件中检测按键按下，
 * 触发打开 {@link MainScreen}。
 * </p>
 *
 * <p>按键类别使用自定义类别 {@code key.categories.xiaomi-heartrate}，
 * 默认按键为 {@code H}（GLFW_KEY_H），玩家可在控制设置中重新绑定。</p>
 *
 * <p>该类仅客户端使用，所有方法应在客户端主线程调用。</p>
 */
public final class ModKeyBindings {
    /** 自定义按键分类标识 */
    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(HeartRateMod.MOD_ID, "heart_rate"));

    /** 打开主界面按键翻译键 */
    private static final String KEY_OPEN_MAIN = "key." + HeartRateMod.MOD_ID + ".open_main";

    /** 打开主界面的按键映射实例 */
    private static KeyMapping openMainKey;

    /** 私有构造器，禁止实例化 */
    private ModKeyBindings() {
    }

    /**
     * 注册按键绑定。
     * <p>应在 {@code onInitializeClient} 中调用一次。</p>
     */
    public static void register() {
        openMainKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                KEY_OPEN_MAIN,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMainKey.consumeClick()) {
                openMainScreen();
            }
        });
    }

    /**
     * 打开主界面屏幕。
     * <p>使用 26.2 API {@code Minecraft.getInstance().setScreen(...)} 切换屏幕。</p>
     */
    private static void openMainScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        // 26.2: 屏幕切换 API 已迁移至 Minecraft#gui#setScreen
        minecraft.setScreen(new MainScreen(Component.translatable("screen." + HeartRateMod.MOD_ID + ".main")));
    }
}
