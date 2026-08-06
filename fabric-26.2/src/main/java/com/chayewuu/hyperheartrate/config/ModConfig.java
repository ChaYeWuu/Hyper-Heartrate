package com.chayewuu.hyperheartrate.config;

/**
 * Mod 配置数据类。
 * <p>
 * 保存 GUI 显示、HUD 位置、网络服务、BLE 行为等所有可配置项。
 * 使用 GSON 进行序列化/反序列化，持久化到 {@code config/heartrate.json}。
 * </p>
 *
 * <p>注意：</p>
 * <ul>
 *     <li>所有字段均有默认值，配合隐式无参构造器使 GSON 反序列化生效；</li>
 *     <li>新增字段时务必给定默认值，避免旧配置文件加载后出现 {@code null}。</li>
 * </ul>
 */
public class ModConfig {
    // ===== GUI 显示设置 =====

    /** HUD 总开关（关闭后游戏内不渲染心率 HUD） */
    private boolean hudEnabled = true;
    /** GUI 透明度，范围 0~1 */
    private float guiOpacity = 0.85f;
    /** 字体大小 */
    private int fontSize = 16;
    /** 字体颜色 RGBA，归一化到 0~1 */
    private float[] fontColor = {1.0f, 1.0f, 1.0f, 1.0f};
    /** 是否显示心率图标 */
    private boolean showIcon = true;
    /** 是否显示设备名称 */
    private boolean showDeviceName = true;
    /** 用户自定义设备名称（为空时使用设备真实名称） */
    private String customDeviceName = "";
    /** 是否显示 FPS（预留） */
    private boolean showFps = false;
    /** 是否显示 Ping（预留） */
    private boolean showPing = false;

    // ===== 字体大小设置（各模块独立缩放）=====

    /** 心率文字缩放（0.5~3.0，1.0=原始大小） */
    private double heartRateFontScale = 1.0;
    /** 设备名称文字缩放（0.5~2.0） */
    private double deviceNameFontScale = 1.0;
    /** GUI 面板文字缩放（0.5~2.0） */
    private double guiFontScale = 1.0;
    /** 心率图标缩放（1~4，每个像素点绘制为 NxN） */
    private int iconScale = 1;

    // ===== HUD 位置（按屏幕宽高比例，0~1）=====

    /** HUD 横向位置，屏幕宽度比例 */
    private double hudX = 0.05;
    /** HUD 纵向位置，屏幕高度比例 */
    private double hudY = 0.4;

    // ===== 模块化 HUD 设置 =====

    /** 是否启用模块化模式（各模块独立位置） */
    private boolean modularHudEnabled = false;
    /** 是否显示 BPM 后缀 */
    private boolean showBpmSuffix = true;
    /** 心动模式：根据心率自动变色（仅限心率图标、心率文字、BPM 后缀） */
    private boolean heartRateColorMode = false;
    /** 心动模式 - 心率图标是否变色 */
    private boolean heartColorIcon = true;
    /** 心动模式 - 心率数值是否变色 */
    private boolean heartColorRate = true;
    /** 心动模式 - BPM 后缀是否变色 */
    private boolean heartColorBpm = true;
    /** HUD 背景板开关（开启后心率 HUD 后方渲染圆角半透明背景板） */
    private boolean hudBackgroundEnabled = false;
    /** HUD 背景板透明度，范围 0~1 */
    private float hudBackgroundOpacity = 0.45f;
    // 模块化模式下各模块独立位置（屏幕比例 0~1）
    private double heartIconX = 0.05;
    private double heartIconY = 0.40;
    private double heartRateTextX = 0.08;
    private double heartRateTextY = 0.40;
    private double deviceNameModuleX = 0.05;
    private double deviceNameModuleY = 0.43;

    // ===== 网络设置 =====

    /** HTTP Server 端口，{@code null} 表示使用随机端口 */
    private Integer httpPort = null;
    /** HTTP Server 绑定地址 */
    private String bindAddress = "127.0.0.1";

    // ===== BLE 设置 =====

