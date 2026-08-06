package com.chayewuu.hyperheartrate.gui;

import com.chayewuu.hyperheartrate.config.ConfigManager;
import com.chayewuu.hyperheartrate.config.ModConfig;
import com.chayewuu.hyperheartrate.network.HttpServerManager;
import com.chayewuu.hyperheartrate.util.ModLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * API 设置界面。
 * <p>
 * 配置 HTTP API（Browser API）与 Mod API 的开关、端口、绑定地址等。
 * 支持垂直滚动（当屏幕高度不足时）。
 * </p>
 */
public class ApiSettingsScreen extends Screen {
    private static final int PANEL_WIDTH = 380;
    private static final int MIN_PANEL_HEIGHT = 200;
    private static final int MAX_PANEL_HEIGHT = 260;
    private static final int CORNER_RADIUS = 14;
    private static final int CONTROL_WIDTH = 320;
    private static final int CONTROL_HEIGHT = 20;
    private static final int MOD_API_TOGGLE_WIDTH = 284;
    private static final int HELP_BTN_WIDTH = 32;
    private static final int HELP_BTN_GAP = 4;
    private static final int ROW_GAP = 30;
    private static final int CONTENT_TOP_OFFSET = 36;
    /** 底部固定区域高度（用于 HTTP 地址显示） */
    private static final int BOTTOM_FIXED_HEIGHT = 28;
    private static final int CONTENT_BOTTOM_PADDING = 10;

    private static final int HELP_POPUP_WIDTH = 360;
    private static final int HELP_POPUP_HEIGHT = 200;

    private final Screen parent;

    private EditBox portInputBox;
    private EditBox bindAddrInputBox;

    private int panelX;
    private int panelY;
    private int panelHeight = MAX_PANEL_HEIGHT;
    private boolean showModApiHelp = false;
    private int popupX;
    private int popupY;

    private int scrollOffset = 0;
    private int contentHeight = 0;

