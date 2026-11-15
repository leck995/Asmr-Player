package cn.tealc.asmr.ui.viewer;

import cn.tealc.asmr.model.SupportedImageFormat;
import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.source.MediaSource;
import cn.tealc.asmr.source.MediaSourceFactory;
import cn.tealc.asmr.ui.dialog.legacy.NewStage;
import cn.tealc.asmr.utils.CssLoader;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 图片查看器窗口，用于在作品详情页中查看指定图片文件。
 * <p>
 * 继承 {@link NewStage}，以非模态独立窗口弹出，使用 StackPane 作为图片容器（无 ScrollPane）。
 * 支持：本地与网络图片显示、左右切换、滚轮/按钮缩放、鼠标拖动平移、自适应窗口。
 * </p>
 *
 * @author Leck
 */
public class ImageViewerView extends NewStage {

    private static final Logger logger = LoggerFactory.getLogger(ImageViewerView.class);

    private static final double MIN_SCALE = 0.1;
    private static final double MAX_SCALE = 10.0;
    private static final double BUTTON_SCALE_STEP = 0.1;
    private static final double WHEEL_SCALE_STEP = 0.05;

    private final MediaSource source;
    private final String imageDir;

    private final ObservableList<AsmrFile> imageFiles = FXCollections.observableArrayList();
    private int currentIndex = -1;

    /** 图片显示容器（StackPane 居中，无 ScrollPane） */
    private final StackPane imageHolder = new StackPane();
    private final ImageView imageView = new ImageView();
    private final ProgressIndicator loadingIndicator = new ProgressIndicator();

    /** 当前缩放比例（1.0 = 适配窗口） */
    private final DoubleProperty scale = new SimpleDoubleProperty(1.0);

    /** 拖动平移状态 */
    private double dragStartX, dragStartY;
    private double dragStartTranslateX, dragStartTranslateY;

    public ImageViewerView(AsmrFile imageFile, AsmrWork work) {
        super();
        setTitle("图片查看");
        this.source = MediaSourceFactory.forWork(work);
        this.imageDir = parentDir(imageFile.getFilepath());

        setWidth(1000);
        setHeight(700);
        getScene().getStylesheets().add(CssLoader.getCommonCss(CssLoader.imageViewer));

        initToolbar();
        initContent();
        loadFileList(imageFile);

        getScene().setOnKeyPressed(this::handleKeyPressed);
    }

    // ==================== UI 初始化 ====================

    private void initToolbar() {
        Button zoomOutBtn = new Button(null, new FontIcon(Material2OutlinedMZ.MINUS));
        zoomOutBtn.setOnAction(e -> zoomBy(-BUTTON_SCALE_STEP));

        Button zoomInBtn = new Button(null, new FontIcon(Material2OutlinedAL.ADD));
        zoomInBtn.setOnAction(e -> zoomBy(BUTTON_SCALE_STEP));

        Button fitBtn = new Button(null, new FontIcon(Material2OutlinedAL.IMAGE));
        fitBtn.setOnAction(e -> resetZoom());

        Label scaleLabel = new Label();
        scaleLabel.getStyleClass().add("image-viewer-scale-label");
        scaleLabel.textProperty().bind(scale.multiply(100).asString("%.0f%%"));

        HBox toolbar = new HBox(4, zoomOutBtn, zoomInBtn, fitBtn, scaleLabel);
        toolbar.getStyleClass().add("image-viewer-toolbar");

        setHeaderRightGraphic(toolbar);
    }

    /**
     * 初始化内容区：StackPane（图片居中） + 左右切换按钮 + 加载指示器。
     * 不使用 ScrollPane，通过 ImageView 的 fitWidth/fitHeight 控制大小，
     * 通过 translateX/translateY 实现拖动平移。
     */
    private void initContent() {
        imageView.getStyleClass().add("image-viewer-image");
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        // 解除布局约束：图片不参与 StackPane 的尺寸计算，完全由 translateX/translateY 定位
        imageView.setManaged(false);
        imageView.setMouseTransparent(false);

        imageHolder.getStyleClass().add("image-viewer-holder");
        imageHolder.getChildren().add(imageView);

        // 窗口大小变化时重新适配
        imageHolder.widthProperty().addListener((obs, o, n) -> applyScale());
        imageHolder.heightProperty().addListener((obs, o, n) -> applyScale());

        // 鼠标拖动平移
        imageHolder.setOnMousePressed(this::onDragStart);
        imageHolder.setOnMouseDragged(this::onDrag);

        // 滚轮缩放
        imageHolder.setOnScroll(this::onScroll);

        loadingIndicator.setVisible(false);
        loadingIndicator.setPrefSize(50, 50);
        loadingIndicator.getStyleClass().add("image-viewer-loading");

        Button leftBtn = createNavButton("‹", -1);
        Button rightBtn = createNavButton("›", 1);
        StackPane.setAlignment(leftBtn, Pos.CENTER_LEFT);
        StackPane.setAlignment(rightBtn, Pos.CENTER_RIGHT);

        StackPane content = new StackPane(imageHolder, leftBtn, rightBtn, loadingIndicator);
        content.getStyleClass().add("image-viewer-content");
        content.setClip(new javafx.scene.shape.Rectangle());
        // 监听容器大小更新裁剪区域，防止图片溢出到 HeaderBar
        content.widthProperty().addListener((obs, o, n) ->
                ((javafx.scene.shape.Rectangle) content.getClip()).setWidth(n.doubleValue()));
        content.heightProperty().addListener((obs, o, n) ->
                ((javafx.scene.shape.Rectangle) content.getClip()).setHeight(n.doubleValue()));
        VBox.setVgrow(content, Priority.ALWAYS);
        setContent(content);
    }

