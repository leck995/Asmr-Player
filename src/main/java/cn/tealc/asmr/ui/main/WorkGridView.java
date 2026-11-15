package cn.tealc.asmr.ui.main;

import cn.tealc.asmr.eventbus.EventBus;

import cn.tealc.asmr.model.AsmrKeyType;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.ui.TeaSkin;
import cn.tealc.asmr.ui.component.PageHeader;
import cn.tealc.asmr.ui.work.list.DefaultWorkCellContext;
import cn.tealc.asmr.ui.work.list.WorkCellFactory;
import cn.tealc.asmr.ui.work.list.WorkListOptions;
import cn.tealc.asmr.ui.work.list.WorkListView;
import cn.tealc.asmr.ui.navigation.WorkSource;
import cn.tealc.asmr.ui.dialog.Alerts;
import cn.tealc.asmr.utils.CssLoader;
import cn.tealc.asmr.mvvm.InjectViewModel;
import cn.tealc.asmr.mvvm.JavaView;
import cn.tealc.asmr.mvvm.NotificationObserver;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.fxml.Initializable;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;
import org.kordamp.ikonli.materialdesign2.MaterialDesignV;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * 作品列表页。
 * <p>
 * 页头由来源图标、标题、说明、导航与操作按钮组成，下方是通用 {@link WorkListView}。
 * 页面切换由全局导航服务负责。
 *
 * @author Leck
 */
public class WorkGridView extends StackPane implements JavaView<WorkGridViewModel>, Initializable, TeaSkin {
    @InjectViewModel
    private WorkGridViewModel viewModel;
    private WorkListView workList;
    private boolean renderedDetailMode;
    private final NotificationObserver skinObserver = (key, payload) -> applyStyleSheet();
    private final ChangeListener<Boolean> viewModeListener = (observable, oldValue, newValue) -> {
        renderedDetailMode = newValue;
        if (workList != null) {
            workList.rebuild();
        }
    };

