package cn.tealc.asmr.ui.component;

import cn.tealc.asmr.utils.CssLoader;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * 页面级页头。
 * <p>
 * 左侧为来源图标、标题和说明，右侧为操作区域；可选在标题行下方追加额外内容。
 * 默认样式与歌单、分类、媒体库页面保持一致。
 *
 * @author Leck
 */
public final class PageHeader extends VBox {

    private final HBox row = new HBox();
    private final HBox leading = new HBox();
    private final VBox identity = new VBox();
    private final HBox titleRow = new HBox();
    private final StackPane iconBox = new StackPane();
    private final Label titleLabel = new Label();
    private final Label subtitleLabel = new Label();
    private final HBox subtitleRow = new HBox();
    private final HBox subtitleExtra = new HBox();
    private final HBox actions = new HBox();
    private final VBox extra = new VBox();
    private final Region separator = new Region();

    private final StringProperty title = new SimpleStringProperty("");
    private final StringProperty subtitle = new SimpleStringProperty("");
    private final ObjectProperty<Node> icon = new SimpleObjectProperty<>();
    private final BooleanProperty separatorVisible = new SimpleBooleanProperty(true);
    private final BooleanProperty subtitleVisible = new SimpleBooleanProperty(true);
    private final BooleanProperty iconVisible = new SimpleBooleanProperty(true);
    private final BooleanProperty compact = new SimpleBooleanProperty(false);

    public PageHeader() {
        getStyleClass().add("page-header");
        getStylesheets().add(CssLoader.getCommonCss("/control/page-header.css"));
        setSpacing(8);

        row.getStyleClass().add("page-header-row");
        row.setAlignment(Pos.TOP_LEFT);
        row.setFillHeight(false);

        leading.getStyleClass().add("page-header-leading");
        leading.setAlignment(Pos.CENTER_LEFT);
        bindVisibilityToChildren(leading);

        identity.getStyleClass().add("page-header-identity");
        identity.setAlignment(Pos.TOP_LEFT);
        identity.setSpacing(6);
        identity.setMinWidth(0);
        identity.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(identity, Priority.ALWAYS);

        titleRow.getStyleClass().add("page-header-title-row");
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.setSpacing(10);
        titleRow.setMinWidth(0);
        titleRow.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleRow, Priority.ALWAYS);

