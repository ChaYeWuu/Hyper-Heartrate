package com.chayewuu.hyperheartrate.gui;

import com.chayewuu.hyperheartrate.config.ConfigManager;
import com.chayewuu.hyperheartrate.config.ModConfig;
import com.chayewuu.hyperheartrate.util.ModLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * 心率显示位置调整界面。
 * <p>
 * 左上角有模块化模式开关按钮。非模块化模式下拖动整体，模块化模式下拖动单个模块。
 * </p>
 */
public class PositionAdjustScreen extends Screen {
    private final Screen parent;

    public PositionAdjustScreen(Screen parent) {
        super(Component.literal("位置调整"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        HeartRateHudWidget.getInstance().setDragModeEnabled(true);
        // 左上角模块化模式开关
        addRenderableWidget(Button.builder(
                Component.literal(getModularButtonText()),
                btn -> toggleModular()
        ).bounds(10, 10, 140, 20).build());
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
        this.rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0x20000000);
        HeartRateHudWidget.getInstance().render(graphics, this.width, this.height);

        // 提示文字
        ModConfig config = ConfigManager.getConfig();
        String hint = config.isModularHudEnabled()
                ? "模块化模式：点击单个模块拖动（ESC 返回）"
                : "拖动心率显示到理想位置，松开自动保存（ESC 返回）";
        int textWidth = this.font.width(hint);
        int textX = (this.width - textWidth) / 2;
        int textY = 12;
        graphics.fill(textX - 10, textY - 4, textX + textWidth + 10, textY + 14, 0xCC000000);
        graphics.text(this.font, hint, textX, textY, 0xFFFFFFAA, false);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean clickCount) {
        if (event.button() == 0) {
            HeartRateHudWidget.getInstance().tryStartDrag((int) event.x(), (int) event.y());
        }
        return super.mouseClicked(event, clickCount);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == 0) {
            HeartRateHudWidget.getInstance().onDragUpdate(
                    (int) event.x(), (int) event.y(), this.width, this.height);
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            HeartRateHudWidget.getInstance().finishDrag(this.width, this.height);
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        HeartRateHudWidget.getInstance().setDragModeEnabled(false);
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
