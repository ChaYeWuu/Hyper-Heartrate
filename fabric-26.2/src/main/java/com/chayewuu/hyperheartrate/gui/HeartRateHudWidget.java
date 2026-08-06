package com.chayewuu.hyperheartrate.gui;

import com.chayewuu.hyperheartrate.config.ConfigManager;
import com.chayewuu.hyperheartrate.config.ModConfig;
import com.chayewuu.hyperheartrate.heart.HeartRateManager;
import com.chayewuu.hyperheartrate.util.ModLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

/**
 * 心率 HUD 渲染组件。
 * <p>
 * 支持两种渲染模式：
 * <ul>
 *     <li><b>非模块化模式</b>：所有元素（图标、心率、设备名）作为整体渲染和拖动；</li>
 *     <li><b>模块化模式</b>：每个元素独立位置，可单独拖动和显隐。</li>
 * </ul>
 * </p>
 *
 * <p>心率图标支持<b>心跳模式</b>：根据实时心率值自动变色（蓝→绿→黄→红）。</p>
 */
public class HeartRateHudWidget {
    private static volatile HeartRateHudWidget instance;

    private final DragComponent dragComponent;
    private volatile boolean dragModeEnabled;

    /** 当前正在拖动的模块（仅模块化模式） */
    private volatile HudModule draggingModule;

    // 各模块边界缓存（命中检测用）
    private int iconX, iconY, iconW, iconH;
    private int textX, textY, textW, textH;
    private int devX, devY, devW, devH;

    /** 非模块化模式下的整体边界 */
    private int cachedWidth = 80;
    private int cachedHeight = 16;

    private HeartRateHudWidget() {
        this.dragComponent = new DragComponent();
    }

    public static HeartRateHudWidget getInstance() {
        if (instance == null) {
            synchronized (HeartRateHudWidget.class) {
                if (instance == null) {
                    instance = new HeartRateHudWidget();
                }
            }
        }
        return instance;
    }

    public void setDragModeEnabled(boolean enabled) {
        this.dragModeEnabled = enabled;
        if (!enabled && dragComponent.isDragging()) {
            dragComponent.endDrag();
            draggingModule = null;
        }
    }

    // ===== 渲染 =====

    public void render(GuiGraphicsExtractor graphics, int screenWidth, int screenHeight) {
        ModConfig config = ConfigManager.getConfig();
        // HUD 总开关：关闭后游戏内不渲染心率 HUD
        if (!config.isHudEnabled()) {
            return;
        }
        if (config.isModularHudEnabled()) {
            renderModular(graphics, screenWidth, screenHeight, config);
        } else {
            renderUnified(graphics, screenWidth, screenHeight, config);
        }
    }

