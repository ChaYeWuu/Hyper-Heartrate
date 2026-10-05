package com.chayewuu.hyperheartrate.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Mod 字体管理器。
 * <p>
 * 统一封装 Mod 内文字渲染入口。当前直接使用 Minecraft 默认 Font。
 * 保留此类作为未来字体切换的扩展点，并集中常用渲染辅助方法。
 * </p>
 */
public final class ModFontManager {
    private ModFontManager() {
    }

    /**
     * 获取当前使用的 Font。
     *
     * @return Minecraft 默认 Font 实例
     */
    public static Font getFont() {
        return Minecraft.getInstance().font;
    }

    /**
     * 渲染文本。
     *
     * @param graphics    图形上下文
     * @param font        字体
     * @param text        文本
     * @param x           X 坐标
     * @param y           Y 坐标
     * @param color       ARGB 颜色
     * @param dropShadow  是否显示阴影
     */
    public static void text(GuiGraphicsExtractor graphics, Font font, String text,
                            int x, int y, int color, boolean dropShadow) {
        graphics.text(font, text, x, y, color, dropShadow);
    }

    /**
     * 渲染居中文本。
     */
    public static void centeredText(GuiGraphicsExtractor graphics, Font font, String text,
                                    int centerX, int y, int color) {
        int textWidth = font.width(text);
        int x = centerX - textWidth / 2;
        text(graphics, font, text, x, y, color, false);
    }
}
