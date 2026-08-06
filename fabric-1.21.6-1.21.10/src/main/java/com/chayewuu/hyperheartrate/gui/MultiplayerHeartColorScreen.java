package com.chayewuu.hyperheartrate.gui;

import com.chayewuu.hyperheartrate.config.ConfigManager;
import com.chayewuu.hyperheartrate.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * 联机心动模式自定义子菜单。
 * <p>
 * 独立控制其他玩家心率图标、心率数值、BPM 后缀在心动模式下是否变色。
 * 仅当 MultiplayerSettingsScreen 中"心动模式"主开关为开时这些选项才生效。
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
public class MultiplayerHeartColorScreen extends BaseModScreen {
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 160;
    private static final int CORNER_RADIUS = 14;
    private static final int CONTROL_WIDTH = 280;
    private static final int CONTROL_HEIGHT = 20;
    private static final int ROW_GAP = 30;

    private final Screen parent;
    private int panelX;
    private int panelY;

    public MultiplayerHeartColorScreen(Screen parent) {
        super(Text.literal("联机心动模式设置"));
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
        addDrawableChild(createToggle("心率图标", config.isMultiplayerHeartColorIcon(), v -> {
            config.setMultiplayerHeartColorIcon(v);
            ConfigManager.save();
        }, controlX, y));
        y += ROW_GAP;
        // 2. 心率数值变色
        addDrawableChild(createToggle("心率", config.isMultiplayerHeartColorRate(), v -> {
            config.setMultiplayerHeartColorRate(v);
            ConfigManager.save();
        }, controlX, y));
        y += ROW_GAP;
        // 3. BPM 后缀变色
        addDrawableChild(createToggle("BPM", config.isMultiplayerHeartColorBpm(), v -> {
            config.setMultiplayerHeartColorBpm(v);
            ConfigManager.save();
        }, controlX, y));
        y += ROW_GAP;
        // 4. 返回按钮
        addDrawableChild(ButtonWidget.builder(
                Text.literal("返回"),
                btn -> close()
        ).dimensions(controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x80000000);
        MainScreen.fillRoundedPanel(context, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, CORNER_RADIUS);

        int centerX = panelX + PANEL_WIDTH / 2;
        ModFontManager.centeredText(context, ModFontManager.getFont(), "联机心动模式设置", centerX, panelY + 14, 0xFFFFD700);
        ModFontManager.centeredText(context, ModFontManager.getFont(), "选择其他玩家哪些元素根据心率自动变色", centerX, panelY + 26, 0xFF888888);

        super.render(context, mouseX, mouseY, delta);
    }

    private ButtonWidget createToggle(String label, boolean initial, java.util.function.Consumer<Boolean> onChange, int x, int y) {
        return ButtonWidget.builder(
                Text.literal(label + ": " + (initial ? "开" : "关")),
                b -> {
                    boolean current = b.getMessage().getString().endsWith("开");
                    boolean next = !current;
                    b.setMessage(Text.literal(label + ": " + (next ? "开" : "关")));
                    onChange.accept(next);
                }
        ).dimensions(x, y, CONTROL_WIDTH, CONTROL_HEIGHT).build();
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
