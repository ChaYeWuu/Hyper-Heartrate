package com.chayewuu.hyperheartrate.gui;

import com.chayewuu.hyperheartrate.config.ConfigManager;
import com.chayewuu.hyperheartrate.config.ModConfig;
import com.chayewuu.hyperheartrate.device.DeviceManagerHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 心率采集设置界面。
 * <p>
 * 独立于 GUI 设置，专门配置心率采集相关的参数：
 * 采集间隔、历史数据保留数量、自动重连开关、重连延迟。
 * </p>
 *
 * <p><b>设置项：</b></p>
 * <ol>
 *     <li>采集间隔 Slider（200~5000ms）</li>
 *     <li>历史数据数量 Slider（60~1000）</li>
 *     <li>自动重连开关</li>
 *     <li>重连延迟 Slider（1000~30000ms）</li>
 *     <li>返回按钮</li>
 * </ol>
 */
public class HeartRateSettingsScreen extends Screen {
    /** 主面板宽度 */
    private static final int PANEL_WIDTH = 340;
    /** 最小面板高度 */
    private static final int MIN_PANEL_HEIGHT = 170;
    /** 最大面板高度 */
    private static final int MAX_PANEL_HEIGHT = 230;
    /** 圆角半径 */
    private static final int CORNER_RADIUS = 14;
    /** 控件宽度 */
    private static final int CONTROL_WIDTH = 280;
    /** 控件高度 */
    private static final int CONTROL_HEIGHT = 20;
    /** 控件行间距 */
    private static final int ROW_GAP = 28;

    /** 父屏幕 */
    private final Screen parent;

    /** 主面板左上角 X */
    private int panelX;
    /** 主面板左上角 Y */
    private int panelY;
    /** 主面板高度（动态计算） */
    private int panelHeight;

    /**
     * 构造心率设置界面。
     *
     * @param parent 父屏幕
     */
    public HeartRateSettingsScreen(Screen parent) {
        super(Component.literal("心率设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // 基于内容计算动态高度：标题 + 4 个控件 + 返回 + 底部间距
        int titleArea = 36;
        int controlsHeight = CONTROL_HEIGHT * 5 + ROW_GAP * 4;
        int bottomPadding = 14;
        int calculatedHeight = titleArea + controlsHeight + bottomPadding;
        panelHeight = Math.max(MIN_PANEL_HEIGHT, Math.min(MAX_PANEL_HEIGHT, calculatedHeight));

        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - panelHeight) / 2;
        int controlX = panelX + (PANEL_WIDTH - CONTROL_WIDTH) / 2;
        ModConfig config = ConfigManager.getConfig();

        int y = panelY + 36;
        // 1. 采集间隔（slider message 已含标签，无需额外画文字）
        addRenderableWidget(new CaptureIntervalSlider(controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT,
                config.getCaptureIntervalMs()));
        y += ROW_GAP;
        // 2. 历史数据数量
        addRenderableWidget(new HistorySizeSlider(controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT,
                config.getHistorySize()));
        y += ROW_GAP;
        // 3. 自动重连开关
        addRenderableWidget(createToggle("自动重连", config.isAutoReconnect(), v -> {
            config.setAutoReconnect(v);
            DeviceManagerHolder.get().setAutoReconnect(v);
            saveConfig();
        }, controlX, y));
        y += ROW_GAP;
        // 4. 重连延迟
        addRenderableWidget(new ReconnectDelaySlider(controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT,
                config.getReconnectDelayMs()));
        y += ROW_GAP + 4;
        // 5. 返回按钮
        addRenderableWidget(Button.builder(
                Component.literal("返回"),
                btn -> onClose()
        ).bounds(controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT).build());
    }

    /**
     * 创建开关按钮。
     */
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

    /**
     * 异步保存配置。
     */
    private static void saveConfig() {
        ConfigManager.save();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0x80000000);
        MainScreen.fillRoundedPanel(graphics, panelX, panelY, PANEL_WIDTH, panelHeight, CORNER_RADIUS);

        int centerX = panelX + PANEL_WIDTH / 2;
        graphics.centeredText(this.font, "心率设置", centerX, panelY + 12, 0xFFFFD700);
        // slider/toggle 的 message 已包含标签（如"采集间隔: 1000ms"），无需额外画标签
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

    // ====== 内部 Slider 实现 ======

    /** 采集间隔滑块（200~5000ms） */
    private static class CaptureIntervalSlider extends AbstractSliderButton {
        private static final String LABEL = "采集间隔: ";

        CaptureIntervalSlider(int x, int y, int width, int height, int ms) {
            super(x, y, width, height, Component.literal(LABEL + ms + "ms"), valueFromMs(ms));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(LABEL + msFromValue(this.value) + "ms"));
        }

        @Override
        protected void applyValue() {
            int ms = msFromValue(this.value);
            ModConfig config = ConfigManager.getConfig();
            config.setCaptureIntervalMs(ms);
            ConfigManager.save();
        }

        private static double valueFromMs(int ms) {
            int clamped = Math.max(200, Math.min(5000, ms));
            return (clamped - 200) / (double) (5000 - 200);
        }

        private static int msFromValue(double value) {
            return 200 + (int) Math.round(value * (5000 - 200));
        }
    }

    /** 历史数据数量滑块（60~1000） */
    private static class HistorySizeSlider extends AbstractSliderButton {
        private static final String LABEL = "历史数据: ";

        HistorySizeSlider(int x, int y, int width, int height, int size) {
            super(x, y, width, height, Component.literal(LABEL + size), valueFromSize(size));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(LABEL + sizeFromValue(this.value)));
        }

        @Override
        protected void applyValue() {
            int size = sizeFromValue(this.value);
            ModConfig config = ConfigManager.getConfig();
            config.setHistorySize(size);
            ConfigManager.save();
        }

        private static double valueFromSize(int size) {
            int clamped = Math.max(60, Math.min(1000, size));
            return (clamped - 60) / (double) (1000 - 60);
        }

        private static int sizeFromValue(double value) {
            return 60 + (int) Math.round(value * (1000 - 60));
        }
    }

    /** 重连延迟滑块（1000~30000ms） */
    private static class ReconnectDelaySlider extends AbstractSliderButton {
        private static final String LABEL = "重连延迟: ";

        ReconnectDelaySlider(int x, int y, int width, int height, int ms) {
            super(x, y, width, height, Component.literal(LABEL + ms + "ms"), valueFromMs(ms));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(LABEL + msFromValue(this.value) + "ms"));
        }

        @Override
        protected void applyValue() {
            int ms = msFromValue(this.value);
            ModConfig config = ConfigManager.getConfig();
            config.setReconnectDelayMs(ms);
            ConfigManager.save();
        }

        private static double valueFromMs(int ms) {
            int clamped = Math.max(1000, Math.min(30000, ms));
            return (clamped - 1000) / (double) (30000 - 1000);
        }

        private static int msFromValue(double value) {
            return 1000 + (int) Math.round(value * (30000 - 1000));
        }
    }
}