    private Button createNavButton(String text, int delta) {
        Button btn = new Button(text);
        btn.getStyleClass().add("image-viewer-nav-btn");
        btn.setStyle("-fx-font-size: 24px; -fx-text-fill: white;");
        btn.setOnAction(e -> navigate(delta));
        return btn;
    }

    // ==================== 数据加载 ====================

    private void loadFileList(AsmrFile currentFile) {
        Thread.startVirtualThread(() -> {
            try {
                List<AsmrFile> allFiles = source.listFiles(imageDir);
                List<AsmrFile> images = allFiles.stream()
                        .filter(f -> !f.isDir())
                        .filter(f -> SupportedImageFormat.contains(f.getType()))
                        .collect(Collectors.toList());
                logger.info("同目录找到 {} 个图片文件", images.size());
                Platform.runLater(() -> {
                    imageFiles.setAll(images);
                    currentIndex = images.indexOf(currentFile);
                    if (currentIndex < 0 && !images.isEmpty()) currentIndex = 0;
                    if (currentIndex >= 0) loadImage(currentIndex);
                });
            } catch (Exception e) {
                logger.error("加载同目录图片文件列表失败", e);
            }
        });
    }

    private void loadImage(int index) {
        if (index < 0 || index >= imageFiles.size()) return;
        currentIndex = index;
        AsmrFile file = imageFiles.get(index);

        loadingIndicator.setVisible(true);
        imageView.setImage(null);
        resetZoom();

        Thread.startVirtualThread(() -> {
            try {
                logger.info("加载图片: {}", file.getFilename());
                byte[] bytes = source.readBinaryFile(file.getFilepath());
                if (bytes != null && bytes.length > 0) {
                    logger.info("图片下载成功: {} bytes", bytes.length);
                    Platform.runLater(() -> {
                        imageView.setImage(new Image(new java.io.ByteArrayInputStream(bytes)));
                        applyScale();
                        loadingIndicator.setVisible(false);
                    });
                } else {
                    logger.error("图片下载失败: {}", file.getFilepath());
                    Platform.runLater(() -> loadingIndicator.setVisible(false));
                }
            } catch (Exception e) {
                logger.error("加载图片失败: {}", file.getFilepath(), e);
                Platform.runLater(() -> loadingIndicator.setVisible(false));
            }
        });
    }

    // ==================== 缩放与导航 ====================

    private void zoomBy(double delta) {
        double newScale = scale.get() + delta;
        newScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScale));
        scale.set(newScale);
        applyScale();
    }

    /**
     * 重置缩放为适配窗口大小（100%），同时重置平移位置。
     */
    private void resetZoom() {
        scale.set(1.0);
        imageView.setTranslateX(0);
        imageView.setTranslateY(0);
        applyScale();
    }

    /**
     * 根据 scale 值和容器大小，设置 ImageView 的 fitWidth/fitHeight，并居中定位。
     * scale=1.0 时图片完整适配容器，scale>1.0 放大，scale<1.0 缩小。
     * 因为 imageView.setManaged(false)，需要手动计算居中坐标。
     */
    private void applyScale() {
        Image img = imageView.getImage();
        if (img == null) return;
        double imgW = img.getWidth();
        double imgH = img.getHeight();
        if (imgW <= 0 || imgH <= 0) return;

        double vpW = imageHolder.getWidth();
        double vpH = imageHolder.getHeight();
        if (vpW <= 0 || vpH <= 0) return;

        // 适配窗口的基准大小
        double ratio = Math.min(vpW / imgW, vpH / imgH);
        double baseW = imgW * ratio;
        double baseH = imgH * ratio;

        // 乘以缩放比例
        double fitW = baseW * scale.get();
        double fitH = baseH * scale.get();
        imageView.setFitWidth(fitW);
        imageView.setFitHeight(fitH);

        // 手动居中定位（managed=false 不参与布局）
        imageView.setLayoutX((vpW - fitW) / 2);
        imageView.setLayoutY((vpH - fitH) / 2);
    }

    private void onScroll(ScrollEvent event) {
        if (event.getDeltaY() == 0) return;
        double delta = event.getDeltaY() > 0 ? WHEEL_SCALE_STEP : -WHEEL_SCALE_STEP;
        zoomBy(delta);
        event.consume();
    }

    private void navigate(int delta) {
        int newIndex = currentIndex + delta;
        if (newIndex < 0) newIndex = imageFiles.size() - 1;
        if (newIndex >= imageFiles.size()) newIndex = 0;
        loadImage(newIndex);
    }

    private void handleKeyPressed(KeyEvent event) {
        switch (event.getCode()) {
            case LEFT -> navigate(-1);
            case RIGHT -> navigate(1);
            default -> { }
        }
    }

    // ==================== 拖动平移 ====================

    /**
     * 记录拖动起始位置和 ImageView 当前平移量。
     */
    private void onDragStart(MouseEvent event) {
        dragStartX = event.getScreenX();
        dragStartY = event.getScreenY();
        dragStartTranslateX = imageView.getTranslateX();
        dragStartTranslateY = imageView.getTranslateY();
    }

    /**
     * 拖动时更新 ImageView 的 translateX/translateY，实现平移。
     */
    private void onDrag(MouseEvent event) {
        double deltaX = event.getScreenX() - dragStartX;
        double deltaY = event.getScreenY() - dragStartY;
        imageView.setTranslateX(dragStartTranslateX + deltaX);
        imageView.setTranslateY(dragStartTranslateY + deltaY);
    }

    // ==================== 工具方法 ====================

    private static String parentDir(String filePath) {
        int slash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        return slash == -1 ? filePath : filePath.substring(0, slash);
    }
}
