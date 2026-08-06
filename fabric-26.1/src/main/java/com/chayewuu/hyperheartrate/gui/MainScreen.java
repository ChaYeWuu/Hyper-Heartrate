package com.chayewuu.hyperheartrate.gui;

import com.chayewuu.hyperheartrate.HeartRateMod;
import com.chayewuu.hyperheartrate.config.ConfigManager;
import com.chayewuu.hyperheartrate.config.ModConfig;
import com.chayewuu.hyperheartrate.device.BleDevice;
import com.chayewuu.hyperheartrate.device.DeviceManagerHolder;
import com.chayewuu.hyperheartrate.heart.HeartRateManager;
import com.chayewuu.hyperheartrate.network.HttpServerManager;
import com.chayewuu.hyperheartrate.util.ModLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Desktop;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Hyper Heartrate 主界面。
 * <p>
 * 动态布局：面板高度根据实际显示内容自动计算，保证不空旷也不拥挤。
 * HUD 开关对齐 HTTP 行右侧，作者在 HTTP 下方。
 * </p>
 *
 * <p><b>布局结构：</b></p>
 * <pre>
 * ┌──────────────────────────────────────┐
 * │       Hyper Heartrate v1.0.0       │  标题
 * │ [连接设备]    [断开连接]             │  按钮行 1
 * │ [模组设置]    [联机功能]             │  按钮行 2
 * │ [B站主页]    [项目主页]              │  按钮行 3
 * │ ─────────────────────────────────── │  分隔线
 * │ 设备名称: xxx   MAC: xx:xx:xx:xx     │  设备信息
 * │ 连接状态: ●已连接  RSSI: -60 dBm     │
 * │ 心率: ♥ 76 BPM  更新时间: 12:34:56   │
 * │ HTTP: 127.0.0.1  [HUD: 开]         │  HTTP行 + HUD开关
 * │ 作者@茶叶Wuu                         │  作者
 * └──────────────────────────────────────┘
 * </pre>
 *
 * <p>按键 {@code H} 打开本界面；按 {@code ESC} 关闭。</p>
 */
public class MainScreen extends Screen {
    /** 主面板宽度 */
    private static final int PANEL_WIDTH = 360;
    /** 最小面板高度（按钮区 + 最小信息区） */
    private static final int MIN_PANEL_HEIGHT = 180;
    /** 最大面板高度 */
    private static final int MAX_PANEL_HEIGHT = 260;
    /** 圆角半径 */
    private static final int CORNER_RADIUS = 14;
    /** 按钮宽度（2 列布局） */
    private static final int BUTTON_WIDTH = 160;
    /** 按钮高度 */
    private static final int BUTTON_HEIGHT = 20;
    /** 按钮列间距 */
    private static final int BUTTON_GAP = 16;
    /** 按钮行间距 */
    private static final int BUTTON_ROW_GAP = 6;
    /** 信息区行距 */
    private static final int INFO_ROW_HEIGHT = 13;
    /** HUD 开关按钮宽度 */
    private static final int HUD_TOGGLE_WIDTH = 70;
    /** HUD 开关按钮高度 */
    private static final int HUD_TOGGLE_HEIGHT = 16;
    /** 面板底部内边距 */
    private static final int BOTTOM_PADDING = 14;

    /** 像素心形图标颜色（红色） */
    private static final int HEART_COLOR = 0xFFFF4060;
    /** 连接状态圆点（非 emoji，使用 Unicode 实心圆 U+25CF） */
    private static final String DOT_CONNECTED = "\u25CF";

    // === 按钮 ===
    private Button connectButton;
    private Button disconnectButton;
    /** 模组设置按钮（打开子菜单：GUI设置/心率设置/API设置） */
    private Button modSettingsButton;
    private Button homepageButton;
    /** 联机功能按钮 */
    private Button multiplayerButton;
    /** B站主页按钮 */
    private Button bilibiliButton;
    /** HUD 总开关按钮（HTTP 行右侧，控制游戏内心率 HUD 显隐） */
    private Button hudToggleButton;