    public ApiSettingsScreen(Screen parent) {
        super(Component.literal("API 设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // 基于内容计算动态高度：6 个控件(5 个 ROW_GAP 间隔) + 标题 + 底部固定 + 底部间距
        int controlRows = 6;
        int contentOnlyHeight = CONTENT_TOP_OFFSET + ROW_GAP * (controlRows - 1) + CONTROL_HEIGHT + BOTTOM_FIXED_HEIGHT + CONTENT_BOTTOM_PADDING;
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

        popupX = (this.width - HELP_POPUP_WIDTH) / 2;
        popupY = (this.height - HELP_POPUP_HEIGHT) / 2;
        int controlX = panelX + (PANEL_WIDTH - CONTROL_WIDTH) / 2;
        ModConfig config = ConfigManager.getConfig();

        int baseY = panelY + CONTENT_TOP_OFFSET;
        int y = baseY - scrollOffset;

        addRenderableWidget(createToggle("HTTP API (Browser)", config.isHttpApiEnabled(), v -> {
            config.setHttpApiEnabled(v);
            saveConfig();
            HttpServerManager httpMgr = HttpServerManager.getInstance();
            if (v) {
                httpMgr.start();
            } else {
                httpMgr.stop();
            }
            ModLogger.info("[ApiSettings] HTTP API 已{}", v ? "开启" : "关闭");
        }, controlX, y, CONTROL_WIDTH));
        y += ROW_GAP;

        addRenderableWidget(createToggle("Mod API", config.isModApiEnabled(), v -> {
            config.setModApiEnabled(v);
            saveConfig();
            ModLogger.info("[ApiSettings] Mod API 已{}", v ? "开启" : "关闭");
        }, controlX, y, MOD_API_TOGGLE_WIDTH));
        Button helpBtn = Button.builder(
                Component.literal("?"),
                btn -> {
                    showModApiHelp = true;
                    ModLogger.info("[ApiSettings] 打开 Mod API 说明弹窗");
                    rebuildWidgets();
                }
        ).bounds(controlX + MOD_API_TOGGLE_WIDTH + HELP_BTN_GAP, y, HELP_BTN_WIDTH, CONTROL_HEIGHT).build();
        addRenderableWidget(helpBtn);
        y += ROW_GAP;

        Integer port = config.getHttpPort();
        portInputBox = new EditBox(this.font, controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT, Component.literal("端口"));
        portInputBox.setMaxLength(5);
        portInputBox.setValue(port != null ? String.valueOf(port) : "");
        portInputBox.setHint(Component.literal("HTTP 端口（留空=随机）"));
        addRenderableWidget(portInputBox);
        y += ROW_GAP;

        bindAddrInputBox = new EditBox(this.font, controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT, Component.literal("地址"));
        bindAddrInputBox.setMaxLength(15);
        bindAddrInputBox.setValue(config.getBindAddress() != null ? config.getBindAddress() : "127.0.0.1");
        bindAddrInputBox.setHint(Component.literal("绑定地址（如 127.0.0.1 / 0.0.0.0）"));
        addRenderableWidget(bindAddrInputBox);
        y += ROW_GAP;

        addRenderableWidget(Button.builder(
                Component.literal("应用并重启 HTTP Server"),
                btn -> onApplyClicked()
        ).bounds(controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT).build());
        y += ROW_GAP;

        addRenderableWidget(Button.builder(
                Component.literal("返回"),
                btn -> onClose()
        ).bounds(controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT).build());

        contentHeight = (y + scrollOffset) - baseY + CONTROL_HEIGHT + BOTTOM_FIXED_HEIGHT + CONTENT_BOTTOM_PADDING;
        int maxScroll = getMaxScroll();
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }

        if (showModApiHelp) {
            popupX = (this.width - HELP_POPUP_WIDTH) / 2;
            popupY = (this.height - HELP_POPUP_HEIGHT) / 2;
            int closeBtnX = popupX + (HELP_POPUP_WIDTH - 120) / 2;
            int closeBtnY = popupY + HELP_POPUP_HEIGHT - 28;
            addRenderableWidget(Button.builder(
                    Component.literal("关闭说明"),
                    btn -> {
                        showModApiHelp = false;
                        ModLogger.info("[ApiSettings] 关闭 Mod API 说明弹窗");
                        rebuildWidgets();
                    }
            ).bounds(closeBtnX, closeBtnY, 120, CONTROL_HEIGHT).build());
        }
    }

    private int getVisibleHeight() {
        return panelHeight - CONTENT_TOP_OFFSET - BOTTOM_FIXED_HEIGHT;
    }

    private int getMaxScroll() {
        return Math.max(0, contentHeight - getVisibleHeight());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (showModApiHelp) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
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

    private void onApplyClicked() {
        ModConfig config = ConfigManager.getConfig();
        String portStr = portInputBox.getValue().trim();
        if (portStr.isEmpty()) {
            config.setHttpPort(null);
        } else {
            try {
                int port = Integer.parseInt(portStr);
                if (port >= 1 && port <= 65535) {
                    config.setHttpPort(port);
                } else {
                    ModLogger.warn("[ApiSettings] 端口超出范围: {}", port);
                    config.setHttpPort(null);
                }
            } catch (NumberFormatException e) {
                ModLogger.warn("[ApiSettings] 端口格式无效: {}", portStr);
                config.setHttpPort(null);
            }
        }
        String addr = bindAddrInputBox.getValue().trim();
        if (!addr.isEmpty()) {
            config.setBindAddress(addr);
        }
        saveConfig();

        HttpServerManager httpMgr = HttpServerManager.getInstance();
        httpMgr.stop();
        if (config.isHttpApiEnabled()) {
            httpMgr.start();
        }
        ModLogger.info("[ApiSettings] HTTP Server 已重启: {}", httpMgr.getAddress());
    }

    private Button createToggle(String label, boolean initial, java.util.function.Consumer<Boolean> onChange,
                                int x, int y, int width) {
        return Button.builder(
                Component.literal(label + ": " + (initial ? "开" : "关")),
                b -> {
                    boolean current = b.getMessage().getString().endsWith("开");
                    boolean next = !current;
                    b.setMessage(Component.literal(label + ": " + (next ? "开" : "关")));
                    onChange.accept(next);
                }
        ).bounds(x, y, width, CONTROL_HEIGHT).build();
    }

    private static void saveConfig() {
        ConfigManager.save();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0x80000000);
        MainScreen.fillRoundedPanel(graphics, panelX, panelY, PANEL_WIDTH, panelHeight, CORNER_RADIUS);

        int centerX = panelX + PANEL_WIDTH / 2;
        graphics.centeredText(this.font, "API 设置", centerX, panelY + 12, 0xFFFFD700);

        // 当前 HTTP 地址显示（面板底部固定位置，不随滚动）
        String addr = HttpServerManager.getInstance().getAddress();
        graphics.centeredText(this.font, "当前: " + addr, centerX, panelY + panelHeight - 18, 0xFF66CCFF);

        // 裁剪面板内容区域
        int clipTop = panelY + CONTENT_TOP_OFFSET - 6;
        int clipBottom = panelY + panelHeight - BOTTOM_FIXED_HEIGHT + 4;
        graphics.enableScissor(panelX + 2, clipTop, panelX + PANEL_WIDTH - 2, clipBottom);

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.disableScissor();

        // 滚动条
        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            int scrollBarX = panelX + PANEL_WIDTH - 7;
            int scrollBarTop = panelY + CONTENT_TOP_OFFSET;
            int scrollBarBottom = panelY + panelHeight - BOTTOM_FIXED_HEIGHT - CONTENT_BOTTOM_PADDING;
            int scrollTrackHeight = scrollBarBottom - scrollBarTop;
            graphics.fill(scrollBarX, scrollBarTop, scrollBarX + 3, scrollBarBottom, 0x40808080);
            int visibleHeight = getVisibleHeight();
            int thumbHeight = Math.max(10, scrollTrackHeight * visibleHeight / contentHeight);
            int thumbY = scrollBarTop + (scrollTrackHeight - thumbHeight) * scrollOffset / maxScroll;
            graphics.fill(scrollBarX, thumbY, scrollBarX + 3, thumbY + thumbHeight, 0xFFCCCCCC);
        }

        if (showModApiHelp) {
            renderHelpPopup(graphics, mouseX, mouseY);
        }
    }

