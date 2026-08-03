package com.chayewuu.xiaomiheartrate.gui;

import com.chayewuu.xiaomiheartrate.HeartRateMod;
import com.chayewuu.xiaomiheartrate.config.ConfigManager;
import com.chayewuu.xiaomiheartrate.config.ModConfig;
import com.chayewuu.xiaomiheartrate.device.BleDevice;
import com.chayewuu.xiaomiheartrate.device.DeviceManagerHolder;
import com.chayewuu.xiaomiheartrate.heart.HeartRateManager;
import com.chayewuu.xiaomiheartrate.network.HttpServerManager;
import com.chayewuu.xiaomiheartrate.util.ModLogger;
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
 * Xiaomi Heartrate 主界面。
 * <p>
 * 现代化布局：屏幕上半部分为功能按钮区（2 列 3 行，6 个按钮），
 * 下半部分为设备连接信息区。采用圆角半透明面板风格，融合 Minecraft 原版 Screen 框架。
 * </p>
 *
 * <p><b>布局结构：</b></p>
 * <pre>
 * ┌──────────────────────────────────────┐
 * │       Xiaomi Heartrate v1.0.0       │  标题
 * │ [连接设备]    [断开连接]             │  按钮行 1
 * │ [GUI设置]    [心率设置]              │  按钮行 2
 * │ [API设置]    [项目主页]              │  按钮行 3
 * │ ─────────────────────────────────── │  分隔线
 * │ 设备名称: xxx   MAC: xx:xx:xx:xx     │  设备信息
 * │ 连接状态: ●已连接  RSSI: -60 dBm     │
 * │ 电量: 85%      心率: ♥ 76 BPM       │
 * │ 更新时间: 12:34:56                  │
 * │ HTTP: 127.0.0.1:xxxxx               │
 * └──────────────────────────────────────┘
 * </pre>
 *
 * <p><b>渲染顺序说明：</b>先绘制全屏遮罩与面板背景，再绘制文字内容，
 * 最后调用 {@code super.extractRenderState} 渲染按钮（确保按钮在最上层，
 * 不被半透明遮罩覆盖，避免"灰色遮蔽"现象）。</p>
 *
 * <p>按键 {@code H} 打开本界面；按 {@code ESC} 关闭。</p>
 */
public class MainScreen extends Screen {
    /** 主面板宽度 */
    private static final int PANEL_WIDTH = 360;
    /** 主面板高度 */
    private static final int PANEL_HEIGHT = 210;
    /** 圆角半径 */
    private static final int CORNER_RADIUS = 14;
    /** 按钮宽度（2 列布局） */
    private static final int BUTTON_WIDTH = 160;
    /** 按钮高度 */
    private static final int BUTTON_HEIGHT = 20;
    /** 按钮列间距 */
    private static final int BUTTON_GAP = 16;
    /** 按钮行间距 */
    private static final int BUTTON_ROW_GAP = 8;

    /** 像素心形图标颜色（红色） */
    private static final int HEART_COLOR = 0xFFFF4060;
    /** 连接状态圆点（非 emoji，使用 Unicode 实心圆 U+25CF） */
    private static final String DOT_CONNECTED = "\u25CF";

    // === 按钮 ===
    private Button connectButton;
    private Button disconnectButton;
    private Button guiSettingsButton;
    private Button heartRateSettingsButton;
    private Button apiSettingsButton;
    private Button homepageButton;

    /** 主面板左上角 X */
    private int panelX;
    /** 主面板左上角 Y */
    private int panelY;

