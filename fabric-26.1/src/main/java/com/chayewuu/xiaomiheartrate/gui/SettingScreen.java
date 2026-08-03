package com.chayewuu.xiaomiheartrate.gui;

import com.chayewuu.xiaomiheartrate.config.ConfigManager;
import com.chayewuu.xiaomiheartrate.config.ModConfig;
import com.chayewuu.xiaomiheartrate.util.ModLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * GUI 显示设置界面。
 * <p>
 * 紧凑垂直布局，支持垂直滚动（当屏幕高度不足时）。RGB 四个滑块放在同一行（缩短宽度），
 * 下方显示颜色预览色块。slider/toggle 的 message 已包含标签，无需额外画文字标签。
 * </p>
 *
 * <p>设置项：</p>
 * <ol>
 *     <li>自定义设备名称输入框</li>
 *     <li>心率显示位置（点击打开位置调整界面，拖动 HUD 定位）</li>
 *     <li>字体大小（子菜单）</li>
 *     <li>GUI 透明度 Slider</li>
 *     <li>字体颜色 R/G/B/A 四个 Slider（同一行）+ 预览色块</li>
 *     <li>心动模式主开关 + 自定义子菜单按钮</li>
 *     <li>显示图标开关</li>
 *     <li>显示设备名称开关</li>
 *     <li>显示 BPM 后缀开关</li>
 *     <li>返回按钮</li>
 * </ol>
 *
 * <p><b>滚动支持：</b>当屏幕高度不足（如 1x GUI 缩放）时，面板高度自适应屏幕，
 * 内容超出可视区域时可通过鼠标滚轮滚动。右侧显示滚动条作为视觉提示。</p>
 */
public class SettingScreen extends Screen {
    private static final int PANEL_WIDTH = 340;
    private static final int MAX_PANEL_HEIGHT = 360;
    private static final int CORNER_RADIUS = 14;
    private static final int CONTROL_WIDTH = 280;
    private static final int CONTROL_HEIGHT = 20;
    private static final int ROW_GAP = 26;
    /** RGB 四个 slider 总宽度 = CONTROL_WIDTH，每个 slider 宽度 */
    private static final int RGB_SLIDER_WIDTH = 66;
    private static final int RGB_SLIDER_GAP = 4;
    /** 颜色预览色块高度 */
    private static final int PREVIEW_HEIGHT = 22;
    /** 面板内容上边距（标题区域） */
    private static final int CONTENT_TOP_OFFSET = 36;
    /** 面板内容下边距 */
    private static final int CONTENT_BOTTOM_PADDING = 10;

    private final Screen parent;

    /** 当前面板高度（自适应屏幕，最大 MAX_PANEL_HEIGHT） */
    private int panelHeight = MAX_PANEL_HEIGHT;
    private int panelX;
    private int panelY;
    private EditBox deviceNameInput;
    /** 四个 RGB slider 的当前值（0~1），用于预览渲染 */
    private float[] rgbaPreview = {1.0f, 1.0f, 1.0f, 1.0f};

    /** 预览色块的 Y 坐标（init 时计算，已含滚动偏移） */
    private int previewY;

    // ===== 滚动支持 =====
    /** 当前垂直滚动偏移（像素） */
    private int scrollOffset = 0;
    /** 内容总高度（init 中计算） */
    private int contentHeight = 0;