    /**
     * 非模块化渲染：所有元素作为整体。
     */
    private void renderUnified(GuiGraphicsExtractor graphics, int screenWidth, int screenHeight, ModConfig config) {
        Font font = ModFontManager.getFont();
        HeartRateManager hrm = HeartRateManager.getInstance();

        int posX = (int) (config.getHudX() * screenWidth);
        int posY = (int) (config.getHudY() * screenHeight);
        if (dragModeEnabled || dragComponent.isDragging()) {
            posX = dragComponent.getPosX();
            posY = dragComponent.getPosY();
            if (posX == 0 && posY == 0) {
                posX = (int) (config.getHudX() * screenWidth);
                posY = (int) (config.getHudY() * screenHeight);
                dragComponent.setPosition(posX, posY);
            }
        } else {
            dragComponent.setPosition(posX, posY);
        }

        int heartRate = hrm.getCurrentHeartRate();
        boolean showIcon = config.isShowIcon();
        boolean showBpm = config.isShowBpmSuffix();
        // 未连接（heartRate<=0）时占位符也尊重 showBpm 开关
        String heartRateText = heartRate > 0
                ? (showBpm ? heartRate + " BPM" : String.valueOf(heartRate))
                : (showBpm ? "— BPM" : "—");

        float[] rgba = config.getFontColor();
        int argbColor = colorToArgb(rgba, config.getGuiOpacity());
        // 未连接（heartRate<=0）时统一灰色，不受心动模式影响
        // 心动模式各元素独立变色开关
        boolean colorMode = config.isHeartRateColorMode();
        int heartColor = colorMode ? getHeartColor(heartRate) : 0xFFFF4060;
        // 心率图标颜色：未连接时灰色；否则心动模式且开启图标变色时用 heartColor
        int iconColor = heartRate <= 0 ? 0xFFAAAAAA : ((colorMode && config.isHeartColorIcon()) ? heartColor : 0xFFFF4060);
        // 心率数值颜色
        int rateColor = (colorMode && config.isHeartColorRate()) ? heartColor : argbColor;
        // BPM 后缀颜色
        int bpmColor = (colorMode && config.isHeartColorBpm()) ? heartColor : argbColor;
        // 用于设备名的颜色（不变色）
        int dnColor = argbColor;
        // 是否需要分段渲染（心率和 BPM 变色设置不同且都显示 BPM）
        boolean splitRender = showBpm && (config.isHeartColorRate() != config.isHeartColorBpm()) && colorMode && heartRate > 0;

        float hrScale = (float) config.getHeartRateFontScale();
        float dnScale = (float) config.getDeviceNameFontScale();
        int iconScale = config.getIconScale();

        int baseLineHeight = font.lineHeight;
        int hrLineHeight = (int) (baseLineHeight * hrScale);
        int dnLineHeight = (int) (baseLineHeight * dnScale);
        int padding = 4;
        int iconWidth = showIcon ? (7 * iconScale + 3) : 0;
        int hrTextWidth = (int) (font.width(heartRateText) * hrScale);
        int bgWidth = hrTextWidth + iconWidth + padding * 2;
        int bgHeight = hrLineHeight + padding * 2;

        // 设备名
        String deviceName = "";
        boolean showDevice = config.isShowDeviceName() && hrm.isConnected() && hrm.getCurrentDevice() != null;
        if (showDevice) {
            String cn = config.getCustomDeviceName();
            deviceName = (cn != null && !cn.isEmpty()) ? cn : hrm.getCurrentDevice().getName();
            if (deviceName == null || deviceName.isEmpty()) {
                showDevice = false;
            } else {
                int nameW = (int) (font.width(deviceName) * dnScale) + padding * 2;
                if (showIcon) nameW += iconWidth;
                if (nameW > bgWidth) bgWidth = nameW;
                bgHeight = dnLineHeight + hrLineHeight + padding * 2;
            }
        }

        // HUD 背景板（scoreboard 风格：简单半透明黑色，无边框）
        if (config.isHudBackgroundEnabled()) {
            int bgAlpha = (int) (clamp(config.getHudBackgroundOpacity(), 0f, 1f) * 255) & 0xFF;
            graphics.fill(posX - 3, posY - 3, posX + bgWidth + 3, posY + bgHeight + 3, (bgAlpha << 24) | 0x000000);
        }

        if (dragModeEnabled) {
            graphics.fill(posX, posY, posX + bgWidth, posY + bgHeight, 0x66000000);
            graphics.outline(posX, posY, bgWidth, bgHeight, 0xFF00AAFF);
        }

        int textStartX = posX + padding;
        if (showIcon) {
            int heartX = posX + padding;
            int heartRowY = showDevice ? (posY + padding + dnLineHeight) : (posY + padding);
            int heartY = heartRowY + (hrLineHeight - 6 * iconScale) / 2;
            MainScreen.drawPixelHeart(graphics, heartX, heartY, iconColor, iconScale);
            textStartX = heartX + 7 * iconScale + 3;
        }

        Matrix3x2fStack stack = graphics.pose();
        // 心率行 Y 坐标
        int hrRowY = showDevice ? (posY + padding + dnLineHeight) : (posY + padding);
        if (showDevice) {
            // 设备名行（不变色）
            stack.pushMatrix();
            stack.translate(posX + padding, posY + padding);
            stack.scale(dnScale, dnScale);
            graphics.text(font, deviceName, 0, 0, dnColor, true);
            stack.popMatrix();
        }

        if (splitRender) {
            // 分段渲染：心率数字用 rateColor，" BPM" 用 bpmColor
            String rateStr = String.valueOf(heartRate);
            String bpmStr = " BPM";
            int rateWidth = font.width(rateStr);
            int bpmWidth = font.width(bpmStr);
            stack.pushMatrix();
            stack.translate(textStartX, hrRowY);
            stack.scale(hrScale, hrScale);
            graphics.text(font, rateStr, 0, 0, rateColor, true);
            stack.popMatrix();
            stack.pushMatrix();
            stack.translate(textStartX + (int)(rateWidth * hrScale), hrRowY);
            stack.scale(hrScale, hrScale);
            graphics.text(font, bpmStr, 0, 0, bpmColor, true);
            stack.popMatrix();
        } else {
            // 统一渲染：心率+BPM 使用同一颜色（当 rateColor 和 bpmColor 相同时）
            int unifiedColor = rateColor;
            stack.pushMatrix();
            stack.translate(textStartX, hrRowY);
            stack.scale(hrScale, hrScale);
            graphics.text(font, heartRateText, 0, 0, unifiedColor, true);
            stack.popMatrix();
        }

        cachedWidth = bgWidth;
        cachedHeight = bgHeight;
    }

