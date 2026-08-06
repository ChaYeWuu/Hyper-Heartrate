package com.chayewuu.hyperheartrate.gui;

import com.chayewuu.hyperheartrate.config.ConfigManager;
import com.chayewuu.hyperheartrate.config.ModConfig;
import com.chayewuu.hyperheartrate.util.ModLogger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * 联机功能设置界面。
 * <p>
 * 配置联机心率同步的显示选项：开关、NameTag 位置（上方/下方）、图标/BPM 显隐、心动模式、TAB 列表。
 * 支持垂直滚动（当屏幕高度不足时）。
 * </p>
 */
public class MultiplayerSettingsScreen extends Screen {
    private static final int PANEL_WIDTH = 340;
    private static final int MIN_PANEL_HEIGHT = 200;
    private static final int MAX_PANEL_HEIGHT = 350;
    private static final int CORNER_RADIUS = 14;
    private static final int CONTROL_WIDTH = 280;
    private static final int CONTROL_HEIGHT = 20;
    private static final int ROW_GAP = 26;
    private static final int CONTENT_TOP_OFFSET = 40;
    private static final int CONTENT_BOTTOM_PADDING = 10;

    private final Screen parent;
    private int panelHeight;
    private int panelX;
    private int panelY;

    private int scrollOffset = 0;
    private int contentHeight = 0;

