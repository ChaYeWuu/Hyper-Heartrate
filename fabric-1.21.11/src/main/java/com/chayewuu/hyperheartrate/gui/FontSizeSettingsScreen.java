package com.chayewuu.hyperheartrate.gui;

import com.chayewuu.hyperheartrate.config.ConfigManager;
import com.chayewuu.hyperheartrate.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * 字体大小设置界面（GUI 设置的子菜单）。
 * <p>
 * 提供各模块独立的字体/图标缩放控制：
 * <ul>
 *     <li>心率文字缩放（50%~300%）</li>
 *     <li>设备名称缩放（50%~200%）</li>
 *     <li>GUI 面板缩放（50%~200%）</li>
 *     <li>心率图标缩放（1x~4x）</li>
 * </ul>
 * 所有设置即时生效并自动保存。
 * </p>
 */
public class FontSizeSettingsScreen extends Screen {
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 190;
    private static final int CORNER_RADIUS = 14;
    private static final int CONTROL_WIDTH = 280;
    private static final int CONTROL_HEIGHT = 20;
    private static final int ROW_GAP = 24;

    private final Screen parent;
    private int panelX;
    private int panelY;

    public FontSizeSettingsScreen(Screen parent) {
        super(Text.literal("字体大小设置"));
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

        int y = panelY + 36;

        // 1. 心率文字缩放
        addDrawableChild(new ScaleSlider(
                controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT,
                "心率文字", config.getHeartRateFontScale(), 0.5, 3.0,
                v -> { config.setHeartRateFontScale(v); ConfigManager.save(); }
        ));
        y += ROW_GAP;

        // 2. 设备名称缩放
        addDrawableChild(new ScaleSlider(
                controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT,
                "设备名称", config.getDeviceNameFontScale(), 0.5, 2.0,
                v -> { config.setDeviceNameFontScale(v); ConfigManager.save(); }
        ));
        y += ROW_GAP;

        // 3. GUI 面板缩放
        addDrawableChild(new ScaleSlider(
                controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT,
                "GUI 面板", config.getGuiFontScale(), 0.5, 2.0,
                v -> { config.setGuiFontScale(v); ConfigManager.save(); }
        ));
        y += ROW_GAP;

        // 4. 心率图标缩放
        addDrawableChild(new IntScaleSlider(
                controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT,
                "心率图标", config.getIconScale(), 1, 4,
                v -> { config.setIconScale(v); ConfigManager.save(); }
        ));
        y += ROW_GAP;

        // 5. 返回按钮
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
        context.drawCenteredTextWithShadow(ModFontManager.getFont(), "字体大小设置", centerX, panelY + 12, 0xFFFFD700);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        net.minecraft.client.MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ====== 滑块实现 ======

    /**
     * 连续缩放滑块（百分比显示）。
     */
    private static class ScaleSlider extends SliderWidget {
        private final String label;
        private final double min;
        private final double max;
        private final java.util.function.Consumer<Double> onChange;

        ScaleSlider(int x, int y, int w, int h, String label, double current, double min, double max,
                    java.util.function.Consumer<Double> onChange) {
            super(x, y, w, h, Text.literal(label + ": " + pct(current)), toNormalized(current, min, max));
            this.label = label;
            this.min = min;
            this.max = max;
            this.onChange = onChange;
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(label + ": " + pct(getValue())));
        }

        @Override
        protected void applyValue() {
            onChange.accept(getValue());
        }

        private double getValue() {
            return min + clamp01(this.value) * (max - min);
        }

        private static String pct(double v) {
            return Math.round(v * 100) + "%";
        }

        private static double toNormalized(double current, double min, double max) {
            return clamp01((current - min) / (max - min));
        }
    }

    /**
     * 整数缩放滑块（倍数显示）。
     */
    private static class IntScaleSlider extends SliderWidget {
        private final String label;
        private final int min;
        private final int max;
        private final java.util.function.Consumer<Integer> onChange;

        IntScaleSlider(int x, int y, int w, int h, String label, int current, int min, int max,
                       java.util.function.Consumer<Integer> onChange) {
            super(x, y, w, h, Text.literal(label + ": " + current + "x"), toNormalized(current, min, max));
            this.label = label;
            this.min = min;
            this.max = max;
            this.onChange = onChange;
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(label + ": " + getValue() + "x"));
        }

        @Override
        protected void applyValue() {
            onChange.accept(getValue());
        }

        private int getValue() {
            return min + (int) Math.round(clamp01(this.value) * (max - min));
        }

        private static double toNormalized(int current, int min, int max) {
            return clamp01((double) (current - min) / (max - min));
        }
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