    /**
     * 模块化渲染：各模块独立位置。
     */
    private void renderModular(GuiGraphicsExtractor graphics, int screenWidth, int screenHeight, ModConfig config) {
        Font font = ModFontManager.getFont();
        HeartRateManager hrm = HeartRateManager.getInstance();

        int heartRate = hrm.getCurrentHeartRate();
        boolean showIcon = config.isShowIcon();
        boolean showBpm = config.isShowBpmSuffix();
        // 未连接（heartRate<=0）时占位符也尊重 showBpm 开关
        String heartRateText = heartRate > 0
                ? (showBpm ? heartRate + " BPM" : String.valueOf(heartRate))
                : (showBpm ? "— BPM" : "—");
        boolean colorMode = config.isHeartRateColorMode();
        // 未连接（heartRate<=0）时图标统一灰色，不受心动模式影响
        int heartColor = colorMode ? getHeartColor(heartRate) : 0xFFFF4060;
        int iconColor = heartRate <= 0 ? 0xFFAAAAAA : ((colorMode && config.isHeartColorIcon()) ? heartColor : 0xFFFF4060);
        int rateColor = (colorMode && config.isHeartColorRate()) ? heartColor : colorToArgb(config.getFontColor(), config.getGuiOpacity());
        int bpmColor = (colorMode && config.isHeartColorBpm()) ? heartColor : colorToArgb(config.getFontColor(), config.getGuiOpacity());
        boolean splitRender = showBpm && (config.isHeartColorRate() != config.isHeartColorBpm()) && colorMode && heartRate > 0;

        float[] rgba = config.getFontColor();
        int argbColor = colorToArgb(rgba, config.getGuiOpacity());
        float hrScale = (float) config.getHeartRateFontScale();
        float dnScale = (float) config.getDeviceNameFontScale();
        int iconScale = config.getIconScale();

        // 心率图标模块
        if (showIcon) {
            int ix = (int) (config.getHeartIconX() * screenWidth);
            int iy = (int) (config.getHeartIconY() * screenHeight);
            if (dragModeEnabled && draggingModule == HudModule.HEART_ICON) {
                ix = dragComponent.getPosX();
                iy = dragComponent.getPosY();
            }
            int iw = 7 * iconScale;
            int ih = 6 * iconScale;

            if (dragModeEnabled) {
                graphics.fill(ix - 2, iy - 2, ix + iw + 2, iy + ih + 2, 0x66000000);
                graphics.outline(ix - 2, iy - 2, iw + 4, ih + 4,
                        draggingModule == HudModule.HEART_ICON ? 0xFFFFAA00 : 0xFF00AAFF);
            }
            MainScreen.drawPixelHeart(graphics, ix, iy, iconColor, iconScale);
            iconX = ix; iconY = iy; iconW = iw; iconH = ih;
        }

        // 心率文字模块
        {
            int tx = (int) (config.getHeartRateTextX() * screenWidth);
            int ty = (int) (config.getHeartRateTextY() * screenHeight);
            if (dragModeEnabled && draggingModule == HudModule.HEART_RATE_TEXT) {
                tx = dragComponent.getPosX();
                ty = dragComponent.getPosY();
            }
            int tw = (int) (font.width(heartRateText) * hrScale);
            int th = (int) (font.lineHeight * hrScale);

            if (dragModeEnabled) {
                graphics.fill(tx - 2, ty - 2, tx + tw + 2, ty + th + 2, 0x66000000);
                graphics.outline(tx - 2, ty - 2, tw + 4, th + 4,
                        draggingModule == HudModule.HEART_RATE_TEXT ? 0xFFFFAA00 : 0xFF00AAFF);
            }
            Matrix3x2fStack stack = graphics.pose();
            if (splitRender) {
                String rateStr = String.valueOf(heartRate);
                String bpmStr = " BPM";
                int rateWidth = font.width(rateStr);
                stack.pushMatrix();
                stack.translate(tx, ty);
                stack.scale(hrScale, hrScale);
                graphics.text(font, rateStr, 0, 0, rateColor, true);
                stack.popMatrix();
                stack.pushMatrix();
                stack.translate(tx + (int)(rateWidth * hrScale), ty);
                stack.scale(hrScale, hrScale);
                graphics.text(font, bpmStr, 0, 0, bpmColor, true);
                stack.popMatrix();
            } else {
                stack.pushMatrix();
                stack.translate(tx, ty);
                stack.scale(hrScale, hrScale);
                graphics.text(font, heartRateText, 0, 0, rateColor, true);
                stack.popMatrix();
            }
            textX = tx; textY = ty; textW = tw; textH = th;
        }

        // 设备名模块
        boolean showDevice = config.isShowDeviceName() && hrm.isConnected() && hrm.getCurrentDevice() != null;
        if (showDevice) {
            String cn = config.getCustomDeviceName();
            String deviceName = (cn != null && !cn.isEmpty()) ? cn : hrm.getCurrentDevice().getName();
            if (deviceName != null && !deviceName.isEmpty()) {
                int dx = (int) (config.getDeviceNameModuleX() * screenWidth);
                int dy = (int) (config.getDeviceNameModuleY() * screenHeight);
                if (dragModeEnabled && draggingModule == HudModule.DEVICE_NAME) {
                    dx = dragComponent.getPosX();
                    dy = dragComponent.getPosY();
                }
                int dw = (int) (font.width(deviceName) * dnScale);
                int dh = (int) (font.lineHeight * dnScale);

                if (dragModeEnabled) {
                    graphics.fill(dx - 2, dy - 2, dx + dw + 2, dy + dh + 2, 0x66000000);
                    graphics.outline(dx - 2, dy - 2, dw + 4, dh + 4,
                            draggingModule == HudModule.DEVICE_NAME ? 0xFFFFAA00 : 0xFF00AAFF);
                }
                Matrix3x2fStack stack = graphics.pose();
                stack.pushMatrix();
                stack.translate(dx, dy);
                stack.scale(dnScale, dnScale);
                graphics.text(font, deviceName, 0, 0, argbColor, true);
                stack.popMatrix();
                devX = dx; devY = dy; devW = dw; devH = dh;
            }
        }
    }

