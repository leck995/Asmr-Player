package cn.tealc.asmr.ui.main;

import cn.tealc.asmr.eventbus.EventBus;
import cn.tealc.asmr.model.AsmrKeyType;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.mvvm.NotificationObserver;
import cn.tealc.asmr.ui.component.EmptyState;
import cn.tealc.asmr.ui.component.LoadingDot;
import cn.tealc.asmr.ui.component.PageHeader;
import cn.tealc.asmr.model.Actor;
import cn.tealc.asmr.ui.TeaSkin;
import cn.tealc.asmr.ui.dialog.DialogAction;
import cn.tealc.asmr.ui.dialog.DialogSpec;
import cn.tealc.asmr.ui.dialog.Dialogs;
import cn.tealc.asmr.ui.navigation.NavigationService;
import cn.tealc.asmr.ui.navigation.WorkSource;
import cn.tealc.asmr.ui.navigation.WorksRoute;
import cn.tealc.asmr.utils.CssLoader;
import cn.tealc.asmr.utils.TimeFormatUtils;
import cn.tealc.asmr.mvvm.InjectViewModel;
import cn.tealc.asmr.mvvm.JavaView;
import javafx.beans.binding.Bindings;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * 分类浏览页：按类别、声优、标签、社团四种字典维度浏览并管理条目。
 * <p>
 * 顶部类型切换按钮控制 {@link CategoriesViewModel} 重新起异步查询，
 * 下方用 {@code FlowPane} 铺开条目胶囊；加载中显示 {@link LoadingDot}，
 * 结果为空时显示 {@link EmptyState}。
 * <p>
 * 左键点击条目跳转到对应作品列表（{@code WorksRoute}），条目数为 0 时不跳转；
 * 右键弹出菜单，可添加到快捷入口、删除，标签类型额外支持“编辑标签”。
 * 换肤时通过 {@link TeaSkin} 清除并重挂本业务样式表。
 *
 * @author Leck
 */
public class CategoriesView extends BorderPane implements JavaView<CategoriesViewModel>,Initializable, TeaSkin {
    @InjectViewModel
    private CategoriesViewModel viewModel;
    /** 四种类型切换按钮的 id 取值，与 {@link AsmrKeyType} 的声明顺序一致 */
    public static final int CATEGORY=1;
    public static final int CIRCLE=3;
    public static final int VA=4;
    public static final int TAG=2;

    private FlowPane contentPane;
    private ContextMenu contextMenu;
    private MenuItem editTagMenuItem;
    private Button createTagButton;
    private StackPane loadingPane;
    private EmptyState emptyState;
    private Label itemCountLabel;
    private final NotificationObserver skinObserver = (key, payload) -> applyStyleSheet();

