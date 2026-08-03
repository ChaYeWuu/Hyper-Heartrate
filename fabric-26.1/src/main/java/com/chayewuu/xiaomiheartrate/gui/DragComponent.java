package com.chayewuu.xiaomiheartrate.gui;

/**
 * 可拖动逻辑封装。
 * <p>
 * 封装 HUD 组件拖动状态管理，包括是否正在拖动、拖动起始偏移量。
 * 由 {@link HeartRateHudWidget} 在鼠标事件中调用，实现 HUD 位置的实时调整。
 * </p>
 *
 * <p>使用流程：</p>
 * <ol>
 *     <li>鼠标按下时调用 {@link #startDrag(int, int)}；</li>
 *     <li>鼠标移动时调用 {@link #onDrag(int, int)} 更新位置；</li>
 *     <li>鼠标释放时调用 {@link #endDrag()} 结束拖动。</li>
 * </ol>
 *
 * <p>该类非线程安全，仅在客户端主线程（渲染线程）使用。</p>
 */
public class DragComponent {
    /** 当前是否正在拖动 */
    private boolean dragging;
    /** 拖动起始时鼠标 X 坐标与组件左上角 X 坐标的偏移量 */
    private int dragOffsetX;
    /** 拖动起始时鼠标 Y 坐标与组件左上角 Y 坐标的偏移量 */
    private int dragOffsetY;
    /** 组件当前左上角 X 坐标（像素） */
    private int posX;
    /** 组件当前左上角 Y 坐标（像素） */
    private int posY;

    /**
     * 默认构造器。
     */
    public DragComponent() {
        this.dragging = false;
        this.dragOffsetX = 0;
        this.dragOffsetY = 0;
        this.posX = 0;
        this.posY = 0;
    }

    /**
     * 设置组件当前位置（像素坐标）。
     *
     * @param posX 左上角 X
     * @param posY 左上角 Y
     */
    public void setPosition(int posX, int posY) {
        this.posX = posX;
        this.posY = posY;
    }

    /**
     * 获取组件当前 X 坐标。
     *
     * @return 左上角 X（像素）
     */
    public int getPosX() {
        return posX;
    }

    /**
     * 获取组件当前 Y 坐标。
     *
     * @return 左上角 Y（像素）
     */
    public int getPosY() {
        return posY;
    }

    /**
     * 查询当前是否正在拖动。
     *
     * @return {@code true} 表示处于拖动状态
     */
    public boolean isDragging() {
        return dragging;
    }

    /**
     * 判断指定坐标是否落在组件范围内（用于决定是否开始拖动）。
     *
     * @param mouseX  鼠标 X
     * @param mouseY  鼠标 Y
     * @param width   组件宽度
     * @param height  组件高度
     * @return {@code true} 表示坐标命中组件
     */
    public boolean isHit(int mouseX, int mouseY, int width, int height) {
        return mouseX >= posX && mouseX <= posX + width
                && mouseY >= posY && mouseY <= posY + height;
    }

    /**
     * 开始拖动。
     * <p>记录鼠标相对组件左上角的偏移量，避免拖动起始时组件跳变。</p>
     *
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     */
    public void startDrag(int mouseX, int mouseY) {
        this.dragging = true;
        this.dragOffsetX = mouseX - posX;
        this.dragOffsetY = mouseY - posY;
    }

    /**
     * 拖动过程中更新组件位置。
     * <p>仅在拖动状态下生效，保证鼠标与组件相对偏移不变。</p>
     *
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     */
    public void onDrag(int mouseX, int mouseY) {
        if (!dragging) {
            return;
        }
        this.posX = mouseX - dragOffsetX;
        this.posY = mouseY - dragOffsetY;
    }

    /**
     * 结束拖动。
     */
    public void endDrag() {
        this.dragging = false;
        this.dragOffsetX = 0;
        this.dragOffsetY = 0;
    }
}
