package cn.tealc.asmr.ui.tray;

import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link TrayMenuGeometry} 的多分辨率 / 多缩放 / 任务栏方向定位测试。
 * 使用纯几何输入，不需要启动 JavaFX 工具包。
 */
class TrayMenuGeometryTest {

    private static final double MENU_WIDTH = 184;
    private static final double MENU_HEIGHT = 260;
    private static final double MARGIN = 8;
    private static final double DELTA = 0.001;

    @Test
    void bottomTaskbarPlacesMenuAboveItAndAlignsRightEdgeWithCursor() {
        TrayMenuGeometry.Placement placement = compute(
                rect(0, 0, 2560, 1440), rect(0, 0, 2560, 1440), rect(0, 0, 2560, 1392),
                insets(0, 0, 48, 0), 2500, 1420);

        assertEquals(TrayMenuGeometry.Edge.BOTTOM, placement.edge());
        assertEquals(2500 - MENU_WIDTH, placement.x(), DELTA);
        assertEquals(1392 - MARGIN - MENU_HEIGHT, placement.y(), DELTA);
    }

    @Test
    void topTaskbarPlacesMenuBelowIt() {
        TrayMenuGeometry.Placement placement = compute(
                rect(0, 0, 2560, 1440), rect(0, 0, 2560, 1440), rect(0, 40, 2560, 1400),
                insets(40, 0, 0, 0), 2500, 20);

        assertEquals(TrayMenuGeometry.Edge.TOP, placement.edge());
        assertEquals(2500 - MENU_WIDTH, placement.x(), DELTA);
        assertEquals(40 + MARGIN, placement.y(), DELTA);
    }

    @Test
    void leftTaskbarPlacesMenuToItsRight() {
        TrayMenuGeometry.Placement placement = compute(
                rect(0, 0, 2560, 1440), rect(0, 0, 2560, 1440), rect(48, 0, 2512, 1440),
                insets(0, 0, 0, 48), 24, 1400);

        assertEquals(TrayMenuGeometry.Edge.LEFT, placement.edge());
        assertEquals(48 + MARGIN, placement.x(), DELTA);
        assertEquals(1400 - MARGIN - MENU_HEIGHT, placement.y(), DELTA);
    }

    @Test
    void rightTaskbarPlacesMenuToItsLeft() {
        TrayMenuGeometry.Placement placement = compute(
                rect(0, 0, 2560, 1440), rect(0, 0, 2560, 1440), rect(0, 0, 2512, 1440),
                insets(0, 48, 0, 0), 2530, 700);

        assertEquals(TrayMenuGeometry.Edge.RIGHT, placement.edge());
        assertEquals(2512 - MARGIN - MENU_WIDTH, placement.x(), DELTA);
        assertEquals(700 - MARGIN - MENU_HEIGHT, placement.y(), DELTA);
    }

    @Test
    void horizontalTaskbarClampsMenuToVisualRightEdge() {
        TrayMenuGeometry.Placement placement = compute(
                rect(0, 0, 2560, 1440), rect(0, 0, 2560, 1440), rect(0, 0, 2560, 1392),
                insets(0, 0, 48, 0), 2555, 1420);

        assertEquals(TrayMenuGeometry.Edge.BOTTOM, placement.edge());
        assertEquals(2560 - MARGIN - MENU_WIDTH, placement.x(), DELTA);
    }

    @Test
    void mixedDpiMapsCursorProportionallyWithinTheSameMonitor() {
        // 主屏在 AWT 中为 1920×1080，在 JavaFX 中按 150% 缩放为 1280×720。
        TrayMenuGeometry.Placement placement = compute(
                rect(0, 0, 1920, 1080), rect(0, 0, 1280, 720), rect(0, 0, 1280, 696),
                insets(0, 0, 48, 0), 1880, 1060);

        assertEquals(TrayMenuGeometry.Edge.BOTTOM, placement.edge());
        assertEquals(1880 * 1280.0 / 1920 - MENU_WIDTH, placement.x(), 0.01);
        assertEquals(696 - MARGIN - MENU_HEIGHT, placement.y(), DELTA);
    }