    private void renderHelpPopup(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, this.width, this.height, 0x99000000);
        MainScreen.fillRoundedPanel(graphics, popupX, popupY, HELP_POPUP_WIDTH, HELP_POPUP_HEIGHT, CORNER_RADIUS);

        int centerX = popupX + HELP_POPUP_WIDTH / 2;
        graphics.centeredText(this.font, "Mod API 接口说明", centerX, popupY + 10, 0xFFFFD700);

        int textX = popupX + 12;
        int textY = popupY + 28;
        int codeColor = 0xFF66FFAA;
        int textColor = 0xFFCCCCCC;

        String[] lines = {
                "HeartRateAPI.getHeartRate()",
                "  → 当前心率 (int, 0=未连接)",
                "HeartRateAPI.isConnected()",
                "  → 设备是否已连接 (boolean)",
                "HeartRateAPI.getCurrentDevice()",
                "  → 当前设备 (BleDevice)",
                "HeartRateAPI.addListener(listener)",
                "  → 监听心率变化/连接事件",
                "HeartRateAPI.getHistory(count)",
                "  → 获取最近 count 条心率历史"
        };
        for (String line : lines) {
            int color = line.startsWith("  ") ? textColor : codeColor;
            graphics.text(this.font, line, textX, textY, color, false);
            textY += 12;
        }
        textY += 4;
        graphics.text(this.font, "包路径: com.chayewuu.hyperheartrate.api", textX, textY, textColor, false);
        textY += 12;
        graphics.text(this.font, "用法: 引入本 Mod 依赖后直接调用静态方法", textX, textY, textColor, false);
    }

    @Override
    public void onClose() {
        if (showModApiHelp) {
            showModApiHelp = false;
            rebuildWidgets();
            return;
        }
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
