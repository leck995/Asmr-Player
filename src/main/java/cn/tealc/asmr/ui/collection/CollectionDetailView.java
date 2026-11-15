package cn.tealc.asmr.ui.collection;

import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.factory.ViewFactory;
import cn.tealc.asmr.model.collection.ItemDataEx;
import cn.tealc.asmr.model.collection.SheetData;
import cn.tealc.asmr.mvvm.ViewTuple;
import cn.tealc.asmr.service.CollectionService;
import cn.tealc.asmr.ui.component.EmptyState;
import cn.tealc.asmr.ui.component.OverlayLayer;
import cn.tealc.asmr.ui.component.PageHeader;
import cn.tealc.asmr.ui.dialog.Alerts;
import cn.tealc.asmr.ui.dialog.DialogAction;
import cn.tealc.asmr.ui.dialog.DialogSpec;
import cn.tealc.asmr.ui.dialog.Dialogs;
import cn.tealc.asmr.ui.work.WorkDetailView;
import cn.tealc.asmr.ui.work.WorkDetailViewModel;
import cn.tealc.asmr.ui.navigation.NavigationService;
import cn.tealc.asmr.ui.navigation.PlaylistsRoute;
import cn.tealc.asmr.utils.CssLoader;
import cn.tealc.asmr.utils.TimeFormatUtils;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

/**
 * 歌单详情页，承载歌单内媒体的管理与播放。
 */
public class CollectionDetailView extends BorderPane {
    private final CollectionService collectionService = DI.get(CollectionService.class);
    private final SheetData sheet;
    private final CollectionDetailViewModel viewModel;
    private final PageHeader pageHeader = new PageHeader();

    public CollectionDetailView(int sheetId) {
        this.sheet = collectionService.getSheetById(sheetId);
        this.viewModel = sheet == null ? null : new CollectionDetailViewModel(sheet);
        initialize();
    }

    /**
     * 重新显示页面时刷新条目，返回列表后由主路由负责刷新歌单卡片。
     */
    public void refresh() {
        if (viewModel == null) return;
        viewModel.refreshItems();
        refreshSummary();
    }

    private void initialize() {
        getStylesheets().add(CssLoader.getCss(CssLoader.collection));
        getStyleClass().add("collection-root");
        setMinSize(0, 0);
        setPadding(new Insets(20, 24, 20, 24));

        pageHeader.setIcon(Material2OutlinedMZ.PLAYLIST_PLAY);
        pageHeader.setTitle("歌单不存在");
        pageHeader.setSubtitle("");
        setTop(pageHeader);

        if (viewModel == null) {
            setCenter(createMissingState());
            return;
        }

        Button playAll = new Button("播放全部", new FontIcon(Material2OutlinedMZ.PLAY_CIRCLE_FILLED_WHITE));
        playAll.getStyleClass().add("accent");
        playAll.setOnAction(event -> viewModel.playAll());
        playAll.disableProperty().bind(Bindings.isEmpty(viewModel.getItemList()));

        MenuButton manage = new MenuButton("管理", new FontIcon(Material2OutlinedMZ.MORE_VERT));
        manage.getStyleClass().add("collection-manage-button");
        MenuItem edit = new MenuItem("编辑歌单", new FontIcon(Material2OutlinedAL.EDIT));
        edit.setOnAction(event -> editSheet());
        MenuItem delete = new MenuItem("删除歌单", new FontIcon(Material2OutlinedAL.DELETE_OUTLINE));
        delete.setOnAction(event -> deleteSheet());
        manage.getItems().addAll(edit, delete);

        HBox actions = new HBox(8, playAll, manage);
        actions.setAlignment(Pos.CENTER_RIGHT);
        pageHeader.setActions(actions);

        TableView<ItemDataEx> table = createTable();
        BorderPane.setMargin(table, new Insets(12, 0, 0, 0));
        setCenter(table);

        viewModel.getItemList().addListener((ListChangeListener<ItemDataEx>) change -> refreshSummary());
        refreshSummary();
    }

    private Node createMissingState() {
        Button back = new Button("返回歌单");
        // 有历史时退回来源页，直接从外部打开该页时回落到歌单列表
        back.setOnAction(event -> {
            NavigationService navigation = DI.get(NavigationService.class);
            if (navigation.canGoBackProperty().get()) {
                navigation.back();
            } else {
                navigation.openRoot(new PlaylistsRoute());
            }
        });
        EmptyState emptyState = EmptyState.of(Material2OutlinedMZ.PLAYLIST_PLAY,
                "歌单不存在或已被删除", "");
        emptyState.setAction(back);
        return emptyState;
    }

    private TableView<ItemDataEx> createTable() {
        TableView<ItemDataEx> table = new TableView<>(viewModel.getItemList());
        table.getStyleClass().add("item-table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ItemDataEx, Integer> indexCol = new TableColumn<>("序号");
        indexCol.setMaxWidth(60);
        indexCol.setPrefWidth(60);
        indexCol.setCellValueFactory(cellData -> new SimpleIntegerProperty(
                cellData.getTableView().getItems().indexOf(cellData.getValue()) + 1
        ).asObject());

        TableColumn<ItemDataEx, String> nameCol = new TableColumn<>("名称");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<ItemDataEx, String> vasCol = new TableColumn<>("声优");
        vasCol.setMaxWidth(400);
        vasCol.setPrefWidth(160);
        vasCol.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().getWork() == null ? "" : cellData.getValue().getWork().getVas()
        ));

