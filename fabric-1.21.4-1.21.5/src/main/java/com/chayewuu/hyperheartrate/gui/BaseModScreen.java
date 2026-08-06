package com.chayewuu.hyperheartrate.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * 所有 Mod Screen 的基类。
 * <p>
 * 重写 {@code renderBackground} 为空操作，防止 1.21.4-5 的 {@code Screen.render()}
 * 调用 {@code renderBackground} 时对已绘制内容应用原版模糊效果（blur）。
 * 各 Screen 子类自行用 {@code context.fill} 绘制半透明背景遮罩。
 * </p>
 */
public abstract class BaseModScreen extends Screen {
    protected BaseModScreen(Text title) {
        super(title);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // 空操作：不应用原版背景模糊效果，各子类自行绘制背景遮罩
    }
}