        iconBox.getStyleClass().add("page-header-icon-box");
        iconBox.setAlignment(Pos.CENTER);
        iconBox.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> iconVisible.get() && icon.get() != null, iconVisible, icon));
        iconBox.managedProperty().bind(iconBox.visibleProperty());
        icon.addListener((observable, oldIcon, newIcon) -> {
            if (newIcon == null) {
                iconBox.getChildren().clear();
            } else {
                iconBox.getChildren().setAll(newIcon);
            }
        });

        titleLabel.getStyleClass().add("page-header-title");
        titleLabel.textProperty().bind(title);
        titleLabel.setMinWidth(0);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        subtitleLabel.getStyleClass().add("page-header-subtitle");
        subtitleLabel.textProperty().bind(subtitle);
        subtitleLabel.setMinWidth(0);
        subtitleLabel.setMaxWidth(Double.MAX_VALUE);
        subtitleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        subtitleLabel.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> subtitleVisible.get() && !subtitle.get().isBlank(), subtitleVisible, subtitle));
        subtitleLabel.managedProperty().bind(subtitleLabel.visibleProperty());
        HBox.setHgrow(subtitleLabel, Priority.ALWAYS);

        subtitleExtra.getStyleClass().add("page-header-subtitle-extra");
        subtitleExtra.setAlignment(Pos.CENTER_LEFT);
        subtitleExtra.setSpacing(6);
        subtitleExtra.setMinWidth(0);
        subtitleExtra.setMaxWidth(Double.MAX_VALUE);
        subtitleExtra.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> subtitleVisible.get() && !subtitleExtra.getChildren().isEmpty(),
                subtitleVisible, subtitleExtra.getChildren()));
        subtitleExtra.managedProperty().bind(subtitleExtra.visibleProperty());
        subtitleExtra.getChildren().addListener((ListChangeListener<Node>) change -> {
            boolean present = !subtitleExtra.getChildren().isEmpty();
            subtitleLabel.setMinWidth(present ? Region.USE_PREF_SIZE : 0);
            subtitleLabel.setMaxWidth(present ? Region.USE_PREF_SIZE : Double.MAX_VALUE);
            HBox.setHgrow(subtitleLabel, present ? Priority.NEVER : Priority.ALWAYS);
            HBox.setHgrow(subtitleExtra, present ? Priority.ALWAYS : Priority.NEVER);
        });

        subtitleRow.getStyleClass().add("page-header-subtitle-row");
        subtitleRow.setAlignment(Pos.CENTER_LEFT);
        subtitleRow.setSpacing(6);
        subtitleRow.setMinWidth(0);
        subtitleRow.setMaxWidth(Double.MAX_VALUE);
        subtitleRow.getChildren().addAll(subtitleLabel, subtitleExtra);
        subtitleRow.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> subtitleVisible.get()
                        && (!subtitle.get().isBlank() || !subtitleExtra.getChildren().isEmpty()),
                subtitleVisible, subtitle, subtitleExtra.getChildren()));
        subtitleRow.managedProperty().bind(subtitleRow.visibleProperty());
        HBox.setHgrow(subtitleRow, Priority.ALWAYS);

        titleRow.getChildren().addAll(iconBox, titleLabel);
        identity.getChildren().addAll(titleRow, subtitleRow);

        actions.getStyleClass().add("page-header-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);
        bindVisibilityToChildren(actions);

        extra.getStyleClass().add("page-header-extra");
        extra.setSpacing(8);
        bindVisibilityToChildren(extra);

        separator.getStyleClass().add("page-header-separator");
        separator.setMaxWidth(Double.MAX_VALUE);
        separator.visibleProperty().bind(separatorVisible);
        separator.managedProperty().bind(separatorVisible);

        row.getChildren().addAll(leading, identity, actions);
        getChildren().addAll(row, extra, separator);

        compact.addListener((observable, oldValue, newValue) ->
                setStyleClass("compact", newValue));
        setStyleClass("compact", compact.get());
    }

    public String getTitle() {
        return title.get();
    }

    public void setTitle(String value) {
        title.set(value == null ? "" : value);
    }

    public StringProperty titleProperty() {
        return title;
    }

    public String getSubtitle() {
        return subtitle.get();
    }

    public void setSubtitle(String value) {
        subtitle.set(value == null ? "" : value);
    }

    public StringProperty subtitleProperty() {
        return subtitle;
    }

    /**
     * 在说明文字后方追加节点，用于展示与说明同行的补充信息。
     */
    public void setSubtitleExtra(Node node) {
        setSlot(subtitleExtra, node);
    }

    public HBox getSubtitleExtra() {
        return subtitleExtra;
    }

    public void setIcon(Ikon value) {
        setIconNode(value == null ? null : new FontIcon(value));
    }

    public void setIconNode(Node value) {
        icon.set(value);
    }

    public ObjectProperty<Node> iconProperty() {
        return icon;
    }

    public void setLeading(Node node) {
        setSlot(leading, node);
    }

    public HBox getLeading() {
        return leading;
    }

    public void setActions(Node node) {
        setSlot(actions, node);
    }

    public HBox getActions() {
        return actions;
    }

    public void setExtra(Node node) {
        setSlot(extra, node);
    }

    public VBox getExtra() {
        return extra;
    }

    public boolean isSeparatorVisible() {
        return separatorVisible.get();
    }

    public void setSeparatorVisible(boolean value) {
        separatorVisible.set(value);
    }

    public BooleanProperty separatorVisibleProperty() {
        return separatorVisible;
    }

    public boolean isSubtitleVisible() {
        return subtitleVisible.get();
    }

    public void setSubtitleVisible(boolean value) {
        subtitleVisible.set(value);
    }

    public BooleanProperty subtitleVisibleProperty() {
        return subtitleVisible;
    }

    /**
     * 只显示大标题，隐藏说明文字。
     */
    public void setTitleOnly(boolean value) {
        subtitleVisible.set(!value);
    }

    public boolean isIconVisible() {
        return iconVisible.get();
    }

    public void setIconVisible(boolean value) {
        iconVisible.set(value);
    }

    public BooleanProperty iconVisibleProperty() {
        return iconVisible;
    }

    public boolean isCompact() {
        return compact.get();
    }

    public void setCompact(boolean value) {
        compact.set(value);
    }

    public BooleanProperty compactProperty() {
        return compact;
    }

    private static void setSlot(javafx.scene.layout.Pane container, Node node) {
        if (node == null) {
            container.getChildren().clear();
        } else {
            container.getChildren().setAll(node);
        }
    }

    private static void bindVisibilityToChildren(HBox container) {
        container.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> !container.getChildren().isEmpty(), container.getChildren()));
        container.managedProperty().bind(container.visibleProperty());
    }

    private static void bindVisibilityToChildren(VBox container) {
        container.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> !container.getChildren().isEmpty(), container.getChildren()));
        container.managedProperty().bind(container.visibleProperty());
    }

    private void setStyleClass(String styleClass, boolean enabled) {
        if (enabled) {
            if (!getStyleClass().contains(styleClass)) {
                getStyleClass().add(styleClass);
            }
        } else {
            getStyleClass().remove(styleClass);
        }
    }
}