    /**
     * 构造主界面。
     *
     * @param title 屏幕标题
     */
    public MainScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - PANEL_HEIGHT) / 2;

        // 按钮区起始 Y（标题下方）
        int buttonsStartY = panelY + 32;
        // 2 列按钮 X 坐标
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

        // 第 2 行：GUI设置 | 心率设置
        int row2Y = buttonsStartY + BUTTON_HEIGHT + BUTTON_ROW_GAP;
        guiSettingsButton = Button.builder(
                Component.literal("GUI设置"),
                btn -> openGuiSettings()
        ).bounds(leftButtonX, row2Y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        addRenderableWidget(guiSettingsButton);

        heartRateSettingsButton = Button.builder(
                Component.literal("心率设置"),
                btn -> openHeartRateSettings()
        ).bounds(rightButtonX, row2Y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        addRenderableWidget(heartRateSettingsButton);

        // 第 3 行：API设置 | 项目主页
        int row3Y = row2Y + BUTTON_HEIGHT + BUTTON_ROW_GAP;
        apiSettingsButton = Button.builder(
                Component.literal("API设置"),
                btn -> openApiSettings()
        ).bounds(leftButtonX, row3Y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        addRenderableWidget(apiSettingsButton);

        homepageButton = Button.builder(
                Component.literal("项目主页"),
                btn -> openHomepage()
        ).bounds(rightButtonX, row3Y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        addRenderableWidget(homepageButton);

        refreshButtonStates();
    }

    /**
     * 刷新按钮可用状态与文本（根据当前连接状态）。
     */
    private void refreshButtonStates() {
        boolean connected = HeartRateManager.getInstance().isConnected();
        boolean scanning = DeviceManagerHolder.get().getConnectionState()
                == com.chayewuu.xiaomiheartrate.device.ConnectionState.SCANNING;
        boolean connecting = DeviceManagerHolder.get().getConnectionState()
                == com.chayewuu.xiaomiheartrate.device.ConnectionState.CONNECTING;

        if (connectButton != null) {
            connectButton.setMessage(Component.literal(getConnectButtonText()));
            // 未连接且未在扫描/连接中时，按钮可用
            connectButton.active = !connected && !scanning && !connecting;
        }
        if (disconnectButton != null) {
            disconnectButton.active = connected;
        }
    }

    /**
     * 获取连接按钮文本。
     *
     * @return 按钮文本
     */
    private String getConnectButtonText() {
        HeartRateManager mgr = HeartRateManager.getInstance();
        if (mgr.isConnected()) {
            return DOT_CONNECTED + " 已连接";
        }
        com.chayewuu.xiaomiheartrate.device.ConnectionState state =
                DeviceManagerHolder.get().getConnectionState();
        if (state == com.chayewuu.xiaomiheartrate.device.ConnectionState.SCANNING) {
            return "扫描中...";
        }
        if (state == com.chayewuu.xiaomiheartrate.device.ConnectionState.CONNECTING) {
            return "连接中...";
        }
        return "连接设备";
    }

    /**
     * 连接按钮点击：打开设备选择界面。
     */
    private void onConnectClicked() {
        ModLogger.info("[MainScreen] 用户点击连接设备，打开设备选择界面");
        Minecraft.getInstance().gui.setScreen(new DeviceSelectionScreen(this));
    }

    /**
     * 断开按钮点击：调用 DeviceManager 断开当前设备。
     */
    private void onDisconnectClicked() {
        ModLogger.info("[MainScreen] 用户点击断开设备");
        DeviceManagerHolder.get().disconnect();
        refreshButtonStates();
    }

    /**
     * 打开 GUI 设置界面。
     */
    private void openGuiSettings() {
        ModLogger.info("[MainScreen] 用户点击 GUI设置");
        Minecraft.getInstance().gui.setScreen(
                new SettingScreen(Component.translatable("screen." + HeartRateMod.MOD_ID + ".settings"), this)
        );
    }

    /**
     * 打开心率采集设置界面。
     */
    private void openHeartRateSettings() {
        ModLogger.info("[MainScreen] 用户点击 心率设置");
        Minecraft.getInstance().gui.setScreen(new HeartRateSettingsScreen(this));
    }

    /**
     * 打开 API 设置界面。
     */
    private void openApiSettings() {
        ModLogger.info("[MainScreen] 用户点击 API设置");
        Minecraft.getInstance().gui.setScreen(new ApiSettingsScreen(this));
    }

    /**
     * 打开项目主页。
     */
    private void openHomepage() {
        ModLogger.info("[MainScreen] 用户点击 项目主页");
        openUrlInBrowser("https://github.com/ChaYeWuu/Xiaomi-Heartrate");
    }

    /**
     * 在系统默认浏览器中打开指定 URL。
     * <p>优先使用 {@code cmd /c start} 命令（Windows 平台最可靠），
     * 回退到 {@link Desktop#browse}。</p>
     *
     * @param url 目标 URL
     */
    private void openUrlInBrowser(String url) {
        Thread browserThread = new Thread(() -> {
            // 优先使用 Windows 的 cmd start 命令（在 Minecraft 客户端中最可靠）
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
                    ModLogger.warn("[MainScreen] cmd start 退出码 {}，尝试 Desktop.browse", code);
                } catch (Exception e) {
                    ModLogger.warn("[MainScreen] cmd start 失败: {}，尝试 Desktop.browse", e.getMessage());
                }
            }
            // 回退到 Desktop.browse
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(url));
                    ModLogger.info("[MainScreen] 已在浏览器打开: {}", url);
                } else {
                    ModLogger.warn("[MainScreen] 当前平台不支持 Desktop.browse，无法打开: {}", url);
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
        // 每 tick 刷新按钮状态（设备状态可能在后台变化）
        refreshButtonStates();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // 渲染顺序：背景遮罩 → 面板 → 文字内容 → 按钮（最上层）
        // 这样按钮不会被半透明遮罩覆盖，避免"灰色遮蔽"现象
        renderBackground(graphics);
        renderPanel(graphics);
        renderContent(graphics);
        // 底部作者署名（灰色小字，居中显示在面板内部底部）
        ModFontManager.centeredText(graphics, ModFontManager.getFont(), "作者@茶叶Wuu", this.width / 2, panelY + PANEL_HEIGHT - 12, 0xFF888888);
        // 最后调用 super 渲染按钮（widgets），确保按钮在最上层
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    /**
     * 渲染半透明背景遮罩（毛玻璃风格简化版）。
     *
     * @param graphics 图形上下文
     */
    private void renderBackground(GuiGraphicsExtractor graphics) {
        // 全屏半透明深色遮罩
        graphics.fill(0, 0, this.width, this.height, 0x80000000);
    }

    /**
     * 渲染圆角主面板。
     *
     * @param graphics 图形上下文
     */
    private void renderPanel(GuiGraphicsExtractor graphics) {
        fillRoundedPanel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, CORNER_RADIUS);
    }

    /**
     * 渲染面板内容（标题、分隔线、设备信息、HTTP 地址）。
     *
     * @param graphics 图形上下文
     */
    private void renderContent(GuiGraphicsExtractor graphics) {
        int centerX = panelX + PANEL_WIDTH / 2;
        Font modFont = ModFontManager.getFont();
        ModConfig config = ConfigManager.getConfig();

        // === 标题 ===
        String title = "Xiaomi Heartrate v" + HeartRateMod.MOD_VERSION;
        ModFontManager.centeredText(graphics, modFont, title, centerX, panelY + 12, 0xFFFFD700);

        // === 分隔线（按钮区与设备信息区之间）===
        int dividerY = panelY + 32 + (BUTTON_HEIGHT + BUTTON_ROW_GAP) * 3 + 8;
        graphics.fill(panelX + 16, dividerY, panelX + PANEL_WIDTH - 16, dividerY + 1, 0xFF_5A5A7A);

        // === 设备信息区 ===
        HeartRateManager mgr = HeartRateManager.getInstance();
        BleDevice device = mgr.getCurrentDevice();
        boolean connected = mgr.isConnected();

        int labelColor = 0xFFAAAAAA;
        int valueColor = 0xFFFFFFFF;
        int col1X = panelX + 18;
        int col2X = panelX + 190;
        int infoY = dividerY + 10;

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
        infoY += 14;

        // 第 2 行：连接状态 | RSSI
        ModFontManager.text(graphics, modFont, "连接状态:", col1X, infoY, labelColor, false);
        ModFontManager.text(graphics, modFont, connected ? (DOT_CONNECTED + " 已连接") : "未连接",
                col1X + 54, infoY, connected ? 0xFF55FF55 : 0xFFAAAAAA, false);
        ModFontManager.text(graphics, modFont, "RSSI:", col2X, infoY, labelColor, false);
        ModFontManager.text(graphics, modFont, connected && device != null ? (device.getRssi() + " dBm") : "—",
                col2X + 30, infoY, valueColor, false);
        infoY += 14;

        // 第 3 行：心率（含像素心形图标，支持心动模式变色）| 更新时间
        ModFontManager.text(graphics, modFont, "心率:", col1X, infoY, labelColor, false);
        int hr = mgr.getCurrentHeartRate();
        boolean colorMode = config.isHeartRateColorMode();
        int heartColor = colorMode ? getHeartColorForHr(hr) : HEART_COLOR;
        // 各元素独立变色
        int iconColor = (colorMode && config.isHeartColorIcon()) ? heartColor : HEART_COLOR;
        int rateColor = (colorMode && config.isHeartColorRate()) ? heartColor : 0xFFFF5555;
        int bpmColor = (colorMode && config.isHeartColorBpm()) ? heartColor : 0xFFFF5555;
        if (hr > 0) {
            int heartX = col1X + 30;
            int heartY = infoY + 2;
            drawPixelHeart(graphics, heartX, heartY, iconColor, 1);
            // 分段渲染：心率数字和 BPM 后缀各自颜色
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
        infoY += 14;

        // 第 4 行：HTTP 服务地址（仅启用时显示）
        if (config.isHttpApiEnabled() && HttpServerManager.getInstance().isRunning()) {
            String httpAddr = HttpServerManager.getInstance().getAddress();
            ModFontManager.centeredText(graphics, modFont, "HTTP: " + httpAddr, centerX, infoY, 0xFF66CCFF);
        } else {
            ModFontManager.centeredText(graphics, modFont, "HTTP: 未启用", centerX, infoY, 0xFF666666);
        }
    }

    /**
     * 根据心率值返回对应颜色（蓝→绿→黄→红）。
     */
    private static int getHeartColorForHr(int hr) {
        if (hr <= 0) return 0xFFAAAAAA;
        if (hr < 60) return 0xFF4080FF;
        if (hr <= 100) return 0xFF40FF60;
        if (hr <= 140) return 0xFFFFD700;
        return 0xFFFF4040;
    }

    /**
     * 绘制像素风格心形图标（7x6 像素基础形，可缩放）。
     * <p>使用 fill 拼接绘制，避免依赖 Unicode 字体渲染，视觉效果更精致。
     * 心形结构：
     * <pre>
     *  XX XX    → 两瓣
     * XXXXXXX   → 最宽行
     * XXXXXXX   → 最宽行
     *  XXXXX    → 收窄
     *   XXX     → 更窄
     *    X      → 尖端
     * </pre>
     * </p>
     *
     * @param graphics  图形上下文
     * @param x         左上角 X
     * @param y         左上角 Y
     * @param color     ARGB 颜色
     * @param pixelSize 每个像素点的边长（1=7x6 像素，2=14x12 像素，...）
     */
    public static void drawPixelHeart(GuiGraphicsExtractor graphics, int x, int y, int color, int pixelSize) {
        if (pixelSize < 1) {
            pixelSize = 1;
        }
        // 第 1 行：两瓣
        graphics.fill(x + 1 * pixelSize, y, x + 3 * pixelSize, y + pixelSize, color);
        graphics.fill(x + 4 * pixelSize, y, x + 6 * pixelSize, y + pixelSize, color);
        // 第 2 行：最宽
        graphics.fill(x, y + pixelSize, x + 7 * pixelSize, y + 2 * pixelSize, color);
        // 第 3 行：最宽
        graphics.fill(x, y + 2 * pixelSize, x + 7 * pixelSize, y + 3 * pixelSize, color);
        // 第 4 行：收窄
        graphics.fill(x + 1 * pixelSize, y + 3 * pixelSize, x + 6 * pixelSize, y + 4 * pixelSize, color);
        // 第 5 行：更窄
        graphics.fill(x + 2 * pixelSize, y + 4 * pixelSize, x + 5 * pixelSize, y + 5 * pixelSize, color);
        // 第 6 行：尖端
        graphics.fill(x + 3 * pixelSize, y + 5 * pixelSize, x + 4 * pixelSize, y + 6 * pixelSize, color);
    }

    /**
     * 安全获取字符串，{@code null} 返回空串。
     *
     * @param s 原始字符串
     * @return 非空字符串
     */
    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /**
     * 绘制圆角填充矩形（含半透明深色背景与浅色边框）。
     * <p>用于屏幕主面板渲染，颜色从 {@link ModConfig#getGuiOpacity()} 读取透明度。</p>
     *
     * @param graphics 图形上下文
     * @param x        左上角 X
     * @param y        左上角 Y
     * @param width    宽度
     * @param height   高度
     * @param radius   圆角半径
     */
    public static void fillRoundedPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius) {
        ModConfig config = ConfigManager.getConfig();
        int alpha = (int) (config.getGuiOpacity() * 255) & 0xFF;
        int bgColor = (alpha << 24) | 0x1E1E2E;
        int borderColor = 0xFF_5A5A7A;
        fillRounded(graphics, x, y, width, height, bgColor, radius);
        drawRoundedOutline(graphics, x, y, width, height, borderColor, radius);
    }

    /**
     * 绘制圆角填充矩形。
     * <p>逐行计算圆角覆盖范围，使用 floor 确保 corner 区域无像素缺失。</p>
     *
     * @param graphics 图形上下文
     * @param x        左上角 X
     * @param y        左上角 Y
     * @param width    宽度
     * @param height   高度
     * @param color    ARGB 颜色
     * @param radius   圆角半径
     */
    public static void fillRounded(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color, int radius) {
        int r = Math.min(radius, Math.min(width, height) / 2);
        if (r <= 0) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        // 中间区域（圆角之间的完整矩形）
        graphics.fill(x, y + r, x + width, y + height - r, color);
        // 用 Bresenham 圆弧点计算每行 inset，确保与 drawRoundedOutline 边框圆弧匹配
        int[] inset = computeArcInset(r);
        // 逐行填充顶部和底部圆角区域
        for (int i = 0; i < r; i++) {
            int ins = inset[i];
            // 顶部行：从 x+ins 到 x+width-ins
            graphics.fill(x + ins, y + i, x + width - ins, y + i + 1, color);
            // 底部行
            graphics.fill(x + ins, y + height - 1 - i, x + width - ins, y + height - i, color);
        }
    }

    /**
     * 用 Bresenham 画圆算法计算圆角弧每行最左 x 坐标（相对左上角）。
     * <p>返回数组 inset[i] 表示 y=i 行的圆弧 x 坐标，用于 fillRounded 确定填充范围，
     * 与 drawRoundedOutline 的 Bresenham 圆弧点完全匹配。</p>
     *
     * @param r 圆角半径
     * @return inset 数组，长度 r
     */
    private static int[] computeArcInset(int r) {
        int[] inset = new int[r];
        java.util.Arrays.fill(inset, r);
        int X = 0, Y = r;
        int d = 1 - r;
        while (X <= Y) {
            // 点1: (r - X, r - Y) → y = r - Y
            int py1 = r - Y;
            if (py1 >= 0 && py1 < r) {
                inset[py1] = Math.min(inset[py1], r - X);
            }
            // 点2: (r - Y, r - X) → y = r - X
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
     * <p>使用 Bresenham 画圆算法绘制四角圆弧，确保每个像素点 8-连通连续，
     * 避免逐行算法在圆弧斜率较大处出现像素跳跃缺口。直线段范围由 i=0 的 inset 值确定，
     * 覆盖圆弧顶部在 y=0 行的多个点。</p>
     *
     * @param graphics 图形上下文
     * @param x        左上角 X
     * @param y        左上角 Y
     * @param width    宽度
     * @param height   高度
     * @param color    边框颜色
     * @param radius   圆角半径
     */
    public static void drawRoundedOutline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color, int radius) {
        int r = Math.min(radius, Math.min(width, height) / 2);
        if (r <= 0) {
            graphics.outline(x, y, width, height, color);
            return;
        }
        // 圆角弧顶端（i=0）的 inset 值：直线段需从该 inset 处开始，覆盖圆弧顶部 y=0 行的多个 Bresenham 点
        double dy0 = r - 0.5;
        double dx0 = Math.sqrt((double) r * r - dy0 * dy0);
        int insetTop = Math.max(0, Math.min((int) Math.floor(r - dx0), r));
        // 上下边（直线段）—— 从 insetTop 到 width-insetTop
        graphics.fill(x + insetTop, y, x + width - insetTop, y + 1, color);
        graphics.fill(x + insetTop, y + height - 1, x + width - insetTop, y + height, color);
        // 左右边（直线段）
        graphics.fill(x, y + r, x + 1, y + height - r, color);
        graphics.fill(x + width - 1, y + r, x + width, y + height - r, color);
        // 用 Bresenham 画圆算法画四角圆弧（每个角 1/4 圆弧）
        // 圆心：左上(x+r, y+r)、右上(x+width-1-r, y+r)、左下(x+r, y+height-1-r)、右下(x+width-1-r, y+height-1-r)
        drawCornerArcBresenham(graphics, x + r, y + r, r, color, -1, -1);
        drawCornerArcBresenham(graphics, x + width - 1 - r, y + r, r, color, 1, -1);
        drawCornerArcBresenham(graphics, x + r, y + height - 1 - r, r, color, -1, 1);
        drawCornerArcBresenham(graphics, x + width - 1 - r, y + height - 1 - r, r, color, 1, 1);
    }

    /**
     * 用 Bresenham 画圆算法绘制 1/4 圆弧（边框点）。
     * <p>从 (cx, cy±r) 到 (cx±r, cy) 的弧线，每个点 8-连通连续。
     * sx/sy 控制弧所在象限：左上(-1,-1)、右上(+1,-1)、左下(-1,+1)、右下(+1,+1)。</p>
     *
     * @param graphics 图形上下文
     * @param cx       圆心 X
     * @param cy       圆心 Y
     * @param r        半径
     * @param color    颜色
     * @param sx       X 方向符号（-1 或 +1）
     * @param sy       Y 方向符号（-1 或 +1）
     */
    private static void drawCornerArcBresenham(GuiGraphicsExtractor graphics, int cx, int cy, int r, int color, int sx, int sy) {
        int X = 0, Y = r;
        int d = 1 - r;
        while (X <= Y) {
            // 点1: (cx + sx*X, cy + sy*Y)
            int px1 = cx + sx * X;
            int py1 = cy + sy * Y;
            graphics.fill(px1, py1, px1 + 1, py1 + 1, color);
            // 点2: (cx + sx*Y, cy + sy*X)（X != Y 时，避免重复）
            if (X != Y) {
                int px2 = cx + sx * Y;
                int py2 = cy + sy * X;
                graphics.fill(px2, py2, px2 + 1, py2 + 1, color);
            }
            // Bresenham 决策参数更新
            if (d < 0) {
                d += 2 * X + 3;
            } else {
                d += 2 * (X - Y) + 5;
                Y--;
            }
            X++;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