    /** 主面板左上角 X */
    private int panelX;
    /** 主面板左上角 Y */
    private int panelY;
    /** 主面板高度（动态计算） */
    private int panelHeight;

    /** HTTP 行 Y 坐标（用于 HUD 按钮定位和渲染） */
    private int httpRowY;
    /** 作者行 Y 坐标 */
    private int authorRowY;

    public MainScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        // 计算动态面板高度
        ModConfig config = ConfigManager.getConfig();
        HeartRateManager mgr = HeartRateManager.getInstance();
        boolean connected = mgr.isConnected();

        // 按钮区高度
        int buttonsHeight = BUTTON_HEIGHT * 3 + BUTTON_ROW_GAP * 2;
        // 按钮区起始 Y 到底部
        int topToButtonsEnd = 30 + buttonsHeight;
        // 按钮到分隔线
        int dividerGap = 6;
        // 分隔线到信息区
        int infoStartGap = 6;
        // 信息区（3 行设备信息 + 1 行 HTTP/HUD + 1 行作者 = 5 行）
        int infoRows = 5;
        int infoHeight = infoRows * INFO_ROW_HEIGHT;
        // 底部内边距
        int bottomPad = BOTTOM_PADDING;

        int calculatedHeight = 12 + 18 + buttonsHeight + dividerGap + infoStartGap + infoHeight + bottomPad;
        panelHeight = Math.max(MIN_PANEL_HEIGHT, Math.min(MAX_PANEL_HEIGHT, calculatedHeight));

        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - panelHeight) / 2;

        // 按钮区起始 Y（标题下方）
        int buttonsStartY = panelY + 30;
        int leftButtonX = panelX + 12;
        int rightButtonX = panelX + PANEL_WIDTH - BUTTON_WIDTH - 12;

        // 第 1 行：连接设备 | 断开连接
        connectButton = Button.builder(
                Component.literal(getConnectButtonText()),
                btn -> onConnectClicked()
        ).bounds(leftButtonX, buttonsStartY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        addRenderableWidget(connectButton);

        disconnectButton = Button.builder(
                Component.literal("断开连接"),
                btn -> onDisconnectClicked()
        ).bounds(rightButtonX, buttonsStartY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        addRenderableWidget(disconnectButton);

        // 第 2 行：模组设置 | 联机功能
        int row2Y = buttonsStartY + BUTTON_HEIGHT + BUTTON_ROW_GAP;
        modSettingsButton = Button.builder(
                Component.literal("模组设置"),
                btn -> openModSettings()
        ).bounds(leftButtonX, row2Y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        addRenderableWidget(modSettingsButton);

        multiplayerButton = Button.builder(
                Component.literal("联机功能"),
                btn -> openMultiplayerSettings()
        ).bounds(rightButtonX, row2Y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        addRenderableWidget(multiplayerButton);

        // 第 3 行：B站主页 | 项目主页
        int row3Y = row2Y + BUTTON_HEIGHT + BUTTON_ROW_GAP;
        bilibiliButton = Button.builder(
                Component.literal("B站主页"),
                btn -> openBilibili()
        ).bounds(leftButtonX, row3Y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        addRenderableWidget(bilibiliButton);

        homepageButton = Button.builder(
                Component.literal("项目主页"),
                btn -> openHomepage()
        ).bounds(rightButtonX, row3Y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        addRenderableWidget(homepageButton);

        // 计算 HTTP 行和作者行 Y 坐标
        int dividerY = panelY + 30 + buttonsHeight + dividerGap;
        int infoStartY = dividerY + infoStartGap;
        // 3 行设备信息
        httpRowY = infoStartY + INFO_ROW_HEIGHT * 3;
        authorRowY = httpRowY + INFO_ROW_HEIGHT;

        // HUD 开关按钮（HTTP 行右侧）
        hudToggleButton = Button.builder(
                Component.literal("HUD: " + (config.isHudEnabled() ? "开" : "关")),
                btn -> {
                    boolean current = btn.getMessage().getString().endsWith("开");
                    boolean next = !current;
                    btn.setMessage(Component.literal("HUD: " + (next ? "开" : "关")));
                    config.setHudEnabled(next);
                    ConfigManager.save();
                    ModLogger.info("[MainScreen] HUD 总开关: {}", next ? "开" : "关");
                }
        ).bounds(panelX + PANEL_WIDTH - HUD_TOGGLE_WIDTH - 12,
                httpRowY - (HUD_TOGGLE_HEIGHT - INFO_ROW_HEIGHT) / 2,
                HUD_TOGGLE_WIDTH, HUD_TOGGLE_HEIGHT).build();
        hudToggleButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("游戏内心率显示总开关")));
        addRenderableWidget(hudToggleButton);

        refreshButtonStates();
    }

    private void refreshButtonStates() {
        boolean connected = HeartRateManager.getInstance().isConnected();
        boolean scanning = DeviceManagerHolder.get().getConnectionState()
                == com.chayewuu.hyperheartrate.device.ConnectionState.SCANNING;
        boolean connecting = DeviceManagerHolder.get().getConnectionState()
                == com.chayewuu.hyperheartrate.device.ConnectionState.CONNECTING;

        if (connectButton != null) {
            connectButton.setMessage(Component.literal(getConnectButtonText()));
            connectButton.active = !connected && !scanning && !connecting;
        }
        if (disconnectButton != null) {
            disconnectButton.active = connected;
        }
    }

    private String getConnectButtonText() {
        HeartRateManager mgr = HeartRateManager.getInstance();
        if (mgr.isConnected()) {
            return DOT_CONNECTED + " 已连接";
        }
        com.chayewuu.hyperheartrate.device.ConnectionState state =
                DeviceManagerHolder.get().getConnectionState();
        if (state == com.chayewuu.hyperheartrate.device.ConnectionState.SCANNING) {
            return "扫描中...";
        }
        if (state == com.chayewuu.hyperheartrate.device.ConnectionState.CONNECTING) {
            return "连接中...";
        }
        return "连接设备";
    }

    private void onConnectClicked() {
        ModLogger.info("[MainScreen] 用户点击连接设备，打开设备选择界面");
        Minecraft.getInstance().setScreen(new DeviceSelectionScreen(this));
    }

    private void onDisconnectClicked() {
        ModLogger.info("[MainScreen] 用户点击断开设备");
        DeviceManagerHolder.get().disconnect();
        refreshButtonStates();
    }

    private void openModSettings() {
        ModLogger.info("[MainScreen] 用户点击 模组设置");
        Minecraft.getInstance().setScreen(new ModSettingsScreen(this));
    }

    private void openHomepage() {
        ModLogger.info("[MainScreen] 用户点击 项目主页");
        openUrlInBrowser("https://github.com/ChaYeWuu/Hyper-Heartrate");
    }

    private void openBilibili() {
        ModLogger.info("[MainScreen] 用户点击 B站主页");
        openUrlInBrowser("https://space.bilibili.com/698351214");
    }

    private void openMultiplayerSettings() {
        ModLogger.info("[MainScreen] 用户点击 联机功能");
        Minecraft.getInstance().setScreen(new MultiplayerSettingsScreen(this));
    }

    private void openUrlInBrowser(String url) {
        Thread browserThread = new Thread(() -> {
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                try {
                    ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start", "", url);
                    pb.redirectErrorStream(true);
                    Process p = pb.start();
                    int code = p.waitFor();
                    if (code == 0) {
                        ModLogger.info("[MainScreen] 已通过 cmd start 打开浏览器: {}", url);
                        return;
                    }
                } catch (Exception e) {
                    ModLogger.warn("[MainScreen] cmd start 失败: {}，尝试 Desktop.browse", e.getMessage());
                }
            }
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(url));
                    ModLogger.info("[MainScreen] 已在浏览器打开: {}", url);
                }
            } catch (Exception e) {
                ModLogger.error("[MainScreen] 打开浏览器失败: " + url, e);
            }
        }, "HeartRateMod-BrowserOpener");
        browserThread.setDaemon(true);
        browserThread.start();
    }

    @Override
    public void tick() {
        super.tick();
        refreshButtonStates();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics);
        renderPanel(graphics);
        renderContent(graphics);
        // 最后调用 super 渲染按钮（widgets），确保按钮在最上层
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void renderBackground(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, this.width, this.height, 0x80000000);
    }

    private void renderPanel(GuiGraphicsExtractor graphics) {
        fillRoundedPanel(graphics, panelX, panelY, PANEL_WIDTH, panelHeight, CORNER_RADIUS);
    }

    private void renderContent(GuiGraphicsExtractor graphics) {
        int centerX = panelX + PANEL_WIDTH / 2;
        Font modFont = ModFontManager.getFont();
        ModConfig config = ConfigManager.getConfig();

        // === 标题 ===
        String title = "Hyper Heartrate v" + HeartRateMod.MOD_VERSION;
        ModFontManager.centeredText(graphics, modFont, title, centerX, panelY + 12, 0xFFFFD700);

        // === 分隔线 ===
        int dividerY = panelY + 30 + BUTTON_HEIGHT * 3 + BUTTON_ROW_GAP * 2 + 6;
        graphics.fill(panelX + 16, dividerY, panelX + PANEL_WIDTH - 16, dividerY + 1, 0xFFA0A0A0);

        // === 设备信息区 ===
        HeartRateManager mgr = HeartRateManager.getInstance();
        BleDevice device = mgr.getCurrentDevice();
        boolean connected = mgr.isConnected();

        int labelColor = 0xFFAAAAAA;
        int valueColor = 0xFFFFFFFF;
        int col1X = panelX + 18;
        int col2X = panelX + 190;
        int infoY = dividerY + 6;

        // 第 1 行：设备名称 | MAC 地址
        ModFontManager.text(graphics, modFont, "设备名称:", col1X, infoY, labelColor, false);
        String displayDeviceName = "—";
        if (connected && device != null) {
            String customName = config.getCustomDeviceName();
            if (customName != null && !customName.isEmpty()) {
                displayDeviceName = customName;
            } else {
                displayDeviceName = safe(device.getName());
            }
        }
        ModFontManager.text(graphics, modFont, displayDeviceName, col1X + 54, infoY, valueColor, false);
        ModFontManager.text(graphics, modFont, "MAC:", col2X, infoY, labelColor, false);
        ModFontManager.text(graphics, modFont, connected && device != null ? safe(device.getAddress()) : "—",
                col2X + 30, infoY, valueColor, false);
        infoY += INFO_ROW_HEIGHT;

        // 第 2 行：连接状态 | RSSI
        ModFontManager.text(graphics, modFont, "连接状态:", col1X, infoY, labelColor, false);
        ModFontManager.text(graphics, modFont, connected ? (DOT_CONNECTED + " 已连接") : "未连接",
                col1X + 54, infoY, connected ? 0xFF55FF55 : 0xFFAAAAAA, false);
        ModFontManager.text(graphics, modFont, "RSSI:", col2X, infoY, labelColor, false);
        ModFontManager.text(graphics, modFont, connected && device != null ? (device.getRssi() + " dBm") : "—",
                col2X + 30, infoY, valueColor, false);
        infoY += INFO_ROW_HEIGHT;

        // 第 3 行：心率 | 更新时间
        ModFontManager.text(graphics, modFont, "心率:", col1X, infoY, labelColor, false);
        int hr = mgr.getCurrentHeartRate();
        boolean colorMode = config.isHeartRateColorMode();
        int heartColor = colorMode ? getHeartColorForHr(hr) : HEART_COLOR;
        int iconColor = (colorMode && config.isHeartColorIcon()) ? heartColor : HEART_COLOR;
        int rateColor = (colorMode && config.isHeartColorRate()) ? heartColor : 0xFFFF5555;
        int bpmColor = (colorMode && config.isHeartColorBpm()) ? heartColor : 0xFFFF5555;
        if (hr > 0) {
            int heartX = col1X + 30;
            int heartY = infoY + 2;
            drawPixelHeart(graphics, heartX, heartY, iconColor, 1);
            String rateStr = String.valueOf(hr);
            ModFontManager.text(graphics, modFont, rateStr, heartX + 10, infoY, rateColor, false);
            int rateWidth = modFont.width(rateStr);
            ModFontManager.text(graphics, modFont, " BPM", heartX + 10 + rateWidth, infoY, bpmColor, false);
        } else {
            ModFontManager.text(graphics, modFont, "—", col1X + 30, infoY, 0xFFAAAAAA, false);
        }
        ModFontManager.text(graphics, modFont, "更新时间:", col2X, infoY, labelColor, false);
        String updateTime = hr > 0 ? new SimpleDateFormat("HH:mm:ss").format(new Date()) : "—";
        ModFontManager.text(graphics, modFont, updateTime, col2X + 54, infoY, valueColor, false);
        infoY += INFO_ROW_HEIGHT;

        // 第 4 行：HTTP 服务地址（居中，HUD 开关在右侧）
        if (config.isHttpApiEnabled() && HttpServerManager.getInstance().isRunning()) {
            String httpAddr = HttpServerManager.getInstance().getAddress();
            ModFontManager.centeredText(graphics, modFont, "HTTP: " + httpAddr, centerX, infoY, 0xFF66CCFF);
        } else {
            ModFontManager.centeredText(graphics, modFont, "HTTP: 未启用", centerX, infoY, 0xFF666666);
        }
        infoY += INFO_ROW_HEIGHT;

        // 第 5 行：作者
        ModFontManager.centeredText(graphics, modFont, "作者@茶叶Wuu", centerX, infoY, 0xFF888888);
    }

    public static int getHeartColorForHr(int hr) {
        if (hr <= 0) return 0xFFAAAAAA;
        if (hr < 60) return 0xFF4080FF;
        if (hr <= 100) return 0xFF40FF60;
        if (hr <= 140) return 0xFFFFD700;
        return 0xFFFF4040;
    }

    public static void drawPixelHeart(GuiGraphicsExtractor graphics, int x, int y, int color, int pixelSize) {
        if (pixelSize < 1) {
            pixelSize = 1;
        }
        graphics.fill(x + 1 * pixelSize, y, x + 3 * pixelSize, y + pixelSize, color);
        graphics.fill(x + 4 * pixelSize, y, x + 6 * pixelSize, y + pixelSize, color);
        graphics.fill(x, y + pixelSize, x + 7 * pixelSize, y + 2 * pixelSize, color);
        graphics.fill(x, y + 2 * pixelSize, x + 7 * pixelSize, y + 3 * pixelSize, color);
        graphics.fill(x + 1 * pixelSize, y + 3 * pixelSize, x + 6 * pixelSize, y + 4 * pixelSize, color);
        graphics.fill(x + 2 * pixelSize, y + 4 * pixelSize, x + 5 * pixelSize, y + 5 * pixelSize, color);
        graphics.fill(x + 3 * pixelSize, y + 5 * pixelSize, x + 4 * pixelSize, y + 6 * pixelSize, color);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    public static void fillRoundedPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius) {
        ModConfig config = ConfigManager.getConfig();
        int alpha = (int) (config.getGuiOpacity() * 255) & 0xFF;
        fillVanillaPanel(graphics, x, y, width, height, alpha);
    }

    public static void fillVanillaPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        int innerColor = (a << 24) | 0x373737;
        int black = 0xFF000000;
        int white = 0xFFFFFFFF;
        int gray198 = 0xFFC6C6C6;
        int shadowGray = 0xFF9B9B9B;

        graphics.fill(x + 8, y + 8, x + width - 8, y + height - 8, innerColor);
        graphics.fill(x + 7, y + 7, x + width - 7, y + 8, black);
        graphics.fill(x + 7, y + height - 8, x + width - 7, y + height - 7, black);
        graphics.fill(x + 7, y + 8, x + 8, y + height - 8, black);
        graphics.fill(x + width - 8, y + 8, x + width - 7, y + height - 8, black);
        graphics.fill(x + 3, y + 3, x + width - 3, y + 7, gray198);
        graphics.fill(x + 3, y + height - 7, x + width - 3, y + height - 3, gray198);
        graphics.fill(x + 3, y + 7, x + 7, y + height - 7, gray198);
        graphics.fill(x + width - 7, y + 7, x + width - 3, y + height - 7, gray198);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 3, white);
        graphics.fill(x + 1, y + 3, x + 3, y + height - 3, white);
        graphics.fill(x + 1, y + height - 3, x + width - 1, y + height - 1, shadowGray);
        graphics.fill(x + width - 3, y + 3, x + width - 1, y + height - 3, shadowGray);
        graphics.fill(x + 2, y, x + width - 2, y + 1, black);
        graphics.fill(x + 2, y + height - 1, x + width - 2, y + height, black);
        graphics.fill(x, y + 2, x + 1, y + height - 2, black);
        graphics.fill(x + width - 1, y + 2, x + width, y + height - 2, black);
        graphics.fill(x + 1, y + 1, x + 2, y + 2, black);
        graphics.fill(x + width - 2, y + 1, x + width - 1, y + 2, black);
        graphics.fill(x + 1, y + height - 2, x + 2, y + height - 1, black);
        graphics.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, black);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * 绘制圆角填充矩形。
     */
    public static void fillRounded(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color, int radius) {
        int r = Math.min(radius, Math.min(width, height) / 2);
        if (r <= 0) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        graphics.fill(x, y + r, x + width, y + height - r, color);
        int[] inset = computeArcInset(r);
        for (int i = 0; i < r; i++) {
            int ins = inset[i];
            graphics.fill(x + ins, y + i, x + width - ins, y + i + 1, color);
            graphics.fill(x + ins, y + height - 1 - i, x + width - ins, y + height - i, color);
        }
    }

    private static int[] computeArcInset(int r) {
        int[] inset = new int[r];
        java.util.Arrays.fill(inset, r);
        int X = 0, Y = r;
        int d = 1 - r;
        while (X <= Y) {
            int py1 = r - Y;
            if (py1 >= 0 && py1 < r) {
                inset[py1] = Math.min(inset[py1], r - X);
            }
            int py2 = r - X;
            if (py2 >= 0 && py2 < r && X != Y) {
                inset[py2] = Math.min(inset[py2], r - Y);
            }
            if (d < 0) {
                d += 2 * X + 3;
            } else {
                d += 2 * (X - Y) + 5;
                Y--;
            }
            X++;
        }
        return inset;
    }

    /**
     * 绘制圆角边框。
     */
    public static void drawRoundedOutline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color, int radius) {
        int r = Math.min(radius, Math.min(width, height) / 2);
        if (r <= 0) {
            graphics.outline(x, y, width, height, color);
            return;
        }
        double dy0 = r - 0.5;
        double dx0 = Math.sqrt((double) r * r - dy0 * dy0);
        int insetTop = Math.max(0, Math.min((int) Math.floor(r - dx0), r));
        graphics.fill(x + insetTop, y, x + width - insetTop, y + 1, color);
        graphics.fill(x + insetTop, y + height - 1, x + width - insetTop, y + height, color);
        graphics.fill(x, y + r, x + 1, y + height - r, color);
        graphics.fill(x + width - 1, y + r, x + width, y + height - r, color);
        drawCornerArcBresenham(graphics, x + r, y + r, r, color, -1, -1);
        drawCornerArcBresenham(graphics, x + width - 1 - r, y + r, r, color, 1, -1);
        drawCornerArcBresenham(graphics, x + r, y + height - 1 - r, r, color, -1, 1);
        drawCornerArcBresenham(graphics, x + width - 1 - r, y + height - 1 - r, r, color, 1, 1);
    }

    private static void drawCornerArcBresenham(GuiGraphicsExtractor graphics, int cx, int cy, int r, int color, int sx, int sy) {
        int X = 0, Y = r;
        int d = 1 - r;
        while (X <= Y) {
            int px1 = cx + sx * X;
            int py1 = cy + sy * Y;
            graphics.fill(px1, py1, px1 + 1, py1 + 1, color);
            if (X != Y) {
                int px2 = cx + sx * Y;
                int py2 = cy + sy * X;
                graphics.fill(px2, py2, px2 + 1, py2 + 1, color);
            }
            if (d < 0) {
                d += 2 * X + 3;
            } else {
                d += 2 * (X - Y) + 5;
                Y--;
            }
            X++;
        }
    }
}