    public MultiplayerSettingsScreen(Screen parent) {
        super(Text.literal("联机功能设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // 基于内容计算动态高度：11 个控件(10 个 ROW_GAP 间隔 + 1 个额外 4px) + 标题 + 底部间距
        // 额外 4px 在 TAB 分隔前
        int controlRows = 11;
        int extraGap = 4;
        int contentOnlyHeight = CONTENT_TOP_OFFSET + ROW_GAP * (controlRows - 1) + extraGap + CONTROL_HEIGHT + CONTENT_BOTTOM_PADDING;
        int calculatedHeight = Math.max(MIN_PANEL_HEIGHT, Math.min(MAX_PANEL_HEIGHT, contentOnlyHeight));
        panelHeight = Math.min(calculatedHeight, this.height - 20);
        panelY = (this.height - panelHeight) / 2;
        if (panelY < 10) {
            panelY = 10;
        }
        panelX = (this.width - PANEL_WIDTH) / 2;

        if (panelHeight >= calculatedHeight) {
            scrollOffset = 0;
        }

        int controlX = panelX + (PANEL_WIDTH - CONTROL_WIDTH) / 2;
        ModConfig config = ConfigManager.getConfig();

        int baseY = panelY + CONTENT_TOP_OFFSET;
        int y = baseY - scrollOffset;

        // 1. 联机功能开关
        addDrawableChild(createToggle("联机功能", config.isMultiplayerEnabled(), v -> {
            config.setMultiplayerEnabled(v);
            ConfigManager.save();
        }, controlX, y));
        y += ROW_GAP;

        // 2. NameTag 显示位置（上方/内部/下方，点击循环切换）
        addDrawableChild(ButtonWidget.builder(
                Text.literal("显示位置: " + positionLabel(config.getMultiplayerNametagPosition())),
                b -> {
                    String current = config.getMultiplayerNametagPosition();
                    String next = "above".equals(current) ? "inside" : ("inside".equals(current) ? "below" : "above");
                    b.setMessage(Text.literal("显示位置: " + positionLabel(next)));
                    config.setMultiplayerNametagPosition(next);
                    ConfigManager.save();
                }
        ).dimensions(controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT).build());
        y += ROW_GAP;

        // 3. 显示心率图标
        addDrawableChild(createToggle("显示心率图标", config.isMultiplayerShowIcon(), v -> {
            config.setMultiplayerShowIcon(v);
            ConfigManager.save();
        }, controlX, y));
        y += ROW_GAP;

        // 4. 显示 BPM
        addDrawableChild(createToggle("显示 BPM", config.isMultiplayerShowBpm(), v -> {
            config.setMultiplayerShowBpm(v);
            ConfigManager.save();
        }, controlX, y));
        y += ROW_GAP;

        // 5. 心动模式开关 + 自定义按钮
        ButtonWidget heartModeBtn = ButtonWidget.builder(
                Text.literal("心动模式: " + (config.isMultiplayerHeartColorMode() ? "开" : "关")),
                b -> {
                    boolean current = b.getMessage().getString().endsWith("开");
                    boolean next = !current;
                    b.setMessage(Text.literal("心动模式: " + (next ? "开" : "关")));
                    config.setMultiplayerHeartColorMode(next);
                    ConfigManager.save();
                }
        ).dimensions(controlX, y, 240, CONTROL_HEIGHT).build();
        heartModeBtn.setTooltip(Tooltip.of(
                Text.literal("开启后心率根据值自动变色：蓝(<60)→绿(60-100)→黄(100-140)→红(>140)")));
        addDrawableChild(heartModeBtn);
        addDrawableChild(ButtonWidget.builder(
                Text.literal("自定义"),
                btn -> {
                    ModLogger.info("[MultiplayerSettings] 打开心动模式自定义");
                    MinecraftClient.getInstance().setScreen(new MultiplayerHeartColorScreen(this));
                }
        ).dimensions(controlX + 244, y, 36, CONTROL_HEIGHT).build());
        y += ROW_GAP;

        // 分隔：TAB 列表心率
        y += 4;
        // 6. TAB 列表显示心率开关
        addDrawableChild(createToggle("TAB列表显示心率", config.isMultiplayerTabListEnabled(), v -> {
            config.setMultiplayerTabListEnabled(v);
            ConfigManager.save();
        }, controlX, y));
        y += ROW_GAP;

        // 7. TAB 显示心率图标
        addDrawableChild(createToggle("TAB显示心率图标", config.isMultiplayerTabListShowIcon(), v -> {
            config.setMultiplayerTabListShowIcon(v);
            ConfigManager.save();
        }, controlX, y));
        y += ROW_GAP;

        // 8. TAB 显示心率数值
        addDrawableChild(createToggle("TAB显示心率数值", config.isMultiplayerTabListShowRate(), v -> {
            config.setMultiplayerTabListShowRate(v);
            ConfigManager.save();
        }, controlX, y));
        y += ROW_GAP;

        // 9. TAB 显示 BPM
        addDrawableChild(createToggle("TAB显示BPM", config.isMultiplayerTabListShowBpm(), v -> {
            config.setMultiplayerTabListShowBpm(v);
            ConfigManager.save();
        }, controlX, y));
        y += ROW_GAP;

        // 10. 返回
        addDrawableChild(ButtonWidget.builder(
                Text.literal("返回"),
                btn -> close()
        ).dimensions(controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT).build());

        contentHeight = CONTENT_TOP_OFFSET + ROW_GAP * (controlRows - 1) + extraGap + CONTROL_HEIGHT + CONTENT_BOTTOM_PADDING;
        int maxScroll = getMaxScroll();
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
    }

    private int getVisibleHeight() {
        return panelHeight - CONTENT_TOP_OFFSET - CONTENT_BOTTOM_PADDING;
    }

    private int getMaxScroll() {
        return Math.max(0, contentHeight - getVisibleHeight());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            int delta = (int) Math.round(verticalAmount * 20);
            int newOffset = Math.max(0, Math.min(scrollOffset - delta, maxScroll));
            if (newOffset != scrollOffset) {
                scrollOffset = newOffset;
                clearAndInit();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x80000000);
        MainScreen.fillRoundedPanel(context, panelX, panelY, PANEL_WIDTH, panelHeight, CORNER_RADIUS);

        int centerX = panelX + PANEL_WIDTH / 2;
        ModFontManager.centeredText(context, ModFontManager.getFont(), "联机功能设置", centerX, panelY + 14, 0xFFFFD700);
        ModFontManager.centeredText(context, ModFontManager.getFont(), "NameTag 上方/内部/下方显示心率", centerX, panelY + 26, 0xFF888888);

        int clipTop = panelY + CONTENT_TOP_OFFSET - 6;
        int clipBottom = panelY + panelHeight - 4;
        context.enableScissor(panelX + 2, clipTop, panelX + PANEL_WIDTH - 2, clipBottom);

        super.render(context, mouseX, mouseY, delta);

        context.disableScissor();

        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            int scrollBarX = panelX + PANEL_WIDTH - 7;
            int scrollBarTop = panelY + CONTENT_TOP_OFFSET;
            int scrollBarBottom = panelY + panelHeight - 6;
            int scrollTrackHeight = scrollBarBottom - scrollBarTop;
            context.fill(scrollBarX, scrollBarTop, scrollBarX + 3, scrollBarBottom, 0x40808080);
            int visibleHeight = getVisibleHeight();
            int thumbHeight = Math.max(10, scrollTrackHeight * visibleHeight / contentHeight);
            int thumbY = scrollBarTop + (scrollTrackHeight - thumbHeight) * scrollOffset / maxScroll;
            context.fill(scrollBarX, thumbY, scrollBarX + 3, thumbY + thumbHeight, 0xFFCCCCCC);
        }
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

    /** 位置枚举值 → 显示文本 */
    private static String positionLabel(String position) {
        if ("above".equals(position)) return "NameTag上方";
        if ("inside".equals(position)) return "NameTag内部";
        return "NameTag下方";
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