    /** 是否启用自动重连 */
    private boolean autoReconnect = true;
    /** 自动重连间隔（毫秒） */
    private int reconnectDelayMs = 5000;
    /** 上次连接的设备 MAC 地址（用于自动重连） */
    private String lastDeviceAddress = null;
    /** 上次连接的设备名称 */
    private String lastDeviceName = null;

    // ===== 心率采集设置 =====

    /** 心率采集间隔（毫秒），0 表示使用设备默认频率 */
    private int captureIntervalMs = 1000;
    /** 历史心率数据保留数量（环形缓冲大小） */
    private int historySize = 300;

    // ===== API 设置 =====

    /** 是否启用 HTTP API（Browser API） */
    private boolean httpApiEnabled = true;
    /** 是否启用 Mod API（供其他 Mod 调用） */
    private boolean modApiEnabled = true;

    // ===== 联机心率同步设置 =====

    /** 联机功能总开关（开启后在其他玩家 NameTag 上方/下方显示心率） */
    private boolean multiplayerEnabled = false;
    /** 心率相对 NameTag 的显示位置："above"=上方, "below"=下方 */
    private String multiplayerNametagPosition = "below";
    /** 联机模式是否显示心率图标 */
    private boolean multiplayerShowIcon = true;
    /** 联机模式是否显示 BPM 后缀 */
    private boolean multiplayerShowBpm = true;
    /** 联机模式心动模式总开关 */
    private boolean multiplayerHeartColorMode = false;
    /** 联机模式心动 - 心率图标变色 */
    private boolean multiplayerHeartColorIcon = true;
    /** 联机模式心动 - 心率数值变色 */
    private boolean multiplayerHeartColorRate = true;
    /** 联机模式心动 - BPM 后缀变色 */
    private boolean multiplayerHeartColorBpm = true;

    // ===== TAB 列表心率显示 =====
    /** TAB 列表显示心率总开关 */
    private boolean multiplayerTabListEnabled = false;
    /** TAB 列表显示心率图标 */
    private boolean multiplayerTabListShowIcon = true;
    /** TAB 列表显示心率数值 */
    private boolean multiplayerTabListShowRate = true;
    /** TAB 列表显示 BPM 后缀 */
    private boolean multiplayerTabListShowBpm = false;

    /** 无参构造器（隐式），供 GSON 反序列化使用 */
    public ModConfig() {
    }

    public float getGuiOpacity() {
        return guiOpacity;
    }

    public void setGuiOpacity(float guiOpacity) {
        this.guiOpacity = guiOpacity;
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
    }

    public float[] getFontColor() {
        return fontColor;
    }

    public void setFontColor(float[] fontColor) {
        this.fontColor = fontColor;
    }

    public boolean isShowIcon() {
        return showIcon;
    }

    public void setShowIcon(boolean showIcon) {
        this.showIcon = showIcon;
    }

    public boolean isShowDeviceName() {
        return showDeviceName;
    }

    public void setShowDeviceName(boolean showDeviceName) {
        this.showDeviceName = showDeviceName;
    }

    public String getCustomDeviceName() {
        return customDeviceName == null ? "" : customDeviceName;
    }

    public void setCustomDeviceName(String customDeviceName) {
        this.customDeviceName = customDeviceName == null ? "" : customDeviceName;
    }

    public boolean isShowFps() {
        return showFps;
    }

    public void setShowFps(boolean showFps) {
        this.showFps = showFps;
    }

    public boolean isShowPing() {
        return showPing;
    }

    public void setShowPing(boolean showPing) {
        this.showPing = showPing;
    }

    public double getHudX() {
        return hudX;
    }

    public void setHudX(double hudX) {
        this.hudX = hudX;
    }

    public double getHudY() {
        return hudY;
    }

    public void setHudY(double hudY) {
        this.hudY = hudY;
    }

    public Integer getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(Integer httpPort) {
        this.httpPort = httpPort;
    }

    public String getBindAddress() {
        return bindAddress;
    }

    public void setBindAddress(String bindAddress) {
        this.bindAddress = bindAddress;
    }

    public boolean isAutoReconnect() {
        return autoReconnect;
    }