    /**
     * 挂上业务样式表，并随 scene 变化订阅/退订 {@code CHANGE_SKIN}：
     * 页面被挂进场景时订阅，scene 置空（页面被移出）时退订。
     */
    public CategoriesView() {
        getStyleClass().add("category-bg");
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
    public void changeSkin() {
        applyStyleSheet();
    }

    private void applyStyleSheet() {
        getStylesheets().clear();
        getStylesheets().add(CssLoader.getCss(CssLoader.categories));
    }


    /**
     * 组装页面并把各状态区域叠到同一容器上。
     * <p>
     * 类型按钮用同一个 {@code ToggleGroup} 保证互斥，且禁止取消当前选中项
     * （未选中时立刻重新选中并返回）。新建标签按钮仅在类型为 TAG 时可见。
     */
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("category-scroll");


        loadingPane = new StackPane(new LoadingDot());
        loadingPane.setAlignment(Pos.CENTER);
        loadingPane.visibleProperty().bind(viewModel.loadingProperty());
        loadingPane.getStyleClass().add("category-loading");

        emptyState = new EmptyState();
        emptyState.setIcon(Material2OutlinedAL.CATEGORY);
        emptyState.setTitle("暂无条目");
        emptyState.setDescription("当前筛选下还没有内容。");
        emptyState.visibleProperty().bind(
                Bindings.isEmpty(viewModel.getItems()).and(viewModel.loadingProperty().not()));
        emptyState.managedProperty().bind(emptyState.visibleProperty());



        ToggleGroup toggleGroup = new ToggleGroup();
        ToggleButton categoryBtn = createTypeButton("类别", Material2OutlinedMZ.OUTLINED_FLAG);
        categoryBtn.setId(String.valueOf(CATEGORY));
        ToggleButton vaBtn = createTypeButton("声优", Material2OutlinedMZ.PERSON);
        vaBtn.setId(String.valueOf(VA));
        ToggleButton tagBtn = createTypeButton("标签", Material2OutlinedAL.LOYALTY);
        tagBtn.setId(String.valueOf(TAG));
        ToggleButton circleBtn = createTypeButton("社团", Material2OutlinedAL.LOCAL_FIRE_DEPARTMENT);
        circleBtn.setId(String.valueOf(CIRCLE));
        categoryBtn.setToggleGroup(toggleGroup);
        vaBtn.setToggleGroup(toggleGroup);
        tagBtn.setToggleGroup(toggleGroup);
        circleBtn.setToggleGroup(toggleGroup);
        categoryBtn.setOnAction(actionEvent -> {
            if (!categoryBtn.isSelected()) {
                categoryBtn.setSelected(true);
                return;
            }
            viewModel.update(AsmrKeyType.CATEGORY);
        });

        vaBtn.setOnAction(actionEvent -> {
            if (!vaBtn.isSelected()) {
                vaBtn.setSelected(true);
                return;
            }
            viewModel.update(AsmrKeyType.VA);
        });
        tagBtn.setOnAction(actionEvent -> {
            if (!tagBtn.isSelected()) {
                tagBtn.setSelected(true);
                return;
            }
            viewModel.update(AsmrKeyType.TAG);
        });
        circleBtn.setOnAction(actionEvent -> {
            if (!circleBtn.isSelected()) {
                circleBtn.setSelected(true);
                return;
            }
            viewModel.update(AsmrKeyType.CIRCLE);
        });

        HBox typeSelector = new HBox(categoryBtn, vaBtn, tagBtn, circleBtn);
        typeSelector.getStyleClass().add("category-type-selector");
        itemCountLabel = new Label("0");
        itemCountLabel.getStyleClass().add("category-count");
        Label itemCountDescription = new Label("个条目");
        itemCountDescription.getStyleClass().add("category-count-description");

        createTagButton = new Button(null, new FontIcon(Material2OutlinedAL.ADD));
        createTagButton.getStyleClass().add("category-add-button");
        createTagButton.setTooltip(new Tooltip("新建标签"));
        createTagButton.setAccessibleText("新建标签");
        createTagButton.setOnAction(event -> showTagDialog(null));
        createTagButton.visibleProperty().bind(viewModel.typeProperty().isEqualTo(AsmrKeyType.TAG));
        createTagButton.managedProperty().bind(createTagButton.visibleProperty());

        Region countSpacer = new Region();
        HBox.setHgrow(countSpacer, Priority.ALWAYS);
        Label contentTitle = new Label("全部条目");
        contentTitle.getStyleClass().add("category-content-title");
        HBox contentHeader = new HBox(8, contentTitle, countSpacer, itemCountLabel, itemCountDescription, createTagButton);
        contentHeader.getStyleClass().add("category-content-header");

        contentPane = new FlowPane();
        contentPane.setVgap(10);
        contentPane.setHgap(10);
        contentPane.setPadding(new Insets(14));
        contentPane.getStyleClass().add("category-flow");
        viewModel.getItems().addListener((javafx.collections.ListChangeListener<? super Actor>) change -> refreshItems());
        refreshItems();
        scrollPane.setContent(contentPane);

        PageHeader header = new PageHeader();
        header.setIcon(Material2OutlinedAL.CATEGORY);
        header.setTitle("分类浏览");
        header.setSubtitle("按类别、声优、标签或社团筛选作品。右键条目可快捷操作，标签支持编辑描述。");
        header.setActions(typeSelector);

        VBox top = new VBox(header);
        top.setPadding(new Insets(20, 20, 0, 20));
        setTop(top);

        StackPane scrollHost = new StackPane(scrollPane, emptyState);
        VBox contentBox = new VBox(10, contentHeader, scrollHost);
        contentBox.setPadding(new Insets(6, 20, 20, 20));
        VBox.setVgrow(scrollHost, Priority.ALWAYS);
        setCenter(new StackPane(contentBox, loadingPane));

        categoryBtn.setSelected(true);

        initContextMenu();

    }



