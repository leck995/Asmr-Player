package cn.tealc.asmr.ui.tray;

import javafx.geometry.Rectangle2D;

/**
 * 托盘菜单的纯几何计算：把 AWT 的鼠标坐标按所在显示器比例映射到 JavaFX 逻辑坐标，
 * 再根据任务栏所在的方向，以点击点为锚计算菜单左上角。
 * <p>
 * 点击发生在任务栏内时菜单贴着任务栏展开；点击发生在任务栏上方的溢出面板、菜单栏等
 * 区域时以点击点为准展开，避免菜单盖住被点击的图标。不依赖 JavaFX 工具包和 AWT 运行时，
 * 便于用模拟的屏幕矩形覆盖多显示器、混合缩放、上下左右四种任务栏位置等场景。
 */
final class TrayMenuGeometry {

    /** 菜单与屏幕可视区域边缘、任务栏之间的默认间距（JavaFX 逻辑像素）。 */
    static final double DEFAULT_MARGIN = 8;

    private TrayMenuGeometry() {
    }

    /** 任务栏贴靠的屏幕边。 */
    enum Edge {
        TOP, RIGHT, BOTTOM, LEFT
    }

    /**
     * 任务栏占用的屏幕边距，含义与 {@link java.awt.Insets} 一致，仅用于判断任务栏在哪条边。
     * 全部为 0 时（自动隐藏任务栏或系统不提供）回退为根据点击点推断。
     */
    record ScreenInsets(int top, int right, int bottom, int left) {
    }

    /** 菜单左上角坐标（JavaFX 逻辑坐标）与判定出的任务栏边。 */
    record Placement(double x, double y, Edge edge) {
    }

    /**
     * 计算菜单位置。
     *
     * @param awtScreen AWT 点击坐标所在的显示器边界（AWT 坐标）
     * @param fxScreen  同一显示器在 JavaFX 中的边界（JavaFX 逻辑坐标）
     * @param fxVisual  同一显示器的可视边界（排除任务栏，JavaFX 逻辑坐标）
     * @param insets    同一显示器的任务栏边距（AWT 坐标）
     * @param cursorX   AWT 鼠标横坐标
     * @param cursorY   AWT 鼠标纵坐标
     * @param menuWidth 菜单宽度（JavaFX 逻辑像素）
     * @param menuHeight 菜单高度（JavaFX 逻辑像素）
     * @param margin    菜单与可视区域边缘的间距
     * @return 菜单左上角位置
     */
    static Placement compute(Rectangle2D awtScreen,
                             Rectangle2D fxScreen,
                             Rectangle2D fxVisual,
                             ScreenInsets insets,
                             double cursorX,
                             double cursorY,
                             double menuWidth,
                             double menuHeight,
                             double margin) {
        double width = Math.max(menuWidth, 0);
        double height = Math.max(menuHeight, 0);
        double gap = Math.max(margin, 0);

        // 同一显示器内 AWT 与 JavaFX 的坐标是线性关系，按比例映射可以消除缩放差异，
        // 混合 DPI 时也不会把点击点映射到屏幕之外。
        double x = map(cursorX, awtScreen.getMinX(), awtScreen.getWidth(),
                fxScreen.getMinX(), fxScreen.getWidth());
        double y = map(cursorY, awtScreen.getMinY(), awtScreen.getHeight(),
                fxScreen.getMinY(), fxScreen.getHeight());

        Edge edge = detectEdge(insets, fxVisual, x, y);

        double minX = fxVisual.getMinX() + gap;
        double maxX = fxVisual.getMaxX() - gap - width;
        double minY = fxVisual.getMinY() + gap;
        double maxY = fxVisual.getMaxY() - gap - height;

        // 点击点可能落在任务栏内，也可能落在任务栏上方的溢出面板 / 菜单栏里。
        // 先夹到可视区域内，再以点击点为锚展开，避免把菜单压到被点击的图标上。
        double anchorX = Math.max(fxVisual.getMinX(), Math.min(x, fxVisual.getMaxX()));
        double anchorY = Math.max(fxVisual.getMinY(), Math.min(y, fxVisual.getMaxY()));

        return switch (edge) {
            // 横向任务栏：菜单右边缘跟随光标，整体位于点击点上方/下方。
            case BOTTOM -> new Placement(clamp(x - width, minX, maxX),
                    clamp(anchorY - gap - height, minY, maxY), edge);
            case TOP -> new Placement(clamp(x - width, minX, maxX),
                    clamp(anchorY + gap, minY, maxY), edge);
            // 纵向任务栏：菜单贴着点击点右侧/左侧展开。
            case LEFT -> new Placement(clamp(anchorX + gap, minX, maxX),
                    clamp(anchorY - gap - height, minY, maxY), edge);
            case RIGHT -> new Placement(clamp(anchorX - gap - width, minX, maxX),
                    clamp(anchorY - gap - height, minY, maxY), edge);
        };
    }

    private static Edge detectEdge(ScreenInsets insets, Rectangle2D visual, double x, double y) {
        int top = Math.max(insets.top(), 0);
        int right = Math.max(insets.right(), 0);
        int bottom = Math.max(insets.bottom(), 0);
        int left = Math.max(insets.left(), 0);

        if (top > 0 || right > 0 || bottom > 0 || left > 0) {
            // 多边同时有边距时取占用最大的一边；顺序固定，保证结果可预期。
            if (bottom >= top && bottom >= left && bottom >= right) {
                return Edge.BOTTOM;
            }
            if (top >= left && top >= right) {
                return Edge.TOP;
            }
            if (left >= right) {
                return Edge.LEFT;
            }
            return Edge.RIGHT;
        }

        // 自动隐藏任务栏：AWT 边距为 0，用点击点相对可视区域的位置推断。
        if (y >= visual.getMaxY()) {
            return Edge.BOTTOM;
        }
        if (y < visual.getMinY()) {
            return Edge.TOP;
        }
        if (x < visual.getMinX()) {
            return Edge.LEFT;
        }
        if (x >= visual.getMaxX()) {
            return Edge.RIGHT;
        }

        double distanceTop = y - visual.getMinY();
        double distanceBottom = visual.getMaxY() - y;
        double distanceLeft = x - visual.getMinX();
        double distanceRight = visual.getMaxX() - x;
        double nearest = Math.min(Math.min(distanceTop, distanceBottom),
                Math.min(distanceLeft, distanceRight));
        if (nearest == distanceBottom) {
            return Edge.BOTTOM;
        }
        if (nearest == distanceTop) {
            return Edge.TOP;
        }
        if (nearest == distanceLeft) {
            return Edge.LEFT;
        }
        return Edge.RIGHT;
    }

    private static double map(double value, double sourceStart, double sourceSize,
                              double targetStart, double targetSize) {
        if (sourceSize <= 0 || targetSize <= 0) {
            return targetStart;
        }
        return targetStart + (value - sourceStart) * targetSize / sourceSize;
    }

    private static double clamp(double value, double min, double max) {
        if (max < min) {
            // 菜单比可视区域还大时优先保证左上角可见，超出部分由窗口自行溢出。
            return min;
        }
        return Math.max(min, Math.min(value, max));
    }
}
