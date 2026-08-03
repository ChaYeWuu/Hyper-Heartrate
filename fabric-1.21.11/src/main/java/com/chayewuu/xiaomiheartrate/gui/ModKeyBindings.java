package com.chayewuu.xiaomiheartrate.gui;

import com.chayewuu.xiaomiheartrate.HeartRateMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Mod 按键绑定注册（1.21.6+ 版本）。
 * <p>
 * 1.21.6+ 的 {@code KeyBinding} 构造函数使用 {@code KeyBinding.Category} record，
 * 直接调用 4 参数构造函数。
 * </p>
 *
 * <p>该类仅客户端使用，所有方法应在客户端主线程调用。</p>
 */
public final class ModKeyBindings {
    /** 打开主界面按键翻译键 */
    private static final String KEY_OPEN_MAIN = "key." + HeartRateMod.MOD_ID + ".open_main";

    /** 打开主界面的按键映射实例 */
    private static KeyBinding openMainKey;

    /** 私有构造器，禁止实例化 */
    private ModKeyBindings() {
    }

    /**
     * 注册按键绑定。
     * <p>应在 {@code onInitializeClient} 中调用一次。</p>
     */
    public static void register() {
        KeyBinding.Category category = new KeyBinding.Category(
                Identifier.of(HeartRateMod.MOD_ID, "category"));
        openMainKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                KEY_OPEN_MAIN, InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMainKey.wasPressed()) {
                openMainScreen();
            }
        });
    }

    /**
     * 打开主界面屏幕。
     * <p>使用 {@code MinecraftClient.getInstance().setScreen(...)} 切换屏幕。</p>
     */
    private static void openMainScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        // 切换屏幕
        client.setScreen(new MainScreen(Text.translatable("screen." + HeartRateMod.MOD_ID + ".main")));
    }
}
