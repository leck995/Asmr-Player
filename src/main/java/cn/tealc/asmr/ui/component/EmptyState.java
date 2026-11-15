package cn.tealc.asmr.ui.component;

import cn.tealc.asmr.utils.CssLoader;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * 通用空状态提示：图标 + 标题 + 说明，可选操作槽位。
 * <p>
 * 只负责展示，不判断数据是否为空；可见性由宿主绑定。标题、说明、图标或操作槽位为空时
 * 会自动隐藏并取消占位，因此也可以只显示标题。
 *
 * @author Leck
 */
public final class EmptyState extends VBox {

    private final StackPane graphicBox = new StackPane();
    private final Label titleLabel = new Label();
    private final Label descriptionLabel = new Label();
    private final HBox actionBox = new HBox();

    private final ObjectProperty<Node> graphic = new SimpleObjectProperty<>();
    private final StringProperty title = new SimpleStringProperty("");
    private final StringProperty description = new SimpleStringProperty("");
    private final ObjectProperty<Node> action = new SimpleObjectProperty<>();
    private final BooleanProperty compact = new SimpleBooleanProperty(false);

    public EmptyState() {
        getStyleClass().add("empty-state");
        getStylesheets().add(CssLoader.getCommonCss("/control/empty-state.css"));
        setAlignment(Pos.CENTER);
        setMinSize(0, 0);

        graphicBox.getStyleClass().add("empty-state-graphic");
        graphicBox.setAlignment(Pos.CENTER);
        graphicBox.visibleProperty().bind(Bindings.createBooleanBinding(() -> graphic.get() != null, graphic));
        graphicBox.managedProperty().bind(graphicBox.visibleProperty());
        graphic.addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                graphicBox.getChildren().clear();
            } else {
                graphicBox.getChildren().setAll(newValue);
            }
        });

        titleLabel.getStyleClass().add("empty-state-title");
        titleLabel.textProperty().bind(title);
        titleLabel.setAlignment(Pos.CENTER);
        titleLabel.setTextAlignment(TextAlignment.CENTER);
        titleLabel.setWrapText(true);
        titleLabel.setMinWidth(0);
        titleLabel.setMaxWidth(420);
        titleLabel.visibleProperty().bind(title.isNotEmpty());
        titleLabel.managedProperty().bind(titleLabel.visibleProperty());

        descriptionLabel.getStyleClass().add("empty-state-description");
        descriptionLabel.textProperty().bind(description);
        descriptionLabel.setAlignment(Pos.CENTER);
        descriptionLabel.setTextAlignment(TextAlignment.CENTER);
        descriptionLabel.setWrapText(true);
        descriptionLabel.setMinWidth(0);
        descriptionLabel.setMaxWidth(420);
        descriptionLabel.visibleProperty().bind(description.isNotEmpty());
        descriptionLabel.managedProperty().bind(descriptionLabel.visibleProperty());

        actionBox.getStyleClass().add("empty-state-action");
        actionBox.setAlignment(Pos.CENTER);
        actionBox.visibleProperty().bind(Bindings.createBooleanBinding(() -> action.get() != null, action));
        actionBox.managedProperty().bind(actionBox.visibleProperty());
        action.addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                actionBox.getChildren().clear();
            } else {
                actionBox.getChildren().setAll(newValue);
            }
        });

        compact.addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                if (!getStyleClass().contains("compact")) {
                    getStyleClass().add("compact");
                }
            } else {
                getStyleClass().remove("compact");
            }
        });

        getChildren().addAll(graphicBox, titleLabel, descriptionLabel, actionBox);
    }

    public static EmptyState of(Ikon icon, String title, String description) {
        EmptyState emptyState = new EmptyState();
        emptyState.setIcon(icon);
        emptyState.setTitle(title);
        emptyState.setDescription(description);
        return emptyState;
    }

    public Node getGraphic() {
        return graphic.get();
    }

    public void setGraphic(Node value) {
        graphic.set(value);
    }

    public ObjectProperty<Node> graphicProperty() {
        return graphic;
    }

    public void setIcon(Ikon icon) {
        setGraphic(icon == null ? null : new FontIcon(icon));
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

    public String getDescription() {
        return description.get();
    }

    public void setDescription(String value) {
        description.set(value == null ? "" : value);
    }

    public StringProperty descriptionProperty() {
        return description;
    }

    public Node getAction() {
        return action.get();
    }

    public void setAction(Node value) {
        action.set(value);
    }

    public ObjectProperty<Node> actionProperty() {
        return action;
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
}