    // ===== 拖动处理 =====

    public boolean tryStartDrag(int mouseX, int mouseY) {
        if (!dragModeEnabled) return false;
        ModConfig config = ConfigManager.getConfig();
        if (config.isModularHudEnabled()) {
            // 模块化模式：检测点击了哪个模块
            // 关键：调用 startDrag 前必须将 dragComponent 的 posX/posY 设为模块当前位置，
            // 否则 startDrag 内部用 mouseX - posX 计算偏移时，posX 是旧值导致跳跃
            if (config.isShowIcon() && hit(mouseX, mouseY, iconX, iconY, iconW, iconH)) {
                draggingModule = HudModule.HEART_ICON;
                dragComponent.setPosition(iconX, iconY);
                dragComponent.startDrag(mouseX, mouseY);
                return true;
            }
            if (hit(mouseX, mouseY, textX, textY, textW, textH)) {
                draggingModule = HudModule.HEART_RATE_TEXT;
                dragComponent.setPosition(textX, textY);
                dragComponent.startDrag(mouseX, mouseY);
                return true;
            }
            if (config.isShowDeviceName() && hit(mouseX, mouseY, devX, devY, devW, devH)) {
                draggingModule = HudModule.DEVICE_NAME;
                dragComponent.setPosition(devX, devY);
                dragComponent.startDrag(mouseX, mouseY);
                return true;
            }
            return false;
        } else {
            // 非模块化模式：检测整体边界
            int px = dragComponent.getPosX();
            int py = dragComponent.getPosY();
            if (hit(mouseX, mouseY, px, py, cachedWidth, cachedHeight)) {
                draggingModule = HudModule.UNIFIED;
                dragComponent.startDrag(mouseX, mouseY);
                return true;
            }
            return false;
        }
    }