    public void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }

    public int getReconnectDelayMs() {
        return reconnectDelayMs;
    }

    public void setReconnectDelayMs(int reconnectDelayMs) {
        this.reconnectDelayMs = reconnectDelayMs;
    }

    public String getLastDeviceAddress() {
        return lastDeviceAddress;
    }

    public void setLastDeviceAddress(String lastDeviceAddress) {
        this.lastDeviceAddress = lastDeviceAddress;
    }

    public String getLastDeviceName() {
        return lastDeviceName;
    }

    public void setLastDeviceName(String lastDeviceName) {
        this.lastDeviceName = lastDeviceName;
    }

    public int getCaptureIntervalMs() {
        return captureIntervalMs;
    }

    public void setCaptureIntervalMs(int captureIntervalMs) {
        this.captureIntervalMs = captureIntervalMs;
    }

    public int getHistorySize() {
        return historySize;
    }

    public void setHistorySize(int historySize) {
        this.historySize = historySize;
    }

    public boolean isHttpApiEnabled() {
        return httpApiEnabled;
    }

    public void setHttpApiEnabled(boolean httpApiEnabled) {
        this.httpApiEnabled = httpApiEnabled;
    }

    public boolean isModApiEnabled() {
        return modApiEnabled;
    }

    public void setModApiEnabled(boolean modApiEnabled) {
        this.modApiEnabled = modApiEnabled;
    }

    // ===== 字体大小设置 getter/setter =====

    public double getHeartRateFontScale() {
        return heartRateFontScale;
    }

    public void setHeartRateFontScale(double heartRateFontScale) {
        this.heartRateFontScale = heartRateFontScale;
    }

    public double getDeviceNameFontScale() {
        return deviceNameFontScale;
    }

    public void setDeviceNameFontScale(double deviceNameFontScale) {
        this.deviceNameFontScale = deviceNameFontScale;
    }

    public double getGuiFontScale() {
        return guiFontScale;
    }

    public void setGuiFontScale(double guiFontScale) {
        this.guiFontScale = guiFontScale;
    }

    public int getIconScale() {
        return iconScale;
    }

    public void setIconScale(int iconScale) {
        this.iconScale = iconScale;
    }

    // ===== 模块化 HUD getter/setter =====

    public boolean isModularHudEnabled() {
        return modularHudEnabled;
    }

    public void setModularHudEnabled(boolean modularHudEnabled) {
        this.modularHudEnabled = modularHudEnabled;
    }

    public boolean isShowBpmSuffix() {
        return showBpmSuffix;
    }

    public void setShowBpmSuffix(boolean showBpmSuffix) {
        this.showBpmSuffix = showBpmSuffix;
    }

    public boolean isHeartRateColorMode() {
        return heartRateColorMode;
    }

    public void setHeartRateColorMode(boolean heartRateColorMode) {
        this.heartRateColorMode = heartRateColorMode;
    }

    public boolean isHeartColorIcon() {
        return heartColorIcon;
    }

    public void setHeartColorIcon(boolean v) {
        this.heartColorIcon = v;
    }

    public boolean isHeartColorRate() {
        return heartColorRate;
    }

    public void setHeartColorRate(boolean v) {
        this.heartColorRate = v;
    }

    public boolean isHeartColorBpm() {
        return heartColorBpm;
    }

    public void setHeartColorBpm(boolean v) {
        this.heartColorBpm = v;
    }

    public double getHeartIconX() {
        return heartIconX;
    }

    public void setHeartIconX(double v) {
        this.heartIconX = v;
    }

    public double getHeartIconY() {
        return heartIconY;
    }

    public void setHeartIconY(double v) {
        this.heartIconY = v;
    }

    public double getHeartRateTextX() {
        return heartRateTextX;
    }

    public void setHeartRateTextX(double v) {
        this.heartRateTextX = v;
    }

    public double getHeartRateTextY() {
        return heartRateTextY;
    }

    public void setHeartRateTextY(double v) {
        this.heartRateTextY = v;
    }

    public double getDeviceNameModuleX() {
        return deviceNameModuleX;
    }

    public void setDeviceNameModuleX(double v) {
        this.deviceNameModuleX = v;
    }

    public double getDeviceNameModuleY() {
        return deviceNameModuleY;
    }

    public void setDeviceNameModuleY(double v) {
        this.deviceNameModuleY = v;
    }

    // ===== HUD 总开关 / 背景板 / 通用设备过滤 getter/setter =====

    public boolean isHudEnabled() {
        return hudEnabled;
    }

    public void setHudEnabled(boolean hudEnabled) {
        this.hudEnabled = hudEnabled;
    }

    public boolean isHudBackgroundEnabled() {
        return hudBackgroundEnabled;
    }

    public void setHudBackgroundEnabled(boolean hudBackgroundEnabled) {
        this.hudBackgroundEnabled = hudBackgroundEnabled;
    }

    public float getHudBackgroundOpacity() {
        return hudBackgroundOpacity;
    }

    public void setHudBackgroundOpacity(float hudBackgroundOpacity) {
        this.hudBackgroundOpacity = hudBackgroundOpacity;
    }

    // ===== 联机心率同步 getter/setter =====

    public boolean isMultiplayerEnabled() {
        return multiplayerEnabled;
    }

    public void setMultiplayerEnabled(boolean multiplayerEnabled) {
        this.multiplayerEnabled = multiplayerEnabled;
    }

    public String getMultiplayerNametagPosition() {
        String v = multiplayerNametagPosition;
        return ("above".equals(v) || "inside".equals(v)) ? v : "below";
    }

    public void setMultiplayerNametagPosition(String position) {
        this.multiplayerNametagPosition = ("above".equals(position) || "inside".equals(position)) ? position : "below";
    }

    public boolean isMultiplayerShowIcon() {
        return multiplayerShowIcon;
    }

    public void setMultiplayerShowIcon(boolean multiplayerShowIcon) {
        this.multiplayerShowIcon = multiplayerShowIcon;
    }

    public boolean isMultiplayerShowBpm() {
        return multiplayerShowBpm;
    }

    public void setMultiplayerShowBpm(boolean multiplayerShowBpm) {
        this.multiplayerShowBpm = multiplayerShowBpm;
    }

    public boolean isMultiplayerHeartColorMode() {
        return multiplayerHeartColorMode;
    }

    public void setMultiplayerHeartColorMode(boolean multiplayerHeartColorMode) {
        this.multiplayerHeartColorMode = multiplayerHeartColorMode;
    }

    public boolean isMultiplayerHeartColorIcon() {
        return multiplayerHeartColorIcon;
    }

    public void setMultiplayerHeartColorIcon(boolean v) {
        this.multiplayerHeartColorIcon = v;
    }

    public boolean isMultiplayerHeartColorRate() {
        return multiplayerHeartColorRate;
    }

    public void setMultiplayerHeartColorRate(boolean v) {
        this.multiplayerHeartColorRate = v;
    }

    public boolean isMultiplayerHeartColorBpm() {
        return multiplayerHeartColorBpm;
    }

    public void setMultiplayerHeartColorBpm(boolean v) {
        this.multiplayerHeartColorBpm = v;
    }

    public boolean isMultiplayerTabListEnabled() {
        return multiplayerTabListEnabled;
    }

    public void setMultiplayerTabListEnabled(boolean v) {
        this.multiplayerTabListEnabled = v;
    }

    public boolean isMultiplayerTabListShowIcon() {
        return multiplayerTabListShowIcon;
    }

    public void setMultiplayerTabListShowIcon(boolean v) {
        this.multiplayerTabListShowIcon = v;
    }

    public boolean isMultiplayerTabListShowRate() {
        return multiplayerTabListShowRate;
    }

    public void setMultiplayerTabListShowRate(boolean v) {
        this.multiplayerTabListShowRate = v;
    }

    public boolean isMultiplayerTabListShowBpm() {
        return multiplayerTabListShowBpm;
    }

    public void setMultiplayerTabListShowBpm(boolean v) {
        this.multiplayerTabListShowBpm = v;
    }
}
