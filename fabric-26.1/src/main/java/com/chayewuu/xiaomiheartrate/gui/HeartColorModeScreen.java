package com.chayewuu.xiaomiheartrate.gui;

import com.chayewuu.xiaomiheartrate.config.ConfigManager;
import com.chayewuu.xiaomiheartrate.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 心动模式自定义子菜单。
 * <p>
 * 独立控制心率图标、心率数值、BPM 后缀在心动模式下是否变色。
 * 仅当 SettingScreen 中"心动模式"主开关为开时这些选项才生效。
 * </p>
 *
 * <p>设置项：</p>
 * <ol>
 *     <li>心率图标变色（开/关）</li>
 *     <li>心率数值变色（开/关）</li>
 *     <li>BPM 后缀变色（开/关）</li>
 *     <li>返回按钮</li>
 * </ol>
 */
public class HeartColorModeScreen extends Screen {
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 160;
    private static final int CORNER_RADIUS = 14;
    private static final int CONTROL_WIDTH = 280;
    private static final int CONTROL_HEIGHT = 20;
    private static final int ROW_GAP = 30;

    private final Screen parent;
    private int panelX;
    private int panelY;

    public HeartColorModeScreen(Screen parent) {
        super(Component.literal("心动模式设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - PANEL_HEIGHT) / 2;
        if (panelY < 10) {
            panelY = 10;
        }
        int controlX = panelX + (PANEL_WIDTH - CONTROL_WIDTH) / 2;
        ModConfig config = ConfigManager.getConfig();

        int y = panelY + 40;
        // 1. 心率图标变色
        addRenderableWidget(createToggle("心率图标", config.isHeartColorIcon(), v -> {
            config.setHeartColorIcon(v);
            ConfigManager.save();
        }, controlX, y));
        y += ROW_GAP;
        // 2. 心率数值变色
        addRenderableWidget(createToggle("心率", config.isHeartColorRate(), v -> {
            config.setHeartColorRate(v);
            ConfigManager.save();
        }, controlX, y));
        y += ROW_GAP;
        // 3. BPM 后缀变色
        addRenderableWidget(createToggle("BPM", config.isHeartColorBpm(), v -> {
            config.setHeartColorBpm(v);
            ConfigManager.save();
        }, controlX, y));
        y += ROW_GAP;
        // 4. 返回按钮
        addRenderableWidget(Button.builder(
                Component.literal("返回"),
                btn -> onClose()
        ).bounds(controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // 背景遮罩（与其他设置界面一致，使用 graphics.fill 而非 renderBackground）
        graphics.fill(0, 0, this.width, this.height, 0x80000000);
        // 面板
        MainScreen.fillRounded(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 0xE6121418, CORNER_RADIUS);
        MainScreen.drawRoundedOutline(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 0xFF3A3F4A, CORNER_RADIUS);

        int centerX = panelX + PANEL_WIDTH / 2;
        Font modFont = ModFontManager.getFont();
        ModFontManager.centeredText(graphics, modFont, "心动模式设置", centerX, panelY + 14, 0xFFFFD700);
        // 副标题说明
        ModFontManager.centeredText(graphics, modFont, "选择哪些元素根据心率自动变色", centerX, panelY + 26, 0xFF888888);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private Button createToggle(String label, boolean initial, java.util.function.Consumer<Boolean> onChange, int x, int y) {
        return Button.builder(
                Component.literal(label + ": " + (initial ? "开" : "关")),
                b -> {
                    boolean current = b.getMessage().getString().endsWith("开");
                    boolean next = !current;
                    b.setMessage(Component.literal(label + ": " + (next ? "开" : "关")));
                    onChange.accept(next);
                }
        ).bounds(x, y, CONTROL_WIDTH, CONTROL_HEIGHT).build();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
