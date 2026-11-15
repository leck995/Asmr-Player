package cn.tealc.asmr.ui.tray;

import cn.tealc.asmr.Main;
import cn.tealc.asmr.player.VlcPlayer;
import cn.tealc.asmr.utils.CssLoader;
import cn.tealc.asmr.utils.FontUtils;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * 系统托盘：AWT {@link SystemTray} 负责图标与点击事件，菜单使用无边框 JavaFX 窗口绘制。
 * <p>
 * 定位时先把 AWT 的点击坐标按所在显示器的比例映射为 JavaFX 逻辑坐标，再按任务栏所在的边
 * 贴边放置，以适配多显示器、系统缩放以及任务栏位于上下左右的情况。托盘图标按主屏缩放
 * 生成对应像素尺寸，避免高 DPI 下被再次放大而发虚。
 * <p>
 * 所有方法都应在 JavaFX 应用线程调用；AWT 事件会先切回 JavaFX 线程再操作窗口。
 * 系统不支持托盘（例如部分 Linux 桌面）时 {@link #install()} 返回 false，调用方需要降级为最小化。
 */
public final class TrayManager {

    private static final Logger LOG = LoggerFactory.getLogger(TrayManager.class);

    private static final String ICON_RESOURCE = "/cn/tealc/asmr/image/favicon.png";
    private static final String MENU_CSS = "/skin/tray-menu.css";
    private static final double MENU_MARGIN = TrayMenuGeometry.DEFAULT_MARGIN;

    private static TrayIcon trayIcon;
    private static Stage menuStage;
    private static boolean installed;

    private TrayManager() {
    }

    /**
     * 安装托盘图标。重复调用无副作用。
     *
     * @return 托盘可用并安装成功返回 true
     */
    public static synchronized boolean install() {
        if (installed) {
            return true;
        }
        if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) {
            LOG.info("当前系统不支持托盘，跳过托盘安装");
            return false;
        }
        try {
            Image image = loadTrayImage();
            if (image == null) {
                LOG.warn("托盘图标资源缺失: {}", ICON_RESOURCE);
                return false;
            }
            TrayIcon icon = new TrayIcon(image, "ASMR Player");
            icon.setImageAutoSize(true);
            icon.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    handlePopupTrigger(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    handlePopupTrigger(e);
                }
            });
            // 双击（macOS 为单击）还原主窗口
            icon.addActionListener(e -> Platform.runLater(TrayManager::restoreWindow));
            SystemTray.getSystemTray().add(icon);
            trayIcon = icon;
            installed = true;
            LOG.info("系统托盘已安装");
            return true;
        } catch (Exception e) {
            LOG.warn("系统托盘初始化失败", e);
            return false;
        }
    }

    /**
     * 移除托盘图标并关闭菜单窗口。
     */
    public static synchronized void uninstall() {
        hideMenu();
        if (menuStage != null) {
            menuStage.close();
            menuStage = null;
        }
        if (trayIcon != null) {
            try {
                SystemTray.getSystemTray().remove(trayIcon);
            } catch (Exception e) {
                LOG.warn("移除托盘图标失败", e);
            }
            trayIcon = null;
        }
        installed = false;
    }

    /**
     * @return 托盘图标是否已安装
     */
    public static boolean isInstalled() {
        return installed;
    }

    private static void handlePopupTrigger(MouseEvent e) {
        if (e.isPopupTrigger()) {
            int x = e.getXOnScreen();
            int y = e.getYOnScreen();
            Platform.runLater(() -> showMenu(x, y));
        }
    }

    private static void showMenu(int screenX, int screenY) {
        if (!installed) {
            return;
        }
        Stage stage = menuStage();
        VBox root = (VBox) stage.getScene().getRoot();
        root.applyCss();
        root.layout();

        // show 之前 Stage 的宽高还没有确定，直接取根节点的首选尺寸。
        double width = root.prefWidth(-1);
        double height = root.prefHeight(width);
        if (width <= 0) {
            width = 184;
        }
        if (height <= 0) {
            height = 120;
        }

        ScreenInfo screen = screenAt(screenX, screenY);
        TrayMenuGeometry.Placement placement = TrayMenuGeometry.compute(
                screen.awtBounds(), screen.fxBounds(), screen.fxVisual(), screen.insets(),
                screenX, screenY, width, height, MENU_MARGIN);

        // 已显示时直接移动，避免先 hide 再 show 造成闪烁。
        stage.sizeToScene();
        stage.setX(placement.x());
        stage.setY(placement.y());
        if (!stage.isShowing()) {
            stage.show();
        }
        stage.toFront();
        stage.requestFocus();
        LOG.debug("托盘菜单定位: edge={}, cursor=({}, {}), menu={}x{}, pos=({}, {})",
                placement.edge(), screenX, screenY, width, height, placement.x(), placement.y());
    }

    private static Stage menuStage() {
        if (menuStage != null) {
            return menuStage;
        }
        VBox root = new VBox();
        root.getStyleClass().add("tray-menu-root");
        root.setPrefWidth(184);

        Label playPause = menuItem("", () -> VlcPlayer.getInstance().playOrPause());
        playPause.textProperty().bind(Bindings.when(VlcPlayer.getInstance().playingProperty())
                .then("暂停").otherwise("播放"));

        Label previous = menuItem("上一曲", () -> VlcPlayer.getInstance().pre());
        Label next = menuItem("下一曲", () -> VlcPlayer.getInstance().next());
        Label show = menuItem("显示主窗口", TrayManager::restoreWindow);
        Label exit = menuItem("退出", Main::exit, "tray-menu-item-danger");

        Region firstSeparator = menuSeparator();
        Region secondSeparator = menuSeparator();
        root.getChildren().addAll(playPause, previous, next, firstSeparator, show, secondSeparator, exit);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(CssLoader.getCss(MENU_CSS));
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                hideMenu();
            }
        });
        FontUtils.applyTo(root);

        Stage stage = new Stage(StageStyle.TRANSPARENT);
        stage.setScene(scene);
        stage.setAlwaysOnTop(true);
        stage.setResizable(false);
        stage.setTitle("ASMR Player");
        stage.focusedProperty().addListener((observable, wasFocused, focused) -> {
            if (!focused) {
                Platform.runLater(() -> {
                    if (stage.isShowing() && !stage.isFocused()) {
                        stage.hide();
                    }
                });
            }
        });
        menuStage = stage;
        return stage;
    }

    private static Label menuItem(String text, Runnable action) {
        return menuItem(text, action, null);
    }

    private static Label menuItem(String text, Runnable action, String extraStyleClass) {
        Label item = new Label(text);
        item.getStyleClass().add("tray-menu-item");
        if (extraStyleClass != null) {
            item.getStyleClass().add(extraStyleClass);
        }
        item.setAlignment(Pos.CENTER_LEFT);
        item.setMaxWidth(Double.MAX_VALUE);
        item.setOnMouseClicked(e -> {
            hideMenu();
            action.run();
        });
        return item;
    }

    private static Region menuSeparator() {
        Region separator = new Region();
        separator.getStyleClass().add("tray-menu-separator");
        VBox.setMargin(separator, new Insets(4, 6, 4, 6));
        return separator;
    }

    private static void restoreWindow() {
        Stage window = Main.window;
        if (window == null) {
            return;
        }
        window.setIconified(false);
        window.show();
        window.toFront();
        window.requestFocus();
    }

    private static void hideMenu() {
        if (menuStage != null && menuStage.isShowing()) {
            menuStage.hide();
        }
    }

    /**
     * 找到 AWT 点击点所在的显示器，并给出该显示器在 AWT / JavaFX 两套坐标系下的边界。
     */
    private static ScreenInfo screenAt(int screenX, int screenY) {
        GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] devices = environment.getScreenDevices();
        if (devices.length == 0) {
            Screen primary = Screen.getPrimary();
            return new ScreenInfo(new Rectangle2D(0, 0, 1, 1), primary.getBounds(),
                    primary.getVisualBounds(), new TrayMenuGeometry.ScreenInsets(0, 0, 0, 0));
        }

        int index = -1;
        for (int i = 0; i < devices.length; i++) {
            if (devices[i].getDefaultConfiguration().getBounds().contains(screenX, screenY)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            // 坐标不在任何显示器内（边界/异常情况），回退主显示器。
            index = 0;
            for (int i = 0; i < devices.length; i++) {
                if (devices[i] == environment.getDefaultScreenDevice()) {
                    index = i;
                    break;
                }
            }
        }

        GraphicsDevice device = devices[index];
        GraphicsConfiguration configuration = device.getDefaultConfiguration();
        Rectangle awtBounds = configuration.getBounds();
        java.awt.Insets awtInsets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        Screen fxScreen = mapToFxScreen(device, index);
        return new ScreenInfo(
                new Rectangle2D(awtBounds.x, awtBounds.y, awtBounds.width, awtBounds.height),
                fxScreen.getBounds(),
                fxScreen.getVisualBounds(),
                new TrayMenuGeometry.ScreenInsets(awtInsets.top, awtInsets.right,
                        awtInsets.bottom, awtInsets.left));
    }

    /**
     * AWT 与 JavaFX 的显示器枚举顺序在 Windows 上一致，按索引对应即可；
     * 主显示器直接使用 JavaFX 的 primary，避免两边主屏顺序不同导致匹配错误。
     */
    private static Screen mapToFxScreen(GraphicsDevice device, int index) {
        if (device == GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()) {
            return Screen.getPrimary();
        }
        List<Screen> screens = Screen.getScreens();
        if (index < screens.size()) {
            return screens.get(index);
        }
        LOG.debug("AWT 显示器索引 {} 超出 JavaFX 屏幕数量 {}，回退主屏", index, screens.size());
        return Screen.getPrimary();
    }

    /**
     * 按主屏缩放比生成托盘图标。Windows 上 {@code getTrayIconSize()} 固定返回 16×16，
     * 而 AWT 在绘制时会再按系统缩放放大，因此这里直接提供 16×scale 的像素图。
     */
    private static Image loadTrayImage() throws IOException {
        URL url = TrayManager.class.getResource(ICON_RESOURCE);
        if (url == null) {
            return null;
        }
        try (InputStream in = url.openStream()) {
            BufferedImage source = ImageIO.read(in);
            if (source == null) {
                return null;
            }
            int target = trayIconPixelSize();
            if (source.getWidth() == target && source.getHeight() == target) {
                return source;
            }
            BufferedImage scaled = new BufferedImage(target, target, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = scaled.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.drawImage(source, 0, 0, target, target, null);
            } finally {
                graphics.dispose();
            }
            LOG.debug("托盘图标缩放: {}x{} -> {}x{}", source.getWidth(), source.getHeight(), target, target);
            return scaled;
        }
    }

    private static int trayIconPixelSize() {
        int base = 16;
        Dimension size = SystemTray.getSystemTray().getTrayIconSize();
        if (size != null && size.width > 0) {
            base = size.width;
        }
        double scale = 1.0;
        try {
            GraphicsConfiguration configuration = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration();
            scale = configuration.getDefaultTransform().getScaleX();
        } catch (RuntimeException e) {
            LOG.debug("读取系统缩放失败，按 100% 处理", e);
        }
        if (!(scale > 0)) {
            scale = 1.0;
        }
        return Math.max(base, (int) Math.round(base * scale));
    }

    private record ScreenInfo(Rectangle2D awtBounds,
                              Rectangle2D fxBounds,
                              Rectangle2D fxVisual,
                              TrayMenuGeometry.ScreenInsets insets) {
    }
}
