package cn.tealc.asmr.ui.tools;

import cn.tealc.asmr.Main;
import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.mvvm.InjectViewModel;
import cn.tealc.asmr.mvvm.JavaView;
import cn.tealc.asmr.ui.component.PageHeader;
import cn.tealc.asmr.ui.dialog.Alerts;
import cn.tealc.asmr.ui.dialog.DialogAction;
import cn.tealc.asmr.ui.dialog.DialogPage;
import atlantafx.base.theme.Styles;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;

/**
 * 字幕与音声文件的配对重命名窗口。
 * <p>
 * 左右两个列表按行序配对音声与字幕，可通过上移/下移/移除调整顺序，选择以哪一侧文件名为准后批量重命名。
 * 仅当两侧数量相等且非空时才允许执行；作为 {@link DialogPage} 由 {@code Dialogs} 展示。
 *
 * @author Leck
 */
public class SubtitleRenameView extends StackPane implements JavaView<SubtitleRenameViewModel>, Initializable, DialogPage {
    @InjectViewModel
    private SubtitleRenameViewModel viewModel;

    private final PageHeader header = new PageHeader();
    private BorderPane content;
    private List<DialogAction> actions;

    public SubtitleRenameView() {
        getStylesheets().add(Main.class.getResource("/cn/tealc/asmr/css/skin/asmr-lrc-rename.css").toExternalForm());
        getStyleClass().add("subtitle-rename-root");
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        VBox audioPane = createListPane(viewModel.getAudios(), "音声文件", "与右侧字幕按顺序配对",
                Material2OutlinedMZ.MUSIC_NOTE);
        VBox subtitlePane = createListPane(viewModel.getLrcs(), "字幕文件", "与左侧音声按顺序配对",
                Material2OutlinedAL.CLOSED_CAPTION);

        ToggleGroup nameTypeGroup = new ToggleGroup();
        RadioButton lrcRadioButton = new RadioButton("以字幕名称为准");
        lrcRadioButton.setUserData(true);
        RadioButton asmrRadioButton = new RadioButton("以音声名称为准");
        asmrRadioButton.setUserData(false);
        lrcRadioButton.setToggleGroup(nameTypeGroup);
        asmrRadioButton.setToggleGroup(nameTypeGroup);
        lrcRadioButton.selectedProperty().bindBidirectional(viewModel.subtitleNameFirstProperty());

        Label ruleHint = new Label("列表从上到下逐项配对，选择一侧文件名作为新名称。");
        ruleHint.setWrapText(true);
        ruleHint.getStyleClass().add("subtitle-rename-section-hint");
        VBox ruleOptions = new VBox(6,
                createOption(lrcRadioButton, "用字幕文件名重命名对应的音声文件"),
                createOption(asmrRadioButton, "用音声文件名重命名对应的字幕文件"));
        VBox ruleCard = new VBox(10, sectionTitle("命名规则"), ruleHint, ruleOptions);
        ruleCard.getStyleClass().add("subtitle-rename-sidebar-card");

        Label pairingStatus = new Label();
        pairingStatus.setWrapText(true);
        pairingStatus.textProperty().bind(Bindings.createStringBinding(() -> {
            if (viewModel.isLoading()) {
                return "正在读取目录文件…";
            }
            if (viewModel.isBusy()) {
                return "正在执行重命名，请稍候…";
            }
            int audioCount = viewModel.getAudios().size();
            int subtitleCount = viewModel.getLrcs().size();
            if (audioCount > 0 && audioCount == subtitleCount) {
                return "已就绪：将按当前顺序配对 " + audioCount + " 组文件。";
            }
            if (audioCount == 0 && subtitleCount == 0) {
                return "无法重命名：当前目录中未找到音声或字幕文件。";
            }
            return "无法重命名：音声 " + audioCount + " 个，字幕 " + subtitleCount + " 个。";
        }, viewModel.getAudios(), viewModel.getLrcs(), viewModel.loadingProperty(), viewModel.busyProperty()));
        FontIcon statusIcon = new FontIcon();
        HBox statusBox = new HBox(8, statusIcon, pairingStatus);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        statusBox.getStyleClass().add("subtitle-rename-status");

        BooleanBinding pairingMatches = Bindings.size(viewModel.getAudios())
                .isEqualTo(Bindings.size(viewModel.getLrcs()))
                .and(Bindings.size(viewModel.getAudios()).greaterThan(0));
        Runnable refreshStatus = () -> updatePairingStatus(statusBox, statusIcon, pairingMatches.get(),
                viewModel.isLoading() || viewModel.isBusy());
        pairingMatches.addListener((observable, oldValue, matched) -> refreshStatus.run());
        viewModel.loadingProperty().addListener((observable, oldValue, loading) -> refreshStatus.run());
        viewModel.busyProperty().addListener((observable, oldValue, busy) -> refreshStatus.run());
        refreshStatus.run();

        Button renameBtn = new Button("执行重命名", new FontIcon(Material2OutlinedAL.EDIT));
        renameBtn.getStyleClass().addAll(Styles.ACCENT, "subtitle-rename-action");
        renameBtn.setMaxWidth(Double.MAX_VALUE);
        renameBtn.setPrefHeight(40);
        renameBtn.disableProperty().bind(pairingMatches.not()
                .or(viewModel.loadingProperty())
                .or(viewModel.busyProperty()));
        renameBtn.setOnAction(actionEvent -> viewModel.rename());

        VBox helpCard = new VBox(8,
                sectionTitle("使用说明"),
                helpLine("仅处理当前目录中的音声与字幕文件。"),
                helpLine("可通过上移、下移调整同一行的配对顺序。"),
                helpLine("移除只从本次配对列表中移除，不会删除硬盘文件。"));
        helpCard.getStyleClass().add("subtitle-rename-sidebar-card");

        Region sidebarSpacer = new Region();
        VBox sidebar = new VBox(12, ruleCard, statusBox, renameBtn, sidebarSpacer, helpCard);
        sidebar.getStyleClass().add("subtitle-rename-sidebar");
        VBox.setVgrow(sidebarSpacer, Priority.ALWAYS);

        HBox fileLists = new HBox(16, audioPane, subtitlePane);
        fileLists.getStyleClass().add("subtitle-rename-file-lists");
        HBox.setHgrow(audioPane, Priority.ALWAYS);
        HBox.setHgrow(subtitlePane, Priority.ALWAYS);

        header.setIcon(Material2OutlinedAL.CLOSED_CAPTION);
        header.setTitle("字幕文件配对重命名");
        header.setSubtitle("调整音声与字幕的对应顺序后，再批量统一名称。");

        content = new BorderPane();
        content.setCenter(fileLists);
        content.setRight(sidebar);
        BorderPane.setMargin(fileLists, new Insets(0, 16, 0, 0));
        content.getStyleClass().add("subtitle-rename-content");
        content.setPrefSize(960, 520);
        content.setMinSize(860, 450);
        getChildren().add(content);

        actions = List.of(DialogAction.cancel("关闭"));
    }