    public void onDragUpdate(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (!dragComponent.isDragging()) return;
        dragComponent.onDrag(mouseX, mouseY);
        int newPosX = clamp(dragComponent.getPosX(), 0, Math.max(0, screenWidth - 10));
        int newPosY = clamp(dragComponent.getPosY(), 0, Math.max(0, screenHeight - 10));
        dragComponent.setPosition(newPosX, newPosY);
    }

    public void finishDrag(int screenWidth, int screenHeight) {
        if (!dragComponent.isDragging()) return;
        dragComponent.endDrag();
        ModConfig config = ConfigManager.getConfig();
        double rx = (double) dragComponent.getPosX() / screenWidth;
        double ry = (double) dragComponent.getPosY() / screenHeight;
        rx = clampD(rx, 0.0, 1.0);
        ry = clampD(ry, 0.0, 1.0);
        if (draggingModule == HudModule.HEART_ICON) {
            config.setHeartIconX(rx); config.setHeartIconY(ry);
        } else if (draggingModule == HudModule.HEART_RATE_TEXT) {
            config.setHeartRateTextX(rx); config.setHeartRateTextY(ry);
        } else if (draggingModule == HudModule.DEVICE_NAME) {
            config.setDeviceNameModuleX(rx); config.setDeviceNameModuleY(ry);
        } else {
            config.setHudX(rx); config.setHudY(ry);
        }
        ConfigManager.save();
        draggingModule = null;
        ModLogger.debug("[HUD] 模块位置已保存: ({}, {})", rx, ry);
    }

    // ===== 心率颜色 =====

    /**
     * 根据心率值返回对应颜色。
     * <ul>
     *   <li>&lt;60：蓝色（平静）</li>
     *   <li>60-100：绿色（正常）</li>
     *   <li>100-140：黄色（偏高）</li>
     *   <li>&gt;140：红色（过高）</li>
     * </ul>
     */
    private static int getHeartColor(int heartRate) {
        if (heartRate <= 0) return 0xFFAAAAAA;
        if (heartRate < 60) return 0xFF4080FF;
        if (heartRate <= 100) return 0xFF40FF60;
        if (heartRate <= 140) return 0xFFFFD700;
        return 0xFFFF4040;
    }

    // ===== 工具 =====

    private static boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x - 2 && mx <= x + w + 2 && my >= y - 2 && my <= y + h + 2;
    }

    private static int colorToArgb(float[] rgba, float opacity) {
        float r = clamp(rgba.length > 0 ? rgba[0] : 1.0f, 0.0f, 1.0f);
        float g = clamp(rgba.length > 1 ? rgba[1] : 1.0f, 0.0f, 1.0f);
        float b = clamp(rgba.length > 2 ? rgba[2] : 1.0f, 0.0f, 1.0f);
        float a = clamp(rgba.length > 3 ? rgba[3] : 1.0f, 0.0f, 1.0f) * clamp(opacity, 0.0f, 1.0f);
        return ((int)(a*255)&0xFF)<<24 | ((int)(r*255)&0xFF)<<16 | ((int)(g*255)&0xFF)<<8 | ((int)(b*255)&0xFF);
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private static double clampD(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }

    /** HUD 模块枚举 */
    private enum HudModule {
        UNIFIED, HEART_ICON, HEART_RATE_TEXT, DEVICE_NAME
    }
}