    /**
     * 生成单个条目胶囊（名称 + 条目数），并挂上左右键行为。
     * <p>
     * 右键先把条目记为 ViewModel 的选中项，再按类型决定是否显示“编辑标签”菜单项并弹出菜单；
     * 左键跳转作品列表，条目数为 0 时不响应。标签类型在有描述或时间时安装悬浮提示。
     *
     * @param actor 条目数据（名称、条目数、描述与时间）
     * @param type  当前列表类型，决定右键菜单与是否可编辑
     * @return 可直接加入 {@code FlowPane} 的条目节点
     */
    private Pane creatTag(Actor actor,AsmrKeyType type){
        HBox hBox = new HBox();
        Label sizeLabel = new Label(String.valueOf(actor.getNum()));
        sizeLabel.getStyleClass().add("size-label");
        Label nameLabel = new Label(actor.getName());
        nameLabel.getStyleClass().add("name-label");
        hBox.getChildren().addAll(nameLabel, sizeLabel);
        hBox.setSpacing(8);
        hBox.setAlignment(Pos.CENTER);
        hBox.getStyleClass().add("category-item");

        if (type == AsmrKeyType.TAG) {
            String tooltip = tagTooltip(actor);
            if (!tooltip.isBlank()) {
                Tooltip.install(hBox, new Tooltip(tooltip));
            }
        }

        hBox.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getButton() == MouseButton.SECONDARY){
                viewModel.setSelectActor(actor);
                editTagMenuItem.setVisible(type == AsmrKeyType.TAG);
                contextMenu.show(this.getScene().getWindow(), mouseEvent.getScreenX(), mouseEvent.getScreenY());
            }
            if (mouseEvent.getButton() == MouseButton.PRIMARY){
                if (actor.getNum() == 0) return;
                DI.get(NavigationService.class).push(new WorksRoute(WorkSource.of(type, actor.getName())));
            }
        });


        return hBox;
    }

    private ToggleButton createTypeButton(String text, org.kordamp.ikonli.Ikon icon) {
        ToggleButton button = new ToggleButton(text, new FontIcon(icon));
        button.getStyleClass().add("category-head-toggle-btn");
        return button;
    }

    /**
     * 按 ViewModel 当前列表整批重建条目胶囊，并同步条目计数。
     */
    private void refreshItems() {
        contentPane.getChildren().setAll(viewModel.getItems().stream()
                .map(actor -> creatTag(actor, viewModel.getType()))
                .toList());
        itemCountLabel.setText(String.valueOf(viewModel.getItems().size()));
    }

    /**
     * 创建条目右键菜单。“编辑标签”默认隐藏，仅在右键标签条目时由
     * {@link #creatTag(Actor, AsmrKeyType)} 显示。
     */
    private void  initContextMenu(){

        contextMenu=new ContextMenu();
        editTagMenuItem=new MenuItem("编辑标签", new FontIcon(Material2OutlinedAL.EDIT));
        editTagMenuItem.setVisible(false);
        editTagMenuItem.setOnAction(actionEvent -> showTagDialog(viewModel.getSelectActor()));
        MenuItem fixedMI=new MenuItem("添加到快捷", new FontIcon("mdi2s-star-outline"));
        MenuItem deleteMI=new MenuItem("删除", new FontIcon("mdi2d-delete-outline"));
        fixedMI.setOnAction(actionEvent -> {

            viewModel.fixed();
        });
        deleteMI.setOnAction(actionEvent -> {
            viewModel.delete();
        });

        contextMenu.getItems().addAll(editTagMenuItem, fixedMI, deleteMI);
    }

    /**
     * 弹出标签新建/编辑对话框。
     * <p>
     * {@code tag} 为 null 表示新建：名称可编辑，保存调用 {@code createTag}；
     * 否则只允许修改描述（名称输入框禁用），并额外展示创建与修改时间。
     * 名称为空或写入失败时把错误显示在对话框内并保持打开，成功后才关闭。
     *
     * @param tag 待编辑的标签，null 表示新建
     */
    private void showTagDialog(Actor tag) {
        boolean creating = tag == null;
        TextField name = new TextField(creating ? "" : tag.getName());
        name.setPromptText("标签名称");
        name.setEditable(creating);
        name.setFocusTraversable(creating);
        TextArea description = new TextArea(creating ? "" : tag.getDescription() == null ? "" : tag.getDescription());
        description.setPromptText("描述（可选）");
        description.setWrapText(true);
        description.setPrefRowCount(4);
        Label error = new Label();
        error.getStyleClass().add("category-dialog-error");
        error.visibleProperty().bind(error.textProperty().isNotEmpty());
        error.managedProperty().bind(error.visibleProperty());

        VBox body = new VBox(8, new Label("名称"), name, new Label("描述"), description);
        if (!creating) {
            VBox metadata = new VBox(3,
                    new Label("创建时间：" + TimeFormatUtils.formatDateTime(tag.getCreateDateTime())),
                    new Label("修改时间：" + TimeFormatUtils.formatDateTime(tag.getUpdateDateTime())));
            metadata.getStyleClass().add("category-dialog-meta");
            body.getChildren().add(metadata);
        }
        body.getChildren().add(error);
        body.setPrefWidth(420);

        DialogAction save = DialogAction.of(creating ? "创建" : "保存", "accent", handle -> {
            String trimmed = name.getText().trim();
            if (trimmed.isEmpty()) {
                error.setText("标签名称不能为空");
                return;
            }
            boolean status = creating
                    ? viewModel.createTag(trimmed, description.getText().trim())
                    : viewModel.updateTagDescription(tag.getId(), description.getText().trim());
            if (status) {
                handle.close();
            } else {
                error.setText(creating ? "标签已存在，或创建失败" : "保存失败，请重试");
            }
        }).defaultButton().disabledWhen(
                Bindings.createBooleanBinding(() -> name.getText().isBlank(), name.textProperty()));

        Dialogs.show(DialogSpec.builder()
                .title(creating ? "新建标签" : "编辑标签")
                .content(body)
                .width(480)
                .stylesheet(CssLoader.getCss(CssLoader.categories))
                .action(save)
                .cancel()
                .build());
    }

    /**
     * 拼接标签条目的悬浮提示文本，描述、创建时间、修改时间按行排列。
     *
     * @param actor 标签条目
     * @return 提示文本；无可展示内容时返回空串，调用方据此不安装提示
     */
    private static String tagTooltip(Actor actor) {
        StringBuilder text = new StringBuilder();
        if (actor.getDescription() != null && !actor.getDescription().isBlank()) {
            text.append(actor.getDescription());
        }
        if (actor.getCreateDateTime() != null && !actor.getCreateDateTime().isBlank()) {
            if (!text.isEmpty()) text.append('\n');
            text.append("创建：").append(TimeFormatUtils.formatDateTime(actor.getCreateDateTime()));
        }
        if (actor.getUpdateDateTime() != null && !actor.getUpdateDateTime().isBlank()) {
            if (!text.isEmpty()) text.append('\n');
            text.append("修改：").append(TimeFormatUtils.formatDateTime(actor.getUpdateDateTime()));
        }
        return text.toString();
    }



}

