package com.chayewuu.xiaomiheartrate.gui;

import com.chayewuu.xiaomiheartrate.HeartRateMod;
import com.chayewuu.xiaomiheartrate.device.BleDevice;
import com.chayewuu.xiaomiheartrate.device.ConnectionCallback;
import com.chayewuu.xiaomiheartrate.device.ConnectionState;
import com.chayewuu.xiaomiheartrate.device.DeviceManager;
import com.chayewuu.xiaomiheartrate.device.DeviceManagerHolder;
import com.chayewuu.xiaomiheartrate.device.DeviceType;
import com.chayewuu.xiaomiheartrate.device.ManualBleDevice;
import com.chayewuu.xiaomiheartrate.util.ModLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 设备选择界面。
 * <p>
 * 内置蓝牙扫描界面，显示扫描到的小米/Redmi 设备列表，并支持手动输入 MAC 地址连接。
 * 仅显示小米手环、小米手表、Redmi Watch 等受支持设备。
 * </p>
 *
 * <p><b>布局：</b></p>
 * <pre>
 * ┌──────────────────────────────────────┐
 * │         选择设备                      │  标题
 * │ 扫描状态: 扫描中... / 已停止          │
 * │ ─────────────────────────────────── │
 * │ [设备1: Mi Band 9 (AA:BB:...)]      │  设备列表
 * │ [设备2: Redmi Watch (CC:DD:...)]     │
 * │ ...                                  │
 * │ ─────────────────────────────────── │
 * │ 手动输入 MAC: [________________]     │  MAC 输入框
 * │ [手动连接]                           │
 * │ [重新扫描]  [返回]                   │
 * └──────────────────────────────────────┘
 * </pre>
 */
public class DeviceSelectionScreen extends Screen {
    /** 主面板宽度 */
    private static final int PANEL_WIDTH = 360;
    /** 主面板高度（自适应） */
    private static final int PANEL_HEIGHT = 240;
    /** 圆角半径 */
    private static final int CORNER_RADIUS = 14;
    /** 设备按钮宽度 */
    private static final int DEVICE_BTN_WIDTH = 320;
    /** 设备按钮高度 */
    private static final int DEVICE_BTN_HEIGHT = 18;
    /** 最多显示的设备数量 */
    private static final int MAX_DISPLAY_DEVICES = 6;

    /** 父屏幕 */
    private final Screen parent;

    /** 扫描到的设备列表（线程安全，BLE 回调在后台线程） */
    private final List<BleDevice> discoveredDevices = new CopyOnWriteArrayList<>();

    /** MAC 输入框 */
    private EditBox macInputBox;
    /** 上次扫描到的设备数量（用于检测变化触发重新布局） */
    private int lastDeviceCount = 0;

    /** 主面板左上角 X */
    private int panelX;
    /** 主面板左上角 Y */
    private int panelY;

    /** 是否正在扫描 */
    private volatile boolean scanning = false;

