package com.chayewuu.xiaomiheartrate.gui;

import com.chayewuu.xiaomiheartrate.config.ConfigManager;
import com.chayewuu.xiaomiheartrate.config.ModConfig;
import com.chayewuu.xiaomiheartrate.util.ModLogger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * 心率显示位置调整界面。
 * <p>
 * 左上角有模块化模式开关按钮。非模块化模式下拖动整体，模块化模式下拖动单个模块。
 * </p>
 */
public class PositionAdjustScreen extends Screen {
    private final Screen parent;

    public PositionAdjustScreen(Screen parent) {
        super(Text.literal("位置调整"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        HeartRateHudWidget.getInstance().setDragModeEnabled(true);
        // 左上角模块化模式开关
        addDrawableChild(ButtonWidget.builder(
                Text.literal(getModularButtonText()),
                btn -> toggleModular()
        ).dimensions(10, 10, 140, 20).build());
    }

    private String getModularButtonText() {
        boolean modular = ConfigManager.getConfig().isModularHudEnabled();
        return "模块化: " + (modular ? "开" : "关");
    }

    private void toggleModular() {
        ModConfig config = ConfigManager.getConfig();
        config.setModularHudEnabled(!config.isModularHudEnabled());
        ConfigManager.save();
        // 刷新按钮文本
        this.clearAndInit();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x20000000);
        HeartRateHudWidget.getInstance().render(context, this.width, this.height);

        // 提示文字
        ModConfig config = ConfigManager.getConfig();
        String hint = config.isModularHudEnabled()
                ? "模块化模式：点击单个模块拖动（ESC 返回）"
                : "拖动心率显示到理想位置，松开自动保存（ESC 返回）";
        int textWidth = this.textRenderer.getWidth(hint);
        int textX = (this.width - textWidth) / 2;
        int textY = 12;
        context.fill(textX - 10, textY - 4, textX + textWidth + 10, textY + 14, 0xCC000000);
        context.drawText(this.textRenderer, hint, textX, textY, 0xFFFFFFAA, false);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean clickCount) {
        if (click.button() == 0) {
            HeartRateHudWidget.getInstance().tryStartDrag((int) click.x(), (int) click.y());
        }
        return super.mouseClicked(click, clickCount);
    }

    @Override
    public boolean mouseDragged(Click click, double dragX, double dragY) {
        if (click.button() == 0) {
            HeartRateHudWidget.getInstance().onDragUpdate(
                    (int) click.x(), (int) click.y(), this.width, this.height);
        }
        return super.mouseDragged(click, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0) {
            HeartRateHudWidget.getInstance().finishDrag(this.width, this.height);
        }
        return super.mouseReleased(click);
    }

    @Override
    public void close() {
        HeartRateHudWidget.getInstance().setDragModeEnabled(false);
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
