package com.chayewuu.xiaomiheartrate.gui;

import com.chayewuu.xiaomiheartrate.config.ConfigManager;
import com.chayewuu.xiaomiheartrate.config.ModConfig;
import com.chayewuu.xiaomiheartrate.network.HttpServerManager;
import com.chayewuu.xiaomiheartrate.util.ModLogger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * API 设置界面。
 * <p>
 * 配置 HTTP API（Browser API）与 Mod API 的开关、端口、绑定地址等。
 * Mod API 右侧的问号按钮点击后会弹出一个<b>叠加层窗口</b>（不是替换整个屏幕），
 * 显示 Mod API 接口说明。点击弹窗外任意位置或关闭按钮即可关闭弹窗。
 * </p>
 *
 * <p><b>设置项（slider/toggle 的 message 已含标签，无需额外画文字标签）：</b></p>
 * <ol>
 *     <li>HTTP API 开关按钮</li>
 *     <li>Mod API 开关按钮 + 问号说明按钮</li>
 *     <li>HTTP 端口输入框</li>
 *     <li>绑定地址输入框</li>
 *     <li>当前 HTTP 地址显示</li>
 *     <li>应用并重启 HTTP Server 按钮</li>
 *     <li>返回按钮</li>
 * </ol>
 */
public class ApiSettingsScreen extends Screen {
    private static final int PANEL_WIDTH = 380;
    private static final int PANEL_HEIGHT = 230;
    private static final int CORNER_RADIUS = 14;
    private static final int CONTROL_WIDTH = 320;
    private static final int CONTROL_HEIGHT = 20;
    /** Mod API 开关按钮宽度（右侧留出问号按钮空间） */
    private static final int MOD_API_TOGGLE_WIDTH = 284;
    private static final int HELP_BTN_WIDTH = 32;
    private static final int HELP_BTN_GAP = 4;
    /** 控件行间距（足够大避免重叠） */
    private static final int ROW_GAP = 30;

    /** 弹窗（Mod API 说明）尺寸 */
    private static final int HELP_POPUP_WIDTH = 360;
    private static final int HELP_POPUP_HEIGHT = 200;

    private final Screen parent;

    private TextFieldWidget portInputBox;
    private TextFieldWidget bindAddrInputBox;

    private int panelX;
    private int panelY;
    /** 是否显示 Mod API 说明弹窗（叠加层） */
    private boolean showModApiHelp = false;
    /** 弹窗位置 */
    private int popupX;
    private int popupY;

    public ApiSettingsScreen(Screen parent) {
        super(Text.literal("API 设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - PANEL_HEIGHT) / 2;
        if (panelY < 10) {
            panelY = 10;
        }
        popupX = (this.width - HELP_POPUP_WIDTH) / 2;
        popupY = (this.height - HELP_POPUP_HEIGHT) / 2;
        int controlX = panelX + (PANEL_WIDTH - CONTROL_WIDTH) / 2;
        ModConfig config = ConfigManager.getConfig();

        int y = panelY + 36;
        // 1. HTTP API 开关（全宽）—— 立即应用（开则 start，关则 stop）
        addDrawableChild(createToggle("HTTP API (Browser)", config.isHttpApiEnabled(), v -> {
            config.setHttpApiEnabled(v);
            saveConfig();
            HttpServerManager httpMgr = HttpServerManager.getInstance();
            if (v) {
                httpMgr.start();
            } else {
                httpMgr.stop();
            }
            ModLogger.info("[ApiSettings] HTTP API 已{}，Server 状态: {}", v ? "开启" : "关闭", v ? "启动" : "停止");
        }, controlX, y, CONTROL_WIDTH));
        y += ROW_GAP;
        // 2. Mod API 开关（左侧） + 问号按钮（右侧）
        addDrawableChild(createToggle("Mod API", config.isModApiEnabled(), v -> {
            config.setModApiEnabled(v);
            saveConfig();
            ModLogger.info("[ApiSettings] Mod API 已{}", v ? "开启" : "关闭");
        }, controlX, y, MOD_API_TOGGLE_WIDTH));
        ButtonWidget helpBtn = ButtonWidget.builder(
                Text.literal("?"),
                btn -> {
                    showModApiHelp = true;
                    ModLogger.info("[ApiSettings] 打开 Mod API 说明弹窗");
                    clearAndInit();
                }
        ).dimensions(controlX + MOD_API_TOGGLE_WIDTH + HELP_BTN_GAP, y, HELP_BTN_WIDTH, CONTROL_HEIGHT).build();
        addDrawableChild(helpBtn);
        y += ROW_GAP;
        // 3. 端口输入框
        Integer port = config.getHttpPort();
        portInputBox = new TextFieldWidget(this.textRenderer, controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT, Text.literal("端口"));
        portInputBox.setMaxLength(5);
        portInputBox.setText(port != null ? String.valueOf(port) : "");
        portInputBox.setPlaceholder(Text.literal("HTTP 端口（留空=随机）"));
        addDrawableChild(portInputBox);
        y += ROW_GAP;
        // 4. 绑定地址输入框
        bindAddrInputBox = new TextFieldWidget(this.textRenderer, controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT, Text.literal("地址"));
        bindAddrInputBox.setMaxLength(15);
        bindAddrInputBox.setText(config.getBindAddress() != null ? config.getBindAddress() : "127.0.0.1");
        bindAddrInputBox.setPlaceholder(Text.literal("绑定地址（如 127.0.0.1 / 0.0.0.0）"));
        addDrawableChild(bindAddrInputBox);
        y += ROW_GAP;
        // 5. 应用并重启 HTTP Server 按钮
        addDrawableChild(ButtonWidget.builder(
                Text.literal("应用并重启 HTTP Server"),
                btn -> onApplyClicked()
        ).dimensions(controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT).build());
        y += ROW_GAP;
        // 6. 返回按钮
        addDrawableChild(ButtonWidget.builder(
                Text.literal("返回"),
                btn -> close()
        ).dimensions(controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT).build());

        // 7. 弹窗显示时的"关闭说明"按钮（位于弹窗底部）
        if (showModApiHelp) {
            popupX = (this.width - HELP_POPUP_WIDTH) / 2;
            popupY = (this.height - HELP_POPUP_HEIGHT) / 2;
            int closeBtnX = popupX + (HELP_POPUP_WIDTH - 120) / 2;
            int closeBtnY = popupY + HELP_POPUP_HEIGHT - 28;
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("关闭说明"),
                    btn -> {
                        showModApiHelp = false;
                        ModLogger.info("[ApiSettings] 关闭 Mod API 说明弹窗");
                        clearAndInit();
                    }
            ).dimensions(closeBtnX, closeBtnY, 120, CONTROL_HEIGHT).build());
        }
    }