    @Test
    void mappingIsRelativeToEachMonitorWhenVirtualOriginsDiffer() {
        // AWT 与 JavaFX 对副屏的虚拟原点不一致时，仍应按该显示器自身的矩形做比例映射。
        TrayMenuGeometry.Placement placement = compute(
                rect(1920, 0, 2560, 1440), rect(1280, 0, 1707, 960), rect(1280, 0, 1707, 936),
                insets(0, 0, 48, 0), 4400, 1420);

        double mappedX = 1280 + (4400 - 1920) * 1707.0 / 2560;
        assertEquals(TrayMenuGeometry.Edge.BOTTOM, placement.edge());
        assertEquals(mappedX - MENU_WIDTH, placement.x(), 0.01);
        assertEquals(936 - MARGIN - MENU_HEIGHT, placement.y(), DELTA);
    }

    @Test
    void negativeCoordinateSecondaryMonitorKeepsMenuOnThatMonitor() {
        TrayMenuGeometry.Placement placement = compute(
                rect(-1920, 0, 1920, 1080), rect(-1920, 0, 1920, 1080), rect(-1920, 0, 1920, 1032),
                insets(0, 0, 48, 0), -80, 1060);

        assertEquals(TrayMenuGeometry.Edge.BOTTOM, placement.edge());
        // 菜单宽度仍在屏幕内，右边缘对齐光标即可。
        assertEquals(-80 - MENU_WIDTH, placement.x(), DELTA);
        assertEquals(1032 - MARGIN - MENU_HEIGHT, placement.y(), DELTA);
    }

    @Test
    void autoHiddenTaskbarFallsBackToNearestScreenEdge() {
        TrayMenuGeometry.Placement placement = compute(
                rect(0, 0, 2560, 1440), rect(0, 0, 2560, 1440), rect(0, 0, 2560, 1440),
                insets(0, 0, 0, 0), 2500, 1420);

        assertEquals(TrayMenuGeometry.Edge.BOTTOM, placement.edge());
        assertEquals(2500 - MENU_WIDTH, placement.x(), DELTA);
        assertEquals(1420 - MARGIN - MENU_HEIGHT, placement.y(), DELTA);
    }

    @Test
    void clickInsideOverflowFlyoutOpensAboveCursorInsteadOfCoveringIt() {
        // 点击溢出面板里的图标时光标在任务栏上方（1300 < 1392），菜单应以点击点为锚向上展开。
        TrayMenuGeometry.Placement placement = compute(
                rect(0, 0, 2560, 1440), rect(0, 0, 2560, 1440), rect(0, 0, 2560, 1392),
                insets(0, 0, 48, 0), 2500, 1300);

        assertEquals(TrayMenuGeometry.Edge.BOTTOM, placement.edge());
        assertEquals(2500 - MENU_WIDTH, placement.x(), DELTA);
        assertEquals(1300 - MARGIN - MENU_HEIGHT, placement.y(), DELTA);
    }

    @Test
    void menuLargerThanVisualAreaStaysAnchoredAtTopLeft() {
        TrayMenuGeometry.Placement placement = compute(
                rect(0, 0, 1000, 300), rect(0, 0, 1000, 300), rect(0, 0, 1000, 300),
                insets(0, 0, 40, 0), 990, 290, 1200, 400);

        assertEquals(TrayMenuGeometry.Edge.BOTTOM, placement.edge());
        assertEquals(MARGIN, placement.x(), DELTA);
        assertEquals(MARGIN, placement.y(), DELTA);
    }

    private static TrayMenuGeometry.Placement compute(Rectangle2D awtScreen,
                                                      Rectangle2D fxScreen,
                                                      Rectangle2D fxVisual,
                                                      TrayMenuGeometry.ScreenInsets insets,
                                                      double cursorX,
                                                      double cursorY) {
        return TrayMenuGeometry.compute(awtScreen, fxScreen, fxVisual, insets,
                cursorX, cursorY, MENU_WIDTH, MENU_HEIGHT, MARGIN);
    }

    private static TrayMenuGeometry.Placement compute(Rectangle2D awtScreen,
                                                      Rectangle2D fxScreen,
                                                      Rectangle2D fxVisual,
                                                      TrayMenuGeometry.ScreenInsets insets,
                                                      double cursorX,
                                                      double cursorY,
                                                      double menuWidth,
                                                      double menuHeight) {
        return TrayMenuGeometry.compute(awtScreen, fxScreen, fxVisual, insets,
                cursorX, cursorY, menuWidth, menuHeight, MARGIN);
    }

    private static Rectangle2D rect(double x, double y, double width, double height) {
        return new Rectangle2D(x, y, width, height);
    }

    private static TrayMenuGeometry.ScreenInsets insets(int top, int right, int bottom, int left) {
        return new TrayMenuGeometry.ScreenInsets(top, right, bottom, left);
    }
}
