package com.chayewuu.hyperheartrate.gui;

import com.chayewuu.hyperheartrate.HeartRateMod;
import com.chayewuu.hyperheartrate.util.ModLogger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * 模组设置子菜单。
 * <p>
 * 作为主菜单与具体设置界面（GUI设置、心率设置、API设置）之间的中间层，
 * 避免主菜单按钮过多。布局与主菜单风格一致：圆角面板 + 2 列按钮。
 * </p>
 */
public class ModSettingsScreen extends BaseModScreen {
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 160;
    private static final int CORNER_RADIUS = 14;
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 16;
    private static final int BUTTON_ROW_GAP = 8;

    private final Screen parent;
    private int panelX;
    private int panelY;

    public ModSettingsScreen(Screen parent) {
        super(Text.literal("模组设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - PANEL_HEIGHT) / 2;

        int leftButtonX = panelX + 12;
        int rightButtonX = panelX + PANEL_WIDTH - BUTTON_WIDTH - 12;
        int buttonsStartY = panelY + 40;

        // 第 1 行：GUI设置 | 心率设置
        addDrawableChild(ButtonWidget.builder(
                Text.literal("GUI设置"),
                btn -> {
                    ModLogger.info("[ModSettings] 用户点击 GUI设置");
                    MinecraftClient.getInstance().setScreen(
                            new SettingScreen(Text.translatable("screen." + HeartRateMod.MOD_ID + ".settings"), this)
                    );
                }
        ).dimensions(leftButtonX, buttonsStartY, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("心率设置"),
                btn -> {
                    ModLogger.info("[ModSettings] 用户点击 心率设置");
                    MinecraftClient.getInstance().setScreen(new HeartRateSettingsScreen(this));
                }
        ).dimensions(rightButtonX, buttonsStartY, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        // 第 2 行：API设置 | (空)
        int row2Y = buttonsStartY + BUTTON_HEIGHT + BUTTON_ROW_GAP;
        addDrawableChild(ButtonWidget.builder(
                Text.literal("API设置"),
                btn -> {
                    ModLogger.info("[ModSettings] 用户点击 API设置");
                    MinecraftClient.getInstance().setScreen(new ApiSettingsScreen(this));
                }
        ).dimensions(leftButtonX, row2Y, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        // 第 3 行：返回（居中）
        int row3Y = row2Y + BUTTON_HEIGHT + BUTTON_ROW_GAP;
        int backWidth = BUTTON_WIDTH * 2 + BUTTON_GAP;
        addDrawableChild(ButtonWidget.builder(
                Text.literal("返回"),
                btn -> close()
        ).dimensions(panelX + (PANEL_WIDTH - backWidth) / 2, row3Y, backWidth, BUTTON_HEIGHT).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x80000000);
        MainScreen.fillRoundedPanel(context, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, CORNER_RADIUS);

        int centerX = panelX + PANEL_WIDTH / 2;
        ModFontManager.centeredText(context, ModFontManager.getFont(), "模组设置", centerX, panelY + 14, 0xFFFFD700);

        super.render(context, mouseX, mouseY, delta);
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