    private void onApplyClicked() {
        ModConfig config = ConfigManager.getConfig();
        String portStr = portInputBox.getText().trim();
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
        String addr = bindAddrInputBox.getText().trim();
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

    private ButtonWidget createToggle(String label, boolean initial, java.util.function.Consumer<Boolean> onChange,
                                int x, int y, int width) {
        return ButtonWidget.builder(
                Text.literal(label + ": " + (initial ? "开" : "关")),
                b -> {
                    boolean current = b.getMessage().getString().endsWith("开");
                    boolean next = !current;
                    b.setMessage(Text.literal(label + ": " + (next ? "开" : "关")));
                    onChange.accept(next);
                }
        ).dimensions(x, y, width, CONTROL_HEIGHT).build();
    }

    private static void saveConfig() {
        ConfigManager.save();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 背景遮罩 + 主面板
        context.fill(0, 0, this.width, this.height, 0x80000000);
        MainScreen.fillRoundedPanel(context, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, CORNER_RADIUS);

        int centerX = panelX + PANEL_WIDTH / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, "API 设置", centerX, panelY + 12, 0xFFFFD700);

        // 当前 HTTP 地址显示（面板底部）
        String addr = HttpServerManager.getInstance().getAddress();
        context.drawCenteredTextWithShadow(this.textRenderer, "当前: " + addr, centerX, panelY + PANEL_HEIGHT - 14, 0xFF66CCFF);

        // 渲染所有 widget（按钮、输入框）
        super.render(context, mouseX, mouseY, delta);

        // Mod API 说明弹窗（叠加层，渲染在最上层）
        if (showModApiHelp) {
            renderHelpPopup(context, mouseX, mouseY);
        }
    }

    /**
     * 渲染 Mod API 说明弹窗（叠加层）。
     * 弹窗居中显示在主面板上方，包含 API 接口列表和关闭按钮。
     */
    private void renderHelpPopup(DrawContext context, int mouseX, int mouseY) {
        // 半透明遮罩（覆盖整个屏幕，点击外部可关闭）
        context.fill(0, 0, this.width, this.height, 0x99000000);

        // 弹窗背景（半透明深色面板）
        MainScreen.fillRoundedPanel(context, popupX, popupY, HELP_POPUP_WIDTH, HELP_POPUP_HEIGHT, CORNER_RADIUS);

        int centerX = popupX + HELP_POPUP_WIDTH / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, "Mod API 接口说明", centerX, popupY + 10, 0xFFFFD700);

        // API 接口列表
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
            context.drawText(this.textRenderer, line, textX, textY, color, false);
            textY += 12;
        }
        textY += 4;
        context.drawText(this.textRenderer, "包路径: com.chayewuu.xiaomiheartrate.api", textX, textY, textColor, false);
        textY += 12;
        context.drawText(this.textRenderer, "用法: 引入本 Mod 依赖后直接调用静态方法", textX, textY, textColor, false);
        // 关闭按钮由 init 中的 widget 提供，这里不画提示
    }

    @Override
    public void close() {
        // 弹窗显示时，ESC 只关闭弹窗，不退出整个界面
        if (showModApiHelp) {
            showModApiHelp = false;
            clearAndInit();
            return;
        }
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