    public WorkGridView() {
        getStyleClass().add("work-page-root");
        applyStyleSheet();
        sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null) {
                EventBus.unsubscribe("CHANGE_SKIN", skinObserver);
            } else {
                EventBus.subscribe("CHANGE_SKIN", skinObserver);
            }
        });
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        PageHeader header = createPageHeader();
        workList = new WorkListView(viewModel.getListModel(), WorkListOptions.grid(
                WorkCellFactory.adaptive(),
                DefaultWorkCellContext.INSTANCE,
                () -> Config.setting.gridItemDetailModel.get() ? 4.0 : 2.0));
        workList.getStyleClass().add("work-page-list");
        // 页头放在滚动区域内部，向下滚动时随作品列表一起移出视野
        workList.setScrollHeader(header);
        renderedDetailMode = Config.setting.gridItemDetailModel.get();

        getChildren().add(workList);

        sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null) {
                Config.setting.gridItemDetailModel.removeListener(viewModeListener);
            } else {
                Config.setting.gridItemDetailModel.addListener(viewModeListener);
                if (renderedDetailMode != Config.setting.gridItemDetailModel.get()) {
                    renderedDetailMode = Config.setting.gridItemDetailModel.get();
                    workList.rebuild();
                }
            }
        });
    }

    /**
     * 路由切换到作品列表时调用。
     */
    public void applySource(WorkSource source) {
        viewModel.applySource(source);
    }

    /**
     * 页面重新显示时刷新当前页，保留页码状态。
     */
    public void reload() {
        viewModel.reload();
    }

    @Override
    public void changeSkin() {
        applyStyleSheet();
    }

    private void applyStyleSheet() {
        getStylesheets().clear();
        getStylesheets().add(CssLoader.getCss(CssLoader.workGrid));
    }

    // ==================== 页头 ====================

    /**
     * 创建作品列表页头。
     * 左侧：来源图标、标题、说明；右侧：前进/后退、排序筛选、视图切换、同步。
     */
    private PageHeader createPageHeader() {
        PageHeader header = new PageHeader();
        header.getStyleClass().addAll("work-page-header", "grid-toolbar");
        header.setIconNode(new FontIcon(sourceIcon(viewModel.getSource().type())));
        viewModel.sourceProperty().addListener((observable, oldSource, newSource) ->
                header.setIconNode(new FontIcon(sourceIcon(newSource.type()))));
        header.titleProperty().bind(viewModel.sourceTitleProperty());
        header.subtitleProperty().bind(Bindings.createStringBinding(
                () -> {
                    String text = viewModel.getSource().description();
                    if (viewModel.getListModel().isLoaded()) {
                        return text + "，共 " + viewModel.getListModel().getTotal() + " 个作品。";
                    }
                    return text + "。";
                },
                viewModel.sourceProperty(),
                viewModel.getListModel().loadedProperty(),
                viewModel.getListModel().totalProperty()));

        HBox tools = new HBox(6, createColumnsButton(), createSortButton(), createViewButton(), createSyncButton());
        tools.setAlignment(Pos.CENTER_RIGHT);
        header.setActions(tools);
        return header;
    }

    /**
     * 每行显示数量：点击弹出 2~8 列的选择菜单。
     * <p>
     * 大图与小图各存一份设置，菜单打开时按当前显示模式读取，切换后互不影响。
     * 用单选按钮而非自定义按钮，既有一眼可见的选中状态，也沿用排序菜单的行高。
     */
    private Button createColumnsButton() {
        Button columnsBtn = new Button();
        columnsBtn.setGraphic(new FontIcon(MaterialDesignV.VIEW_WEEK_OUTLINE));
        columnsBtn.setTooltip(new Tooltip("每行显示数量"));
        columnsBtn.getStyleClass().addAll("tool-btn", "tool-columns-btn");

        ContextMenu menu = new ContextMenu();
        ToggleGroup group = new ToggleGroup();
        for (int columns = 2; columns <= 8; columns++) {
            int value = columns;
            RadioButton radio = new RadioButton(value + " 列");
            radio.setToggleGroup(group);
            radio.setAccessibleText(String.valueOf(value));
            radio.setOnAction(event -> {
                Config.setting.resolveGridColumns().set(value);
                menu.hide();
            });
            CustomMenuItem menuItem = new CustomMenuItem(radio);
            menuItem.setHideOnClick(false);
            menu.getItems().add(menuItem);
        }
        // 每次弹出前同步选中项：读取当前显示模式对应的列数
        columnsBtn.setOnAction(event -> {
            int current = Config.setting.getGridColumns();
            for (var item : menu.getItems()) {
                if (item instanceof CustomMenuItem custom && custom.getContent() instanceof RadioButton radio) {
                    radio.setSelected(Integer.parseInt(radio.getAccessibleText()) == current);
                }
            }
            menu.show(columnsBtn, Side.BOTTOM, 0, 0);
        });
        return columnsBtn;
    }

    /**
     * 排序与筛选菜单。
     */
    private Button createSortButton() {
        Button sortBtn = new Button();
        sortBtn.setGraphic(new FontIcon(MaterialDesignS.SORT));
        sortBtn.setTooltip(new Tooltip("排序与筛选"));
        sortBtn.getStyleClass().addAll("tool-btn", "tool-sort-btn");

        ContextMenu sortMenu = new ContextMenu();
        ToggleGroup sortToggleGroup = new ToggleGroup();
        CustomMenuItem addTimeItem = createSortRadioItem("添加时间", "1", sortToggleGroup);
        CustomMenuItem rjItem = createSortRadioItem("作品编号", "2", sortToggleGroup);
        CustomMenuItem releaseItem = createSortRadioItem("发行时间", "3", sortToggleGroup);
        CustomMenuItem rateItem = createSortRadioItem("评分", "4", sortToggleGroup);
        CustomMenuItem randomItem = createSortRadioItem("随机", "5", sortToggleGroup);

        int currentType = viewModel.getSqlType();
        ToggleButton defaultToggle = switch (currentType) {
            case 1 -> (ToggleButton) addTimeItem.getContent();
            case 2 -> (ToggleButton) rjItem.getContent();
            case 3 -> (ToggleButton) releaseItem.getContent();
            case 4 -> (ToggleButton) rateItem.getContent();
            case 5 -> (ToggleButton) randomItem.getContent();
            default -> (ToggleButton) addTimeItem.getContent();
        };
        defaultToggle.setSelected(true);

        CustomMenuItem separator = new CustomMenuItem(new Separator(Orientation.HORIZONTAL), false);
        separator.setHideOnClick(false);
        separator.setDisable(true);

        CustomMenuItem descItem = createFilterCheckboxItem("倒序", viewModel.sqlOrderProperty());
        CustomMenuItem nsfwItem = createFilterCheckboxItem("NSFW", viewModel.sqlNsfwProperty());
        CustomMenuItem subtitleItem = createFilterCheckboxItem("字幕", viewModel.sqlSubtextProperty());
        CustomMenuItem noMateItem = createFilterCheckboxItem("未同步", viewModel.sqlNoMateProperty());
        CustomMenuItem localItem = createFilterCheckboxItem("本地", viewModel.showLocalProperty());
        CustomMenuItem networkItem = createFilterCheckboxItem("网络", viewModel.showNetworkProperty());

        sortMenu.getItems().addAll(
                addTimeItem, rjItem, releaseItem, rateItem, randomItem,
                separator,
                descItem, nsfwItem, subtitleItem, noMateItem, localItem, networkItem
        );
        sortBtn.setOnAction(event -> sortMenu.show(sortBtn, Side.BOTTOM, 0, 0));
        return sortBtn;
    }

    /**
     * 大图/小图切换。
     */
    private Button createViewButton() {
        Button viewBtn = new Button();
        viewBtn.graphicProperty().bind(Bindings.when(Config.setting.gridItemDetailModel)
                .then(new FontIcon(MaterialDesignV.VIEW_COMFY))
                .otherwise(new FontIcon(MaterialDesignV.VIEW_MODULE)));
        viewBtn.setTooltip(new Tooltip("切换大图/小图"));
        viewBtn.getStyleClass().addAll("tool-btn", "tool-view-btn");
        viewBtn.setOnAction(event -> viewModel.changeItemShowModel());
        return viewBtn;
    }

    /**
     * 智能同步当前页作品。
     */
    private Button createSyncButton() {
        Button syncBtn = new Button();
        syncBtn.setGraphic(new FontIcon(MaterialDesignC.CLOUD_SYNC));
        syncBtn.setTooltip(new Tooltip("智能同步"));
        syncBtn.getStyleClass().addAll("tool-btn", "tool-sync-btn");
        syncBtn.setOnAction(event -> {
            if (Config.setting.sourcesUrl.get() == null || !Config.setting.sourcesUrl.get().startsWith("https://")) {
                Alerts.warning().message("请先正确设置同步网址").cancel("确定").show();
                return;
            }
            viewModel.syncMetadata();
        });
        return syncBtn;
    }

    /**
     * 根据作品来源选择页头图标。
     */
    private static Ikon sourceIcon(AsmrKeyType type) {
        return switch (type) {
            case ALL -> Material2OutlinedAL.LIBRARY_MUSIC;
            case FAVORITE -> Material2OutlinedAL.FAVORITE_BORDER;
            case CATEGORY -> Material2OutlinedAL.CATEGORY;
            case TAG -> Material2OutlinedAL.LOYALTY;
            case VA -> Material2OutlinedMZ.PERSON;
            case CIRCLE -> Material2OutlinedAL.LOCAL_FIRE_DEPARTMENT;
            case FOLDER -> Material2OutlinedAL.FOLDER_OPEN;
            case ONLINE -> Material2OutlinedMZ.WEB_ASSET;
            case SEARCH -> Material2OutlinedMZ.SEARCH;
        };
    }

    /**
     * 创建排序单选项。
     */
    private CustomMenuItem createSortRadioItem(String text, String value, ToggleGroup toggleGroup) {
        RadioButton radio = new RadioButton(text);
        radio.setToggleGroup(toggleGroup);
        radio.setAccessibleText(value);
        radio.setOnAction(event -> viewModel.setSqlType(Integer.parseInt(value)));
        CustomMenuItem item = new CustomMenuItem(radio);
        item.setHideOnClick(false);
        return item;
    }

    /**
     * 创建筛选复选项。
     */
    private CustomMenuItem createFilterCheckboxItem(String text, javafx.beans.property.SimpleBooleanProperty property) {
        CheckBox checkBox = new CheckBox(text);
        checkBox.selectedProperty().bindBidirectional(property);
        CustomMenuItem item = new CustomMenuItem(checkBox);
        item.setHideOnClick(false);
        return item;
    }
}
