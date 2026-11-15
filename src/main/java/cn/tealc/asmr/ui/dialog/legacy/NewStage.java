package cn.tealc.asmr.ui.dialog.legacy;

import cn.tealc.asmr.utils.CssLoader;
import cn.tealc.asmr.utils.FontUtils;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HeaderBar;
import javafx.scene.layout.HeaderButtonType;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

/**
 * 通用 Stage，与 {@link NewDialog} 形成对称的 New* 家族。
 * <p>
 * 提供 shadcn 风格 HeaderBar（最小化/最大化/关闭三按钮），{@code StageStyle.EXTENDED}
 * + 隐藏原生系统按钮区，CSS 加载（atlantafx 变量 + Dialog.css + 主题 CSS），字体应用。
 * 默认非模态、可拉伸调整窗口大小。
 * </p>
 *
 * @author Leck
 */
public class NewStage extends Stage {

    private final HeaderBar headerBar = new HeaderBar();
    private final Label titleLabel = new Label();
    private final HBox headerRight = new HBox();
    private HBox systemBox;

    private final BorderPane root = new BorderPane();
    private final Scene scene;

    /**
     * 默认构造：EXTENDED 样式，非模态，无 owner，可拉伸。
     */
    public NewStage() {
        this(null);
    }

    /**
     * 带 owner 构造，关联到父窗口。
     *
     * @param owner 父窗口 Stage，为 null 时不设 owner
     */
    public NewStage(Stage owner) {
        initStyle(StageStyle.EXTENDED);
        if (owner != null) {
            initOwner(owner);
        }
        initModality(Modality.NONE);

        buildHeaderBar();

        root.setTop(headerBar);
        scene = new Scene(root);
        setScene(scene);

        loadStylesheets();
        FontUtils.applyTo(root);

        // show 后隐藏 EXTENDED 原生系统按钮区，由自定义 HeaderBar 接管
        showingProperty().addListener((obs, wasShowing, isShowing) -> {
            if (isShowing) {
                HeaderBar.setPrefButtonHeight(this, 0);
            }
        });
    }

    // ==================== HeaderBar 构建 ====================

    /**
     * 构造 HeaderBar：左侧标题，右侧扩展区 + 最小化/最大化/关闭。
     */
    private void buildHeaderBar() {
        headerBar.getStyleClass().addAll("headbar", "shadcn-dialog-headerbar");

        titleLabel.getStyleClass().add("shadcn-dialog-title");
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);
        titleLabel.textProperty().bind(titleProperty());

        HBox left = new HBox(titleLabel);
        left.getStyleClass().add("leading");
        headerBar.setLeft(left);

        Button minBtn = new Button(null, new FontIcon(Material2OutlinedMZ.MINUS));
        Button maxBtn = new Button(null, new FontIcon());
        Button closeBtn = new Button(null, new FontIcon(Material2OutlinedAL.CLOSE));
        HeaderBar.setButtonType(minBtn, HeaderButtonType.ICONIFY);
        HeaderBar.setButtonType(maxBtn, HeaderButtonType.MAXIMIZE);
        closeBtn.getStyleClass().add("close-btn");
        maxBtn.getStyleClass().add("max-btn");
        closeBtn.setOnAction(e -> hide()); // 子窗口关闭只 hide 不 exit

        // 最大化状态切换图标
        maxBtn.getStyleClass().add("max-btn");
        maximizedProperty().addListener((obs, oldVal, isMax) -> {
            if (isMax) {
                maxBtn.getStyleClass().add("full-exit");
            } else {
                maxBtn.getStyleClass().remove("full-exit");
            }
        });

        systemBox = new HBox(minBtn, maxBtn, closeBtn);
        systemBox.getStyleClass().add("system-func");

        headerRight.getChildren().add(systemBox);
        headerRight.getStyleClass().add("trailing");
        headerBar.setRight(headerRight);

        headerBar.setLeftSystemPadding(false);
        headerBar.setRightSystemPadding(false);
    }

    // ==================== CSS 加载 ====================

    /**
     * 加载样式表：atlantafx（颜色变量）+ Dialog.css（shadcn 皮肤）+ 主题 CSS（.headbar 样式）。
     * 独立 Stage 不继承主窗口内联样式，必须显式加载 atlantafx 让 -color-* 变量可解析。
     */
    private void loadStylesheets() {
        scene.getStylesheets().add(CssLoader.getCss(CssLoader.atlantafx));
        scene.getStylesheets().add(
                NewStage.class.getResource("/cn/tealc/asmr/css/Dialog.css").toExternalForm());
        scene.getStylesheets().add(CssLoader.getCss(CssLoader.main));
    }

    // ==================== 公共 API ====================

    /**
     * 在 HeaderBar 右侧、系统按钮之前插入额外节点（工具栏等）。
     *
     * @param graphic 待插入的节点，为 null 时仅移除已有额外节点
     */
    public void setHeaderRightGraphic(Node graphic) {
        headerRight.getChildren().removeIf(n -> n != systemBox);
        if (graphic != null) {
            headerRight.getChildren().add(0, graphic);
        }
    }

    /**
     * 设置内容区。
     *
     * @param content 内容节点
     */
    public void setContent(Node content) {
        root.setCenter(content);
    }

    /**
     * 获取根布局。
     *
     * @return BorderPane 根节点
     */
    public BorderPane getRoot() {
        return root;
    }
}