        TableColumn<ItemDataEx, String> rjCol = new TableColumn<>("来源");
        rjCol.setMaxWidth(140);
        rjCol.setPrefWidth(120);
        rjCol.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().getWork() == null ? "" : cellData.getValue().getWork().getWorkId()
        ));

        table.getColumns().addAll(indexCol, nameCol, vasCol, rjCol);
        table.setPlaceholder(createEmptyState());
        table.setRowFactory(ignored -> createTableRow());
        return table;
    }

    private EmptyState createEmptyState() {
        return EmptyState.of(Material2OutlinedMZ.PLAYLIST_PLAY,
                "歌单暂时为空", "可在作品详情中将媒体添加到此歌单。");
    }

    private TableRow<ItemDataEx> createTableRow() {
        TableRow<ItemDataEx> row = new TableRow<>() {
            @Override
            protected void updateItem(ItemDataEx item, boolean empty) {
                super.updateItem(item, empty);
                setDisable(empty);
            }
        };
        row.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && !row.isEmpty()) {
                viewModel.playSelected(row.getIndex());
            }
        });
        row.setContextMenu(createItemContextMenu(row));
        return row;
    }

    private ContextMenu createItemContextMenu(TableRow<ItemDataEx> row) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem play = new MenuItem("播放", new FontIcon(Material2OutlinedMZ.PLAY_CIRCLE_FILLED_WHITE));
        play.setOnAction(event -> viewModel.playSelected(row.getIndex()));
        MenuItem add = new MenuItem("添加到播放列表", new FontIcon(Material2OutlinedMZ.PLAYLIST_ADD));
        add.setOnAction(event -> viewModel.addSelected(row.getIndex()));
        MenuItem detail = new MenuItem("查看作品详情", new FontIcon(Material2OutlinedAL.COMPUTER));
        detail.setOnAction(event -> {
            ItemDataEx item = row.getItem();
            if (item != null && item.getWork() != null) {
                ViewTuple<WorkDetailView, WorkDetailViewModel> tuple = ViewFactory.workDetailView(item.getWork());
                OverlayLayer.show((javafx.scene.layout.Pane) tuple.getView());
            }
        });
        MenuItem remove = new MenuItem("移除", new FontIcon(Material2OutlinedAL.DELETE_OUTLINE));
        remove.setOnAction(event -> {
            ItemDataEx item = row.getItem();
            if (item != null) {
                Alerts.danger().title("移除条目")
                        .message(String.format("确认从歌单中移除媒体 %s 吗？", item.getName()))
                        .ok("移除", ignored -> viewModel.deleteItem(row.getIndex()))
                        .cancel().show();
            }
        });
        contextMenu.getItems().addAll(play, add, detail, remove);
        return contextMenu;
    }

    private void editSheet() {
        SheetData sheet = viewModel.getSheet();
        TextField name = new TextField(sheet.getSheetName());
        name.setPromptText("歌单名称");
        TextArea description = new TextArea(sheet.getDescription() == null ? "" : sheet.getDescription());
        description.setPromptText("描述（可选）");
        description.setWrapText(true);
        description.setPrefRowCount(4);
        Label error = new Label();
        error.getStyleClass().add("collection-dialog-error");
        error.visibleProperty().bind(error.textProperty().isNotEmpty());
        error.managedProperty().bind(error.visibleProperty());
        VBox metadata = new VBox(3,
                new Label("创建时间：" + TimeFormatUtils.formatDateTime(sheet.getCreateDateTime())),
                new Label("修改时间：" + TimeFormatUtils.formatDateTime(sheet.getUpdateDateTime())));
        metadata.getStyleClass().add("collection-dialog-meta");
        VBox body = new VBox(8, new Label("名称"), name, new Label("描述"), description, metadata, error);
        body.setPrefWidth(440);

        DialogAction save = DialogAction.of("保存", "accent", handle -> {
            String trimmed = name.getText().trim();
            if (trimmed.isEmpty()) {
                error.setText("歌单名称不能为空");
                return;
            }
            if (viewModel.updateSheet(trimmed, description.getText().trim())) {
                refreshSummary();
                handle.close();
            } else {
                error.setText("保存失败，请重试");
            }
        }).defaultButton().disabledWhen(
                Bindings.createBooleanBinding(() -> name.getText().isBlank(), name.textProperty()));

        Dialogs.show(DialogSpec.builder()
                .title("编辑歌单")
                .content(body)
                .width(500)
                .stylesheet(CssLoader.getCss(CssLoader.collection))
                .action(save)
                .cancel()
                .build());
    }

    private void deleteSheet() {
        Alerts.danger().title("删除歌单")
                .message(String.format("确认删除歌单 %s 吗？", viewModel.getSheet().getSheetName()))
                .ok("删除", handle -> {
                    if (viewModel.deleteSheet()) {
                        DI.get(NavigationService.class).openRoot(new PlaylistsRoute());
                    }
                })
                .cancel().show();
    }

    private void refreshSummary() {
        SheetData current = viewModel.getSheet();
        String description = current.getDescription() == null ? "" : current.getDescription().trim();
        String subtitle = String.format("共 %d 个媒体", viewModel.getItemList().size());
        if (!description.isEmpty()) {
            subtitle += " · " + description;
        }
        pageHeader.setTitle(current.getSheetName());
        pageHeader.setSubtitle(subtitle);
    }
}