    @Override
    public PageHeader header() {
        return header;
    }

    @Override
    public Node content() {
        return this;
    }

    @Override
    public List<DialogAction> actions() {
        return actions;
    }

    @Override
    public double width() {
        return 1000;
    }

    /**
     * 构建一列文件清单：标题、行内单元格、计数与上移/下移/移除按钮。
     * <p>
     * 上移位于首行时回绕到末行、下移位于末行时回绕到首行，以维持循环的配对顺序。
     *
     * @param list    该列绑定的可观察文件列表（音声或字幕）
     * @param title   列标题
     * @param caption 标题下方的说明文字
     * @param icon    列标题图标
     * @return 组合好的列容器
     */
    private VBox createListPane(ObservableList<AsmrFile> list, String title, String caption, Ikon icon) {
        ListView<AsmrFile> listView = new ListView<>();
        listView.setItems(list);
        listView.setCellFactory(RenameFileCell::new);
        listView.setFixedCellSize(38);
        listView.getStyleClass().add("subtitle-rename-list");
        if (!list.isEmpty()) {
            listView.getSelectionModel().select(0);
        }

        FontIcon listIcon = new FontIcon(icon);
        listIcon.getStyleClass().add("subtitle-rename-list-icon");
        StackPane iconBox = new StackPane(listIcon);
        iconBox.getStyleClass().add("subtitle-rename-list-icon-box");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("subtitle-rename-list-title");
        Label captionLabel = new Label(caption);
        captionLabel.getStyleClass().add("subtitle-rename-list-caption");
        VBox titles = new VBox(2, titleLabel, captionLabel);

        Label countLabel = new Label();
        countLabel.textProperty().bind(Bindings.size(list).asString("%d 项"));
        countLabel.getStyleClass().add("subtitle-rename-count");

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox listHeader = new HBox(10, iconBox, titles, headerSpacer, countLabel);
        listHeader.setAlignment(Pos.CENTER_LEFT);
        listHeader.getStyleClass().add("subtitle-rename-list-header");

        Button upBtn = createListActionButton("上移", Material2OutlinedAL.ARROW_UPWARD);
        upBtn.setOnAction(actionEvent -> {
            int current = listView.getSelectionModel().getSelectedIndex();
            if (current < 0) {
                showSelectHint();
            } else if (current == 0) {
                AsmrFile temp = list.remove(current);
                list.add(temp);
                listView.getSelectionModel().select(list.size() - 1);
            } else {
                Collections.swap(list, current - 1, current);
                listView.getSelectionModel().select(current - 1);
            }
        });

        Button downBtn = createListActionButton("下移", Material2OutlinedAL.ARROW_DOWNWARD);
        downBtn.setOnAction(actionEvent -> {
            int current = listView.getSelectionModel().getSelectedIndex();
            if (current < 0) {
                showSelectHint();
            } else if (current == list.size() - 1) {
                AsmrFile temp = list.remove(current);
                list.add(0, temp);
                listView.getSelectionModel().select(0);
            } else {
                Collections.swap(list, current + 1, current);
                listView.getSelectionModel().select(current + 1);
            }
        });

        Button deleteBtn = createListActionButton("移除", Material2OutlinedAL.DELETE_OUTLINE);
        deleteBtn.getStyleClass().add(Styles.DANGER);
        deleteBtn.setOnAction(actionEvent -> {
            AsmrFile selectedItem = listView.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                list.remove(selectedItem);
            } else {
                showSelectHint();
            }
        });

