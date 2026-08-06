package com.chayewuu.hyperheartrate.gui;

import com.chayewuu.hyperheartrate.HeartRateMod;
import com.chayewuu.hyperheartrate.config.ConfigManager;
import com.chayewuu.hyperheartrate.config.ModConfig;
import com.chayewuu.hyperheartrate.device.BleDevice;
import com.chayewuu.hyperheartrate.device.DeviceManagerHolder;
import com.chayewuu.hyperheartrate.heart.HeartRateManager;
import com.chayewuu.hyperheartrate.network.HttpServerManager;
import com.chayewuu.hyperheartrate.util.ModLogger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.awt.Desktop;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Hyper Heartrate 主界面。
 * <p>
 * 现代化布局：屏幕上半部分为功能按钮区（2 列 3 行，6 个按钮），
 * 下半部分为设备连接信息区。采用圆角半透明面板风格，融合 Minecraft 原版 Screen 框架。
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
 * │ 电量: 85%      心率: ♥ 76 BPM       │
 * │ 更新时间: 12:34:56                  │
 * │ HTTP: 127.0.0.1:xxxxx               │
 * └──────────────────────────────────────┘
 * </pre>
 *
 * <p><b>渲染顺序说明：</b>先绘制全屏遮罩与面板背景，再绘制文字内容，
 * 最后调用 {@code super.render} 渲染按钮（确保按钮在最上层，
 * 不被半透明遮罩覆盖，避免"灰色遮蔽"现象）。</p>
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
    private ButtonWidget connectButton;
    private ButtonWidget disconnectButton;
    /** 模组设置按钮（打开子菜单：GUI设置/心率设置/API设置） */
    private ButtonWidget modSettingsButton;
    private ButtonWidget homepageButton;
    /** 联机功能按钮 */
    private ButtonWidget multiplayerButton;
    /** B站主页按钮 */
    private ButtonWidget bilibiliButton;
    /** HUD 总开关按钮（HTTP 行右侧，控制游戏内心率 HUD 显隐） */
    private ButtonWidget hudToggleButton;

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

    /**
     * 构造主界面。
     *
     * @param title 屏幕标题
     */
    public MainScreen(Text title) {
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
        connectButton = ButtonWidget.builder(
                Text.literal(getConnectButtonText()),
                btn -> onConnectClicked()
        ).dimensions(leftButtonX, buttonsStartY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        addDrawableChild(connectButton);

        disconnectButton = ButtonWidget.builder(
                Text.literal("断开连接"),
                btn -> onDisconnectClicked()
        ).dimensions(rightButtonX, buttonsStartY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        addDrawableChild(disconnectButton);

        // 第 2 行：模组设置 | 联机功能
        int row2Y = buttonsStartY + BUTTON_HEIGHT + BUTTON_ROW_GAP;
        modSettingsButton = ButtonWidget.builder(
                Text.literal("模组设置"),
                btn -> openModSettings()
        ).dimensions(leftButtonX, row2Y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        addDrawableChild(modSettingsButton);

        multiplayerButton = ButtonWidget.builder(
                Text.literal("联机功能"),
                btn -> openMultiplayerSettings()
        ).dimensions(rightButtonX, row2Y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        addDrawableChild(multiplayerButton);

        // 第 3 行：B站主页 | 项目主页
        int row3Y = row2Y + BUTTON_HEIGHT + BUTTON_ROW_GAP;
        bilibiliButton = ButtonWidget.builder(
                Text.literal("B站主页"),
                btn -> openBilibili()
        ).dimensions(leftButtonX, row3Y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        addDrawableChild(bilibiliButton);

        homepageButton = ButtonWidget.builder(
                Text.literal("项目主页"),
                btn -> openHomepage()
        ).dimensions(rightButtonX, row3Y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        addDrawableChild(homepageButton);

        // 计算 HTTP 行和作者行 Y 坐标
        int dividerY = panelY + 30 + buttonsHeight + dividerGap;
        int infoStartY = dividerY + infoStartGap;
        // 3 行设备信息
        httpRowY = infoStartY + INFO_ROW_HEIGHT * 3;
        authorRowY = httpRowY + INFO_ROW_HEIGHT;

        // HUD 开关按钮（HTTP 行右侧）
        hudToggleButton = ButtonWidget.builder(
                Text.literal("HUD: " + (config.isHudEnabled() ? "开" : "关")),
                btn -> {
                    boolean current = btn.getMessage().getString().endsWith("开");
                    boolean next = !current;
                    btn.setMessage(Text.literal("HUD: " + (next ? "开" : "关")));
                    config.setHudEnabled(next);
                    ConfigManager.save();
                    ModLogger.info("[MainScreen] HUD 总开关: {}", next ? "开" : "关");
                }
        ).dimensions(panelX + PANEL_WIDTH - HUD_TOGGLE_WIDTH - 12,
                httpRowY - (HUD_TOGGLE_HEIGHT - INFO_ROW_HEIGHT) / 2,
                HUD_TOGGLE_WIDTH, HUD_TOGGLE_HEIGHT).build();
        hudToggleButton.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
                Text.literal("游戏内心率显示总开关")));
        addDrawableChild(hudToggleButton);

        refreshButtonStates();
    }

    /**
     * 刷新按钮可用状态与文本（根据当前连接状态）。
     */
    private void refreshButtonStates() {
        boolean connected = HeartRateManager.getInstance().isConnected();
        boolean scanning = DeviceManagerHolder.get().getConnectionState()
                == com.chayewuu.hyperheartrate.device.ConnectionState.SCANNING;
        boolean connecting = DeviceManagerHolder.get().getConnectionState()
                == com.chayewuu.hyperheartrate.device.ConnectionState.CONNECTING;

        if (connectButton != null) {
            connectButton.setMessage(Text.literal(getConnectButtonText()));
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

    /**
     * 连接按钮点击：打开设备选择界面。
     */
    private void onConnectClicked() {
        ModLogger.info("[MainScreen] 用户点击连接设备，打开设备选择界面");
        MinecraftClient.getInstance().setScreen(new DeviceSelectionScreen(this));
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
     * 打开模组设置子菜单（GUI设置/心率设置/API设置）。
     */
    private void openModSettings() {
        ModLogger.info("[MainScreen] 用户点击 模组设置");
        MinecraftClient.getInstance().setScreen(new ModSettingsScreen(this));
    }

    /**
     * 打开项目主页。
     */
    private void openHomepage() {
        ModLogger.info("[MainScreen] 用户点击 项目主页");
        openUrlInBrowser("https://github.com/ChaYeWuu/Hyper-Heartrate");
    }

    /**
     * 打开 B站主页。
     */
    private void openBilibili() {
        ModLogger.info("[MainScreen] 用户点击 B站主页");
        openUrlInBrowser("https://space.bilibili.com/698351214");
    }

    /**
     * 打开联机功能设置界面。
     */
    private void openMultiplayerSettings() {
        ModLogger.info("[MainScreen] 用户点击 联机功能");
        MinecraftClient.getInstance().setScreen(new MultiplayerSettingsScreen(this));
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 渲染顺序：背景遮罩 → 面板 → 文字内容 → 按钮（最上层）
        // 这样按钮不会被半透明遮罩覆盖，避免"灰色遮蔽"现象
        renderBackground(context);
        renderPanel(context);
        renderContent(context);
        // 最后调用 super 渲染按钮（widgets），确保按钮在最顶层
        super.render(context, mouseX, mouseY, delta);
    }

    /**
     * 渲染半透明背景遮罩（毛玻璃风格简化版）。
     *
     * @param context 图形上下文
     */
    private void renderBackground(DrawContext context) {
        // 全屏半透明深色遮罩
        context.fill(0, 0, this.width, this.height, 0x80000000);
    }

    /**
     * 渲染圆角主面板。
     *
     * @param context 图形上下文
     */
    private void renderPanel(DrawContext context) {
        fillRoundedPanel(context, panelX, panelY, PANEL_WIDTH, panelHeight, CORNER_RADIUS);
    }

    /**
     * 渲染面板内容（标题、分隔线、设备信息、HTTP 地址）。
     *
     * @param context 图形上下文
     */
    private void renderContent(DrawContext context) {
        int centerX = panelX + PANEL_WIDTH / 2;
        TextRenderer modFont = ModFontManager.getFont();
        ModConfig config = ConfigManager.getConfig();

        // === 标题 ===
        String title = "Hyper Heartrate v" + HeartRateMod.MOD_VERSION;
        ModFontManager.centeredText(context, modFont, title, centerX, panelY + 12, 0xFFFFD700);

        // === 分隔线（按钮区与设备信息区之间）===
        int dividerY = panelY + 30 + BUTTON_HEIGHT * 3 + BUTTON_ROW_GAP * 2 + 6;
        context.fill(panelX + 16, dividerY, panelX + PANEL_WIDTH - 16, dividerY + 1, 0xFFA0A0A0);

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
        ModFontManager.text(context, modFont, "设备名称:", col1X, infoY, labelColor, false);
        String displayDeviceName = "—";
        if (connected && device != null) {
            String customName = config.getCustomDeviceName();
            if (customName != null && !customName.isEmpty()) {
                displayDeviceName = customName;
            } else {
                displayDeviceName = safe(device.getName());
            }
        }
        ModFontManager.text(context, modFont, displayDeviceName, col1X + 54, infoY, valueColor, false);
        ModFontManager.text(context, modFont, "MAC:", col2X, infoY, labelColor, false);
        ModFontManager.text(context, modFont, connected && device != null ? safe(device.getAddress()) : "—",
                col2X + 30, infoY, valueColor, false);
        infoY += INFO_ROW_HEIGHT;

        // 第 2 行：连接状态 | RSSI
        ModFontManager.text(context, modFont, "连接状态:", col1X, infoY, labelColor, false);
        ModFontManager.text(context, modFont, connected ? (DOT_CONNECTED + " 已连接") : "未连接",
                col1X + 54, infoY, connected ? 0xFF55FF55 : 0xFFAAAAAA, false);
        ModFontManager.text(context, modFont, "RSSI:", col2X, infoY, labelColor, false);
        ModFontManager.text(context, modFont, connected && device != null ? (device.getRssi() + " dBm") : "—",
                col2X + 30, infoY, valueColor, false);
        infoY += INFO_ROW_HEIGHT;

        // 第 3 行：心率（含像素心形图标，支持心动模式变色）| 更新时间
        ModFontManager.text(context, modFont, "心率:", col1X, infoY, labelColor, false);
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
            drawPixelHeart(context, heartX, heartY, iconColor, 1);
            // 分段渲染：心率数字和 BPM 后缀各自颜色
            String rateStr = String.valueOf(hr);
            ModFontManager.text(context, modFont, rateStr, heartX + 10, infoY, rateColor, false);
            int rateWidth = modFont.getWidth(rateStr);
            ModFontManager.text(context, modFont, " BPM", heartX + 10 + rateWidth, infoY, bpmColor, false);
        } else {
            ModFontManager.text(context, modFont, "—", col1X + 30, infoY, 0xFFAAAAAA, false);
        }
        ModFontManager.text(context, modFont, "更新时间:", col2X, infoY, labelColor, false);
        String updateTime = hr > 0 ? new SimpleDateFormat("HH:mm:ss").format(new Date()) : "—";
        ModFontManager.text(context, modFont, updateTime, col2X + 54, infoY, valueColor, false);
        infoY += INFO_ROW_HEIGHT;

        // 第 4 行：HTTP 服务地址（居中，HUD 开关在右侧）
        if (config.isHttpApiEnabled() && HttpServerManager.getInstance().isRunning()) {
            String httpAddr = HttpServerManager.getInstance().getAddress();
            ModFontManager.centeredText(context, modFont, "HTTP: " + httpAddr, centerX, infoY, 0xFF66CCFF);
        } else {
            ModFontManager.centeredText(context, modFont, "HTTP: 未启用", centerX, infoY, 0xFF666666);
        }
        infoY += INFO_ROW_HEIGHT;

        // 第 5 行：作者
        ModFontManager.centeredText(context, modFont, "作者@茶叶Wuu", centerX, infoY, 0xFF888888);
    }

    /**
     * 根据心率值返回对应颜色（蓝→绿→黄→红）。
     */
    public static int getHeartColorForHr(int hr) {
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
     * @param context  图形上下文
     * @param x         左上角 X
     * @param y         左上角 Y
     * @param color     ARGB 颜色
     * @param pixelSize 每个像素点的边长（1=7x6 像素，2=14x12 像素，...）
     */
    public static void drawPixelHeart(DrawContext context, int x, int y, int color, int pixelSize) {
        if (pixelSize < 1) {
            pixelSize = 1;
        }
        // 第 1 行：两瓣
        context.fill(x + 1 * pixelSize, y, x + 3 * pixelSize, y + pixelSize, color);
        context.fill(x + 4 * pixelSize, y, x + 6 * pixelSize, y + pixelSize, color);
        // 第 2 行：最宽
        context.fill(x, y + pixelSize, x + 7 * pixelSize, y + 2 * pixelSize, color);
        // 第 3 行：最宽
        context.fill(x, y + 2 * pixelSize, x + 7 * pixelSize, y + 3 * pixelSize, color);
        // 第 4 行：收窄
        context.fill(x + 1 * pixelSize, y + 3 * pixelSize, x + 6 * pixelSize, y + 4 * pixelSize, color);
        // 第 5 行：更窄
        context.fill(x + 2 * pixelSize, y + 4 * pixelSize, x + 5 * pixelSize, y + 5 * pixelSize, color);
        // 第 6 行：尖端
        context.fill(x + 3 * pixelSize, y + 5 * pixelSize, x + 4 * pixelSize, y + 6 * pixelSize, color);
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
     * @param context 图形上下文
     * @param x        左上角 X
     * @param y        左上角 Y
     * @param width    宽度
     * @param height   高度
     * @param radius   圆角半径
     */
    public static void fillRoundedPanel(DrawContext context, int x, int y, int width, int height, int radius) {
        ModConfig config = ConfigManager.getConfig();
        int alpha = (int) (config.getGuiOpacity() * 255) & 0xFF;
        fillVanillaPanel(context, x, y, width, height, alpha);
    }

    /**
     * 绘制 Minecraft 原版风格面板（参考好友列表）。
     * 边框结构（由外到内）：外黑1px(R角) + 白2px(255,下右阴影) + 灰4px(198) + 内黑1px + 内填充RGB(55,55,55)
     */
    public static void fillVanillaPanel(DrawContext context, int x, int y, int width, int height, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        int innerColor = (a << 24) | 0x373737;
        int black = 0xFF000000;
        int white = 0xFFFFFFFF;
        int gray198 = 0xFFC6C6C6;
        int shadowGray = 0xFF9B9B9B;

        // 1. 内部填充 RGB(55,55,55) (offset 8+)
        context.fill(x + 8, y + 8, x + width - 8, y + height - 8, innerColor);
        // 2. 内层黑色描边 1px (offset 7)
        context.fill(x + 7, y + 7, x + width - 7, y + 8, black);
        context.fill(x + 7, y + height - 8, x + width - 7, y + height - 7, black);
        context.fill(x + 7, y + 8, x + 8, y + height - 8, black);
        context.fill(x + width - 8, y + 8, x + width - 7, y + height - 8, black);
        // 3. 浅灰边框 4px RGB(198,198,198) (offset 3-6)
        context.fill(x + 3, y + 3, x + width - 3, y + 7, gray198);
        context.fill(x + 3, y + height - 7, x + width - 3, y + height - 3, gray198);
        context.fill(x + 3, y + 7, x + 7, y + height - 7, gray198);
        context.fill(x + width - 7, y + 7, x + width - 3, y + height - 7, gray198);
        // 4. 白色边框 2px (offset 1-2)：上左纯白，下右阴影灰
        context.fill(x + 1, y + 1, x + width - 1, y + 3, white);
        context.fill(x + 1, y + 3, x + 3, y + height - 3, white);
        context.fill(x + 1, y + height - 3, x + width - 1, y + height - 1, shadowGray);
        context.fill(x + width - 3, y + 3, x + width - 1, y + height - 3, shadowGray);
        // 5. 外层黑色描边 1px + R角 (offset 0)
        context.fill(x + 2, y, x + width - 2, y + 1, black);
        context.fill(x + 2, y + height - 1, x + width - 2, y + height, black);
        context.fill(x, y + 2, x + 1, y + height - 2, black);
        context.fill(x + width - 1, y + 2, x + width, y + height - 2, black);
        context.fill(x + 1, y + 1, x + 2, y + 2, black);
        context.fill(x + width - 2, y + 1, x + width - 1, y + 2, black);
        context.fill(x + 1, y + height - 2, x + 2, y + height - 1, black);
        context.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, black);
    }

    /**
     * 绘制圆角填充矩形。
     * <p>逐行计算圆角覆盖范围，使用 floor 确保 corner 区域无像素缺失。</p>
     *
     * @param context 图形上下文
     * @param x        左上角 X
     * @param y        左上角 Y
     * @param width    宽度
     * @param height   高度
     * @param color    ARGB 颜色
     * @param radius   圆角半径
     */
    public static void fillRounded(DrawContext context, int x, int y, int width, int height, int color, int radius) {
        int r = Math.min(radius, Math.min(width, height) / 2);
        if (r <= 0) {
            context.fill(x, y, x + width, y + height, color);
            return;
        }
        // 中间区域（圆角之间的完整矩形）
        context.fill(x, y + r, x + width, y + height - r, color);
        // 用 Bresenham 圆弧点计算每行 inset，确保与 drawRoundedOutline 边框圆弧匹配
        int[] inset = computeArcInset(r);
        // 逐行填充顶部和底部圆角区域
        for (int i = 0; i < r; i++) {
            int ins = inset[i];
            // 顶部行：从 x+ins 到 x+width-ins
            context.fill(x + ins, y + i, x + width - ins, y + i + 1, color);
            // 底部行
            context.fill(x + ins, y + height - 1 - i, x + width - ins, y + height - i, color);
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
     * @param context 图形上下文
     * @param x        左上角 X
     * @param y        左上角 Y
     * @param width    宽度
     * @param height   高度
     * @param color    边框颜色
     * @param radius   圆角半径
     */
    public static void drawRoundedOutline(DrawContext context, int x, int y, int width, int height, int color, int radius) {
        int r = Math.min(radius, Math.min(width, height) / 2);
        if (r <= 0) {
            // 替代 graphics.outline(x, y, w, h, color)：用 4 次 fill 绘制边框
            context.fill(x, y, x + width, y + 1, color);       // 上边
            context.fill(x, y + height - 1, x + width, y + height, color); // 下边
            context.fill(x, y, x + 1, y + height, color);       // 左边
            context.fill(x + width - 1, y, x + width, y + height, color); // 右边
            return;
        }
        // 圆角弧顶端（i=0）的 inset 值：直线段需从该 inset 处开始，覆盖圆弧顶部 y=0 行的多个 Bresenham 点
        double dy0 = r - 0.5;
        double dx0 = Math.sqrt((double) r * r - dy0 * dy0);
        int insetTop = Math.max(0, Math.min((int) Math.floor(r - dx0), r));
        // 上下边（直线段）—— 从 insetTop 到 width-insetTop
        context.fill(x + insetTop, y, x + width - insetTop, y + 1, color);
        context.fill(x + insetTop, y + height - 1, x + width - insetTop, y + height, color);
        // 左右边（直线段）
        context.fill(x, y + r, x + 1, y + height - r, color);
        context.fill(x + width - 1, y + r, x + width, y + height - r, color);
        // 用 Bresenham 画圆算法画四角圆弧（每个角 1/4 圆弧）
        // 圆心：左上(x+r, y+r)、右上(x+width-1-r, y+r)、左下(x+r, y+height-1-r)、右下(x+width-1-r, y+height-1-r)
        drawCornerArcBresenham(context, x + r, y + r, r, color, -1, -1);
        drawCornerArcBresenham(context, x + width - 1 - r, y + r, r, color, 1, -1);
        drawCornerArcBresenham(context, x + r, y + height - 1 - r, r, color, -1, 1);
        drawCornerArcBresenham(context, x + width - 1 - r, y + height - 1 - r, r, color, 1, 1);
    }

    /**
     * 用 Bresenham 画圆算法绘制 1/4 圆弧（边框点）。
     * <p>从 (cx, cy±r) 到 (cx±r, cy) 的弧线，每个点 8-连通连续。
     * sx/sy 控制弧所在象限：左上(-1,-1)、右上(+1,-1)、左下(-1,+1)、右下(+1,+1)。</p>
     *
     * @param context 图形上下文
     * @param cx       圆心 X
     * @param cy       圆心 Y
     * @param r        半径
     * @param color    颜色
     * @param sx       X 方向符号（-1 或 +1）
     * @param sy       Y 方向符号（-1 或 +1）
     */
    private static void drawCornerArcBresenham(DrawContext context, int cx, int cy, int r, int color, int sx, int sy) {
        int X = 0, Y = r;
        int d = 1 - r;
        while (X <= Y) {
            // 点1: (cx + sx*X, cy + sy*Y)
            int px1 = cx + sx * X;
            int py1 = cy + sy * Y;
            context.fill(px1, py1, px1 + 1, py1 + 1, color);
            // 点2: (cx + sx*Y, cy + sy*X)（X != Y 时，避免重复）
            if (X != Y) {
                int px2 = cx + sx * Y;
                int py2 = cy + sy * X;
                context.fill(px2, py2, px2 + 1, py2 + 1, color);
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
    public boolean shouldPause() {
        return false;
    }
}