    /**
     * 构造设备选择界面。
     *
     * @param parent 父屏幕
     */
    public DeviceSelectionScreen(Screen parent) {
        super(Component.translatable("screen." + HeartRateMod.MOD_ID + ".device_selection"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - PANEL_HEIGHT) / 2;

        int controlX = panelX + (PANEL_WIDTH - DEVICE_BTN_WIDTH) / 2;

        // MAC 输入框
        int macY = panelY + PANEL_HEIGHT - 70;
        macInputBox = new EditBox(this.font, controlX + 100, macY, 220, 16, Component.literal("MAC"));
        macInputBox.setMaxLength(17); // AA:BB:CC:DD:EE:FF = 17 字符
        macInputBox.setHint(Component.literal("AA:BB:CC:DD:EE:FF"));
        addRenderableWidget(macInputBox);

        // 手动连接按钮
        addRenderableWidget(Button.builder(
                Component.literal("连接"),
                btn -> onManualConnectClicked()
        ).bounds(controlX + 100, macY + 18, 100, 16).build());

        // 重新扫描按钮
        addRenderableWidget(Button.builder(
                Component.literal("重新扫描"),
                btn -> startScan()
        ).bounds(controlX, macY + 18, 90, 16).build());

        // 返回按钮
        addRenderableWidget(Button.builder(
                Component.literal("返回"),
                btn -> onClose()
        ).bounds(controlX + 210, macY + 18, 110, 16).build());

        // 添加设备按钮
        rebuildDeviceButtons();

        // 首次打开自动开始扫描
        if (!scanning && discoveredDevices.isEmpty()) {
            startScan();
        }
    }

    /**
     * 根据已发现设备列表重建设备按钮。
     */
    private void rebuildDeviceButtons() {
        // 移除旧的设备按钮（通过重新 init 实现）
        // MC 26.2 中 addRenderableWidget 添加的 widget 在 init 时会被清空
        // 这里手动添加设备按钮
        int controlX = panelX + (PANEL_WIDTH - DEVICE_BTN_WIDTH) / 2;
        int deviceListStartY = panelY + 40;
        int count = Math.min(discoveredDevices.size(), MAX_DISPLAY_DEVICES);
        for (int i = 0; i < count; i++) {
            BleDevice device = discoveredDevices.get(i);
            int y = deviceListStartY + i * (DEVICE_BTN_HEIGHT + 2);
            String label = formatDeviceLabel(device);
            addRenderableWidget(Button.builder(
                    Component.literal(label),
                    btn -> onDeviceSelected(device)
            ).bounds(controlX, y, DEVICE_BTN_WIDTH, DEVICE_BTN_HEIGHT).build());
        }
    }

    /**
     * 格式化设备标签。
     *
     * @param device 设备
     * @return 形如 "Mi Band 9 (AA:BB:CC:DD:EE:FF) -60dBm" 的标签
     */
    private static String formatDeviceLabel(BleDevice device) {
        String name = device.getName();
        if (name == null || name.isEmpty()) {
            name = "未知设备";
        }
        String addr = device.getAddress();
        int rssi = device.getRssi();
        String typeTag = getTypeTag(device.getType());
        return typeTag + " " + name + " (" + addr + ") " + rssi + "dBm";
    }

    /**
     * 获取设备类型标签。
     *
     * @param type 设备类型
     * @return 中文标签
     */
    private static String getTypeTag(DeviceType type) {
        return switch (type) {
            case XIAOMI_BAND -> "[小米手环]";
            case XIAOMI_WATCH -> "[小米手表]";
            case REDMI_WATCH -> "[红米手表]";
            case STANDARD_GATT -> "[标准GATT]";
            default -> "[未知]";
        };
    }

    /**
     * 开始扫描设备。
     */
    private void startScan() {
        discoveredDevices.clear();
        lastDeviceCount = 0;
        scanning = true;
        ModLogger.info("[DeviceSelection] 开始扫描设备");

        DeviceManager manager = DeviceManagerHolder.get();
        manager.startScan(device -> {
            if (device == null) return;
            // 去重
            for (BleDevice existing : discoveredDevices) {
                if (existing.getAddress().equals(device.getAddress())) {
                    return;
                }
            }
            discoveredDevices.add(device);
            ModLogger.info("[DeviceSelection] 发现设备: {} ({}) type={}",
                    device.getName(), device.getAddress(), device.getType());
        });
    }

    /**
     * 停止扫描。
     */
    private void stopScan() {
        if (scanning) {
            DeviceManagerHolder.get().stopScan();
            scanning = false;
        }
    }

    /**
     * 设备被选中时连接。
     *
     * @param device 选中的设备
     */
    private void onDeviceSelected(BleDevice device) {
        ModLogger.info("[DeviceSelection] 用户选择设备: {} ({})",
                device.getName(), device.getAddress());
        stopScan();
        DeviceManager manager = DeviceManagerHolder.get();
        manager.connect(device, new ConnectionCallback() {
            @Override
            public void onConnected(BleDevice d) {
                ModLogger.info("[DeviceSelection] 设备已连接: {}", d.getName());
            }

            @Override
            public void onDisconnected(BleDevice d) {
                ModLogger.info("[DeviceSelection] 设备已断开: {}", d.getName());
            }

            @Override
            public void onError(String message, Throwable cause) {
                ModLogger.error("[DeviceSelection] 连接错误: " + message, cause);
            }
        });
        onClose();
    }

    /**
     * 手动输入 MAC 连接。
     */
    private void onManualConnectClicked() {
        String mac = macInputBox.getValue().trim();
        if (!isValidMac(mac)) {
            ModLogger.warn("[DeviceSelection] MAC 地址格式无效: {}", mac);
            return;
        }
        ModLogger.info("[DeviceSelection] 手动连接 MAC: {}", mac);
        stopScan();
        ManualBleDevice device = new ManualBleDevice(mac);
        onDeviceSelected(device);
    }

    /**
     * 验证 MAC 地址格式。
     *
     * @param mac MAC 地址字符串
     * @return {@code true} 表示格式有效
     */
    private static boolean isValidMac(String mac) {
        if (mac == null || mac.isEmpty()) return false;
        // 支持 AA:BB:CC:DD:EE:FF 或 AABBCCDDEEFF 格式
        String cleaned = mac.replace(":", "").replace("-", "").replace(" ", "");
        if (cleaned.length() != 12) return false;
        for (char c : cleaned.toCharArray()) {
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        // 检测设备列表变化，触发重新布局
        int currentCount = discoveredDevices.size();
        if (currentCount != lastDeviceCount) {
            lastDeviceCount = currentCount;
            // 重新初始化以更新设备按钮列表
            rebuildWidgets();
        }
        // 扫描状态检测：连接状态变为非扫描时停止扫描标志
        ConnectionState state = DeviceManagerHolder.get().getConnectionState();
        if (scanning && state != ConnectionState.SCANNING) {
            scanning = false;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // 渲染顺序：背景遮罩 → 面板 → 文字 → 按钮（最上层）
        graphics.fill(0, 0, this.width, this.height, 0x80000000);
        MainScreen.fillRoundedPanel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, CORNER_RADIUS);

        int centerX = panelX + PANEL_WIDTH / 2;

        // 标题
        graphics.centeredText(this.font, "选择设备", centerX, panelY + 12, 0xFFFFD700);

        // 扫描状态
        String status = scanning ? "扫描状态: 扫描中..." : "扫描状态: 已停止";
        int statusColor = scanning ? 0xFF66CCFF : 0xFFAAAAAA;
        graphics.centeredText(this.font, status, centerX, panelY + 26, statusColor);

        // 分隔线
        int dividerY = panelY + 40 + MAX_DISPLAY_DEVICES * (DEVICE_BTN_HEIGHT + 2) + 4;
        graphics.fill(panelX + 16, dividerY, panelX + PANEL_WIDTH - 16, dividerY + 1, 0xFF_5A5A7A);

        // MAC 输入提示
        graphics.text(this.font, "手动输入 MAC:",
                panelX + (PANEL_WIDTH - DEVICE_BTN_WIDTH) / 2,
                panelY + PANEL_HEIGHT - 70, 0xFFAAAAAA, false);

        // 设备列表为空时的提示
        if (discoveredDevices.isEmpty()) {
            String hint = scanning ? "正在搜索 BLE 设备..." : "未发现设备";
            graphics.centeredText(this.font, hint, centerX,
                    panelY + 40 + MAX_DISPLAY_DEVICES * (DEVICE_BTN_HEIGHT + 2) / 2, 0xFF888888);
            // 提示开启心率广播
            graphics.centeredText(this.font, "提示: 小米手环需在设置→心率广播中开启", centerX,
                    panelY + 40 + MAX_DISPLAY_DEVICES * (DEVICE_BTN_HEIGHT + 2) / 2 + 12, 0xFFAA8800);

            // 显示扫描错误信息（如 dotnet 未安装）
            String error = DeviceManagerHolder.get().getScanError();
            if (error != null && !error.isEmpty()) {
                graphics.centeredText(this.font, "错误: " + error, centerX,
                        panelY + 40 + MAX_DISPLAY_DEVICES * (DEVICE_BTN_HEIGHT + 2) / 2 + 24, 0xFFFF5555);
                graphics.centeredText(this.font, "需安装 .NET 10 运行时: dotnet.microsoft.com", centerX,
                        panelY + 40 + MAX_DISPLAY_DEVICES * (DEVICE_BTN_HEIGHT + 2) / 2 + 36, 0xFFFF8800);
            }
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        stopScan();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