        HBox listActions = new HBox(8, upBtn, downBtn, deleteBtn);
        listActions.setAlignment(Pos.CENTER_RIGHT);
        listActions.getStyleClass().add("subtitle-rename-list-actions");
        BooleanBinding listLocked = viewModel.loadingProperty().or(viewModel.busyProperty());
        upBtn.disableProperty().bind(listLocked);
        downBtn.disableProperty().bind(listLocked);
        deleteBtn.disableProperty().bind(listLocked);

        VBox listPane = new VBox(12, listHeader, listView, listActions);
        listPane.getStyleClass().add("subtitle-rename-list-pane");
        VBox.setVgrow(listView, Priority.ALWAYS);
        return listPane;
    }

    private VBox createOption(RadioButton radioButton, String hint) {
        Label hintLabel = new Label(hint);
        hintLabel.setWrapText(true);
        hintLabel.getStyleClass().add("subtitle-rename-option-hint");
        VBox option = new VBox(2, radioButton, hintLabel);
        option.getStyleClass().add("subtitle-rename-option");
        VBox.setMargin(hintLabel, new Insets(0, 0, 0, 24));
        option.setOnMouseClicked(event -> radioButton.setSelected(true));
        return option;
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("subtitle-rename-section-title");
        return label;
    }

    private Label helpLine(String text) {
        Label label = new Label("• " + text);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.getStyleClass().add("subtitle-rename-help-line");
        return label;
    }

    private Button createListActionButton(String text, Ikon icon) {
        Button button = new Button(text, new FontIcon(icon));
        button.getStyleClass().add("subtitle-rename-list-action");
        return button;
    }

    private void showSelectHint() {
        Alerts.warning().title("提示").message("请先选择一个文件").cancel("确定").show();
    }

    private void updatePairingStatus(HBox statusBox, FontIcon statusIcon, boolean matched, boolean pending) {
        statusBox.getStyleClass().removeAll(Styles.SUCCESS, Styles.DANGER, Styles.WARNING);
        if (pending) {
            statusBox.getStyleClass().add(Styles.WARNING);
            statusIcon.setIconCode(Material2OutlinedAL.HOURGLASS_EMPTY);
            return;
        }
        statusBox.getStyleClass().add(matched ? Styles.SUCCESS : Styles.DANGER);
        statusIcon.setIconCode(matched ? Material2OutlinedAL.CHECK_CIRCLE : Material2OutlinedAL.ERROR_OUTLINE);
    }

    /**
     * 行内展示配对序号与文件名；文件名过长时省略并保留完整提示，避免出现横向滚动条。
     */
    private static final class RenameFileCell extends ListCell<AsmrFile> {
        private final Label indexLabel = new Label();
        private final Label nameLabel = new Label();
        private final HBox row;

        private RenameFileCell(ListView<AsmrFile> listView) {
            getStyleClass().add("subtitle-rename-cell");
            indexLabel.getStyleClass().add("subtitle-rename-cell-index");
            nameLabel.getStyleClass().add("subtitle-rename-cell-name");
            nameLabel.setWrapText(false);
            nameLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
            nameLabel.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(nameLabel, Priority.ALWAYS);
            row = new HBox(10, indexLabel, nameLabel);
            row.setAlignment(Pos.CENTER_LEFT);
            prefWidthProperty().bind(listView.widthProperty().subtract(24));
            setText(null);
        }

        @Override
        protected void updateItem(AsmrFile file, boolean empty) {
            super.updateItem(file, empty);
            if (empty || file == null) {
                setGraphic(null);
                setTooltip(null);
                return;
            }
            indexLabel.setText(formatIndex(getIndex()));
            nameLabel.setText(file.getFilename());
            setGraphic(row);
            setTooltip(new Tooltip(file.getFilename()));
        }

        @Override
        public void updateIndex(int index) {
            super.updateIndex(index);
            indexLabel.setText(formatIndex(index));
        }

        private static String formatIndex(int index) {
            return index >= 0 ? String.format("%02d", index + 1) : "";
        }
    }
}
