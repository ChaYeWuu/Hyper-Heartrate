package com.chayewuu.hyperheartrate.gui;

import com.chayewuu.hyperheartrate.HeartRateMod;
import com.chayewuu.hyperheartrate.util.ModLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 模组设置子菜单。
 * <p>
 * 作为主菜单与具体设置界面（GUI设置、心率设置、API设置）之间的中间层，
 * 避免主菜单按钮过多。布局与主菜单风格一致：圆角面板 + 2 列按钮。
 * </p>
 */
public class ModSettingsScreen extends Screen {
    private static final int PANEL_WIDTH = 340;
    private static final int MIN_PANEL_HEIGHT = 140;
    private static final int MAX_PANEL_HEIGHT = 200;
    private static final int CORNER_RADIUS = 14;
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 16;
    private static final int BUTTON_ROW_GAP = 6;

    private final Screen parent;
    private int panelX;
    private int panelY;
    private int panelHeight;

    public ModSettingsScreen(Screen parent) {
        super(Component.literal("模组设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // 基于内容计算动态高度
        int titleHeight = 30;
        int rowCount = 3; // 3 行按钮
        int buttonsHeight = BUTTON_HEIGHT * rowCount + BUTTON_ROW_GAP * (rowCount - 1);
        int bottomPadding = 14;
        int calculatedHeight = titleHeight + buttonsHeight + bottomPadding;
        panelHeight = Math.max(MIN_PANEL_HEIGHT, Math.min(MAX_PANEL_HEIGHT, calculatedHeight));

        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - panelHeight) / 2;

        int leftButtonX = panelX + 12;
        int rightButtonX = panelX + PANEL_WIDTH - BUTTON_WIDTH - 12;
        int buttonsStartY = panelY + 40;

        // 第 1 行：GUI设置 | 心率设置
        addRenderableWidget(Button.builder(
                Component.literal("GUI设置"),
                btn -> {
                    ModLogger.info("[ModSettings] 用户点击 GUI设置");
                    Minecraft.getInstance().setScreen(
                            new SettingScreen(Component.translatable("screen." + HeartRateMod.MOD_ID + ".settings"), this)
                    );
                }
        ).bounds(leftButtonX, buttonsStartY, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        addRenderableWidget(Button.builder(
                Component.literal("心率设置"),
                btn -> {
                    ModLogger.info("[ModSettings] 用户点击 心率设置");
                    Minecraft.getInstance().setScreen(new HeartRateSettingsScreen(this));
                }
        ).bounds(rightButtonX, buttonsStartY, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        // 第 2 行：API设置 | (空)
        int row2Y = buttonsStartY + BUTTON_HEIGHT + BUTTON_ROW_GAP;
        addRenderableWidget(Button.builder(
                Component.literal("API设置"),
                btn -> {
                    ModLogger.info("[ModSettings] 用户点击 API设置");
                    Minecraft.getInstance().setScreen(new ApiSettingsScreen(this));
                }
        ).bounds(leftButtonX, row2Y, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        // 第 3 行：返回（居中）
        int row3Y = row2Y + BUTTON_HEIGHT + BUTTON_ROW_GAP;
        int backWidth = BUTTON_WIDTH * 2 + BUTTON_GAP;
        addRenderableWidget(Button.builder(
                Component.literal("返回"),
                btn -> onClose()
        ).bounds(panelX + (PANEL_WIDTH - backWidth) / 2, row3Y, backWidth, BUTTON_HEIGHT).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0x80000000);
        MainScreen.fillRoundedPanel(graphics, panelX, panelY, PANEL_WIDTH, panelHeight, CORNER_RADIUS);

        int centerX = panelX + PANEL_WIDTH / 2;
        ModFontManager.centeredText(graphics, ModFontManager.getFont(), "模组设置", centerX, panelY + 14, 0xFFFFD700);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
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