    public SettingScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_WIDTH) / 2;
        // 面板高度自适应屏幕，保留上下各 10px 边距
        panelHeight = Math.min(MAX_PANEL_HEIGHT, this.height - 20);
        panelY = (this.height - panelHeight) / 2;
        if (panelY < 10) {
            panelY = 10;
        }
        int controlX = panelX + (PANEL_WIDTH - CONTROL_WIDTH) / 2;
        ModConfig config = ConfigManager.getConfig();

        // 初始化预览颜色
        float[] cfgRgba = config.getFontColor();
        rgbaPreview = new float[]{cfgRgba[0], cfgRgba[1], cfgRgba[2], cfgRgba[3]};

        int baseY = panelY + CONTENT_TOP_OFFSET;
        int y = baseY - scrollOffset;

        // 1. 自定义设备名称输入框
        deviceNameInput = new EditBox(this.font, controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT, Component.literal("设备名"));
        deviceNameInput.setMaxLength(32);
        deviceNameInput.setValue(config.getCustomDeviceName());
        deviceNameInput.setHint(Component.literal("自定义设备名称（留空使用真实名）"));
        addRenderableWidget(deviceNameInput);
        y += ROW_GAP;
        // 2. 心率显示位置（点击打开位置调整界面）
        addRenderableWidget(Button.builder(
                Component.literal("心率显示位置（点击拖动调整）"),
                btn -> {
                    ModLogger.info("[SettingScreen] 打开心率显示位置调整界面");
                    Minecraft.getInstance().setScreen(new PositionAdjustScreen(this));
                }
        ).bounds(controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT).build());
        y += ROW_GAP;
        // 2b. 字体大小（点击打开字体大小设置子菜单）
        addRenderableWidget(Button.builder(
                Component.literal("字体大小（各模块独立设置）"),
                btn -> {
                    ModLogger.info("[SettingScreen] 打开字体大小设置界面");
                    Minecraft.getInstance().setScreen(new FontSizeSettingsScreen(this));
                }
        ).bounds(controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT).build());
        y += ROW_GAP;
        // 3. GUI 透明度
        addRenderableWidget(new OpacitySlider(controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT, config.getGuiOpacity()));
        y += ROW_GAP;
        // 4. 字体颜色 R/G/B/A（四个 slider 同一行）
        int sliderX = controlX;
        addRenderableWidget(new ColorChannelSlider(sliderX, y, RGB_SLIDER_WIDTH, CONTROL_HEIGHT, 0, rgbaPreview[0], this));
        sliderX += RGB_SLIDER_WIDTH + RGB_SLIDER_GAP;
        addRenderableWidget(new ColorChannelSlider(sliderX, y, RGB_SLIDER_WIDTH, CONTROL_HEIGHT, 1, rgbaPreview[1], this));
        sliderX += RGB_SLIDER_WIDTH + RGB_SLIDER_GAP;
        addRenderableWidget(new ColorChannelSlider(sliderX, y, RGB_SLIDER_WIDTH, CONTROL_HEIGHT, 2, rgbaPreview[2], this));
        sliderX += RGB_SLIDER_WIDTH + RGB_SLIDER_GAP;
        addRenderableWidget(new ColorChannelSlider(sliderX, y, RGB_SLIDER_WIDTH, CONTROL_HEIGHT, 3, rgbaPreview[3], this));
        y += CONTROL_HEIGHT + 4;
        // 预览色块位置由 extractRenderState 绘制（y 此时为预览色块顶部）
        previewY = y;
        y += PREVIEW_HEIGHT + 10;
        // 5. 心动模式（根据心率自动变色，放在显示图标上方）
        // 主开关（左侧）+ 自定义按钮（右侧，打开子菜单选择图标/心率/BPM 各自是否变色）
        final int HEART_MODE_TOGGLE_WIDTH = 240;
        final int HEART_MODE_BTN_WIDTH = 36;
        final int HEART_MODE_BTN_GAP = 4;
        Button heartModeBtn = Button.builder(
                Component.literal("心动模式: " + (config.isHeartRateColorMode() ? "开" : "关")),
                b -> {
                    boolean current = b.getMessage().getString().endsWith("开");
                    boolean next = !current;
                    b.setMessage(Component.literal("心动模式: " + (next ? "开" : "关")));
                    config.setHeartRateColorMode(next);
                    saveConfig();
                }
        ).bounds(controlX, y, HEART_MODE_TOGGLE_WIDTH, CONTROL_HEIGHT).build();
        heartModeBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("开启后心率图标和数值根据心率自动变色：蓝(<60)→绿(60-100)→黄(100-140)→红(>140)")));
        addRenderableWidget(heartModeBtn);
        // 自定义按钮（打开子菜单）
        addRenderableWidget(Button.builder(
                Component.literal("自定义"),
                btn -> {
                    ModLogger.info("[Settings] 打开心动模式自定义子菜单");
                    Minecraft.getInstance().setScreen(new HeartColorModeScreen(this));
                }
        ).bounds(controlX + HEART_MODE_TOGGLE_WIDTH + HEART_MODE_BTN_GAP, y,
                HEART_MODE_BTN_WIDTH, CONTROL_HEIGHT).build());
        y += ROW_GAP;
        // 5b. 显示图标
        addRenderableWidget(createToggle("显示图标", config.isShowIcon(), v -> {
            config.setShowIcon(v);
            saveConfig();
        }, controlX, y));
        y += ROW_GAP;
        // 6. 显示设备名称
        addRenderableWidget(createToggle("显示设备名称", config.isShowDeviceName(), v -> {
            config.setShowDeviceName(v);
            saveConfig();
        }, controlX, y));
        y += ROW_GAP;
        // 6b. 显示 BPM 后缀
        addRenderableWidget(createToggle("显示 BPM 后缀", config.isShowBpmSuffix(), v -> {
            config.setShowBpmSuffix(v);
            saveConfig();
        }, controlX, y));
        y += ROW_GAP;
        // 7. 返回按钮
        addRenderableWidget(Button.builder(
                Component.literal("返回"),
                btn -> onClose()
        ).bounds(controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT).build());

        // 计算内容总高度（用于滚动范围限制）
        contentHeight = (y + scrollOffset) - baseY + CONTROL_HEIGHT + CONTENT_BOTTOM_PADDING;
        // 限制 scrollOffset 在有效范围内
        int maxScroll = getMaxScroll();
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
    }

    /** 可视区域高度（面板高度减去标题和底部边距） */
    private int getVisibleHeight() {
        return panelHeight - CONTENT_TOP_OFFSET - CONTENT_BOTTOM_PADDING;
    }

    /** 最大滚动偏移量 */
    private int getMaxScroll() {
        return Math.max(0, contentHeight - getVisibleHeight());
    }

    /**
     * 鼠标滚动处理：更新滚动偏移并重建控件位置。
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        saveDeviceNameIfChanged();
        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            // scrollY: 向上滚动为正，向下为负；滚动一格约 20px
            int delta = (int) Math.round(scrollY * 20);
            int newOffset = Math.max(0, Math.min(scrollOffset - delta, maxScroll));
            if (newOffset != scrollOffset) {
                scrollOffset = newOffset;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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

    private static void saveConfig() {
        ConfigManager.save();
    }

    private void saveDeviceNameIfChanged() {
        if (deviceNameInput != null) {
            ModConfig config = ConfigManager.getConfig();
            String newName = deviceNameInput.getValue().trim();
            if (!newName.equals(config.getCustomDeviceName())) {
                config.setCustomDeviceName(newName);
                saveConfig();
            }
        }
    }

    /**
     * 更新预览颜色（由 ColorChannelSlider 调用）。
     */
    public void updatePreview(int channel, float value) {
        if (channel >= 0 && channel < 4) {
            rgbaPreview[channel] = value;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        saveDeviceNameIfChanged();
        // 背景遮罩 + 面板
        graphics.fill(0, 0, this.width, this.height, 0x80000000);
        MainScreen.fillRoundedPanel(graphics, panelX, panelY, PANEL_WIDTH, panelHeight, CORNER_RADIUS);

        int centerX = panelX + PANEL_WIDTH / 2;
        Font modFont = ModFontManager.getFont();
        float guiScale = (float) ConfigManager.getConfig().getGuiFontScale();
        // 标题（不裁剪，始终可见）
        if (guiScale != 1.0f) {
            org.joml.Matrix3x2fStack stack = graphics.pose();
            stack.pushMatrix();
            stack.translate(centerX, panelY + 12);
            stack.scale(guiScale, guiScale);
            ModFontManager.centeredText(graphics, modFont, "GUI 设置", 0, 0, 0xFFFFD700);
            stack.popMatrix();
        } else {
            ModFontManager.centeredText(graphics, modFont, "GUI 设置", centerX, panelY + 12, 0xFFFFD700);
        }

        // 启用 scissor 裁剪面板内容区域，避免滚动时内容溢出面板
        int clipTop = panelY + CONTENT_TOP_OFFSET - 6;
        int clipBottom = panelY + panelHeight - 4;
        graphics.enableScissor(panelX + 2, clipTop, panelX + PANEL_WIDTH - 2, clipBottom);

        // 渲染所有 widget（slider、toggle、输入框）
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        // 绘制颜色预览色块（在 RGB slider 下方）
        int controlX = panelX + (PANEL_WIDTH - CONTROL_WIDTH) / 2;
        int previewX = controlX;
        int previewWidth = CONTROL_WIDTH;
        // 预览背景（边框）
        graphics.fill(previewX - 1, previewY - 1, previewX + previewWidth + 1, previewY + PREVIEW_HEIGHT + 1, 0xFF444444);
        // 预览内容（RGBA 颜色）
        int argb = colorToArgb(rgbaPreview, 1.0f);
        graphics.fill(previewX, previewY, previewX + previewWidth, previewY + PREVIEW_HEIGHT, argb);
        // 预览标签（居中显示在色块上）
        String previewLabel = "预览  R:" + Math.round(rgbaPreview[0] * 255)
                + " G:" + Math.round(rgbaPreview[1] * 255)
                + " B:" + Math.round(rgbaPreview[2] * 255)
                + " A:" + Math.round(rgbaPreview[3] * 255);
        // 根据亮度选择文字颜色（黑/白）
        float brightness = (rgbaPreview[0] * 0.299f + rgbaPreview[1] * 0.587f + rgbaPreview[2] * 0.114f) * rgbaPreview[3];
        int labelColor = brightness > 0.5f ? 0xFF000000 : 0xFFFFFFFF;
        graphics.centeredText(this.font, previewLabel, centerX, previewY + (PREVIEW_HEIGHT - 8) / 2, labelColor);

        // 禁用 scissor
        graphics.disableScissor();

        // 渲染滚动条（如果内容超出可视区域）
        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            int scrollBarX = panelX + PANEL_WIDTH - 7;
            int scrollBarTop = panelY + CONTENT_TOP_OFFSET;
            int scrollBarBottom = panelY + panelHeight - 6;
            int scrollTrackHeight = scrollBarBottom - scrollBarTop;
            // 轨道背景
            graphics.fill(scrollBarX, scrollBarTop, scrollBarX + 3, scrollBarBottom, 0x40808080);
            // 滑块
            int visibleHeight = getVisibleHeight();
            int thumbHeight = Math.max(10, scrollTrackHeight * visibleHeight / contentHeight);
            int thumbY = scrollBarTop + (scrollTrackHeight - thumbHeight) * scrollOffset / maxScroll;
            graphics.fill(scrollBarX, thumbY, scrollBarX + 3, thumbY + thumbHeight, 0xFFCCCCCC);
        }
    }

    @Override
    public void onClose() {
        saveDeviceNameIfChanged();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ====== 内部 Slider 实现 ======

    private static class OpacitySlider extends AbstractSliderButton {
        private static final String LABEL = "GUI 透明度: ";

        OpacitySlider(int x, int y, int width, int height, float value) {
            super(x, y, width, height, Component.literal(LABEL + percent(value)), clamp01(value));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(LABEL + percent((float) this.value)));
        }

        @Override
        protected void applyValue() {
            ModConfig config = ConfigManager.getConfig();
            config.setGuiOpacity((float) clamp01(this.value));
            ConfigManager.save();
        }

        private static String percent(float v) {
            return Math.round(clamp01(v) * 100) + "%";
        }
    }

    /**
     * 字体颜色通道滑块。滑动时同时更新预览。
     */
    private static class ColorChannelSlider extends AbstractSliderButton {
        private static final String[] LABELS = {"R", "G", "B", "A"};
        private final int channel;
        private final SettingScreen parentScreen;

        ColorChannelSlider(int x, int y, int width, int height, int channel, float normalized, SettingScreen parentScreen) {
            super(x, y, width, height, Component.literal(LABELS[channel] + ":" + toInt(normalized)), clamp01(normalized));
            this.channel = channel;
            this.parentScreen = parentScreen;
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(LABELS[channel] + ":" + toInt(this.value)));
        }

        @Override
        protected void applyValue() {
            float v = (float) clamp01(this.value);
            ModConfig config = ConfigManager.getConfig();
            float[] rgba = config.getFontColor();
            if (rgba == null || rgba.length < 4) {
                rgba = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
            } else {
                rgba = new float[]{rgba[0], rgba[1], rgba[2], rgba[3]};
            }
            rgba[channel] = v;
            config.setFontColor(rgba);
            ConfigManager.save();
            // 实时更新预览
            parentScreen.updatePreview(channel, v);
        }

        private static int toInt(double v) {
            return Math.round((float) clamp01(v) * 255);
        }
    }

    private static int colorToArgb(float[] rgba, float opacity) {
        int r = Math.round(clamp01(rgba[0]) * 255);
        int g = Math.round(clamp01(rgba[1]) * 255);
        int b = Math.round(clamp01(rgba[2]) * 255);
        int a = Math.round(clamp01(rgba[3]) * opacity * 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }
}
