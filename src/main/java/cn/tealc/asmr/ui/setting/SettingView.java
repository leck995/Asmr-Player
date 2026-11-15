package cn.tealc.asmr.ui.setting;

import cn.tealc.asmr.eventbus.EventBus;

import cn.tealc.asmr.Main;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.ui.dialog.Alerts;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.galgame.GalgameSkin;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.model.message.MessageType;
import cn.tealc.asmr.player.VlcPlayer;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.service.WorkService;
import cn.tealc.asmr.service.FileOperationService;
import cn.tealc.asmr.subtitle.SubtitleScriptMode;
import cn.tealc.asmr.ui.component.PageHeader;
import cn.tealc.asmr.utils.AppPaths;
import cn.tealc.asmr.utils.CssLoader;
import cn.tealc.asmr.utils.FileUtils;
import atlantafx.base.controls.ProgressSliderSkin;
import atlantafx.base.controls.Tile;
import atlantafx.base.controls.ToggleSwitch;
import cn.tealc.asmr.mvvm.FXMLView;
import cn.tealc.asmr.mvvm.InjectViewModel;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

/**
 * 设置页：把 {@link SettingViewModel} 暴露的配置项分区绑定到控件，统一由页头按钮保存。
 * <p>
 * 由左侧导航以 {@code SettingsRoute} 路由方式展示；页头的“保存”直接写配置，
 * “恢复默认”先用 {@link Alerts} 二次确认，恢复后再调用 {@link #refreshControlsAfterReset()}
 * 同步那些无法双向绑定的控件（Spinner、ColorPicker、单选按钮组）。
 * <p>
 * 未选中的 Tab 内容延后到首次选中时才挂入场景，见 {@link #deferInactiveTabContent()}，
 * 避免打开设置页就完成全部皮肤控件的初始化。
 *
 * @author Leck
 */
public class SettingView implements FXMLView<SettingViewModel>, Initializable {
    private final WorkService workService = DI.get(WorkService.class);
    private final FileOperationService fileOperationService = DI.get(FileOperationService.class);
    @InjectViewModel
    private SettingViewModel viewModel;
    @FXML
    private AnchorPane root;
    @FXML
    private PageHeader pageHeader;
    @FXML
    private TabPane tabPane;
    @FXML
    private Button choosePlayerBtn,importImageBtn,showLrcWindowBtn,showLicenseBtn;

    @FXML
    private ToggleSwitch openFontSmooth,openGalgame
            ,openLrcBorder
            ,openGalgameBg,openGalgameAnimation,openProxy,openSyncImageReDown
           ,openAutoCheckVersion,openSyncSkipAlready,openMediaInnerInfo,openLrcBold,openLrcStroke,openUserChineseTitle,openPlayingHistory,openCache;

    @FXML
    private ToggleSwitch openPassword;

    /** 反转鼠标侧键翻页方向。 */
    @FXML
    private ToggleSwitch reverseSideButtonPaging;

    @FXML
    private RadioButton openGridItemDetail,openGridItemThumb;

    @FXML
    private PasswordField passwordField,aiTranslationApiKeyPasswordField;
    @FXML
    private Tile passwordTile, galgameBgImageTile, lrcBorderTile, lrcStrokeTile, proxyTile;
    @FXML
    private TextField imageTextField,proxyHostTextField,proxyPortTextField,galgameBgSizeWidthTextField,
            galgameBgSizeHeightTextField,sourcesUrlTextField,cacheMaxSizeTextField,
            aiTranslationEndpointTextField,aiTranslationModelTextField,aiTranslationBatchSizeTextField,
            aiTranslationConcurrencyTextField,aiTranslationTimeoutTextField;


    @FXML
    private TextField playerURLTextField;

    @FXML
    private Button addButton;


    @FXML
    private RadioButton detailLrcAlignmentLeftRadio,detailLrcAlignmentCenterRadio;

    @FXML
    private Hyperlink githubLink;


    @FXML
    private RadioButton saveCoverTypeRadio1,saveCoverTypeRadio2;

    @FXML
    private Slider lrcFontSizeSlider;
    @FXML
    private Spinner<Integer> gridListCountSpinner;
    @FXML
    private Spinner<Integer> gridColumnsSpinner;

    @FXML
    private Spinner<Integer> detailAlbumRadiusSpinner, detailAlbumEffectSpinner;

    @FXML
    private ColorPicker lrcFontColorPicker,lrcBorderColorPicker,lrcStrokeColorPicker;

    @FXML
    private Tile aboutTile;

    @FXML
    private Label cacheInfoLabel;
    @FXML
    private Button clearCacheBtn;
    @FXML
    private Tile dataDirTile;

    @FXML
    private ComboBox<GalgameSkin> galgameSkinComboBox;
    @FXML
    private ComboBox<SubtitleScriptMode> subtitleScriptModeComboBox;
    @FXML
    private ComboBox<String> aiTranslationPromptPresetComboBox;
    @FXML
    private TextArea textBlacklistArea;
    @FXML
    private TextArea aiTranslationPromptArea;

    @FXML
    private ToggleSwitch openExternalPlayer;
    @FXML
    private TextField externalPlayerPathTextField;
    @FXML
    private TextField externalPlayerArgsTextField;
    @FXML
    private Tile externalPlayerPathTile;
    @FXML
    private Tile externalPlayerArgsTile;

    /**
     * 绑定设置页全部控件：按“基本/桌面歌词/播放界面/Galgame/插件（同步与缓存）/黑名单/AI 翻译/关于”
     * 分区把开关、输入框双向绑定到视图模型，颜色、Spinner、单选组等无法直接绑定的控件在监听回调里写回。
     * <p>
     * 页头动作按钮在此动态创建；皮肤与字幕模式等下拉项的候选值来自枚举或注册表，
     * 选择结果立即写进模型，但是否落盘由“保存”决定。
     */
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        root.getStylesheets().clear();
        root.getStylesheets().add(CssLoader.getCss(CssLoader.setting));

        pageHeader.setIcon(Material2OutlinedMZ.SETTINGS);
        pageHeader.setTitle("设置");
        pageHeader.setSubtitle("管理播放、界面、同步和 AI 翻译偏好。");
        pageHeader.setSeparatorVisible(false);

        dataDirTile.setDescription(AppPaths.dataDir().getAbsolutePath());

        Button recoveryBtn = new Button("恢复默认");
        recoveryBtn.setOnAction(this::resetToDefaults);
        Button saveBtn = new Button("保存");
        saveBtn.getStyleClass().add("accent");
        saveBtn.setOnAction(this::save);
        HBox actions = new HBox(10, recoveryBtn, saveBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);
        pageHeader.setActions(actions);

        // 为所有 Slider 设置 atlantafx ProgressSliderSkin（进度填充样式）
        lrcFontSizeSlider.setSkin(new ProgressSliderSkin(lrcFontSizeSlider));


        /*=====================基本设置相关=======================*/
        openAutoCheckVersion.selectedProperty().bindBidirectional(viewModel.autoCheckVersionProperty());
        openPassword.selectedProperty().bindBidirectional(viewModel.pKeyModelProperty());
        openFontSmooth.selectedProperty().bindBidirectional(viewModel.textSmoothProperty());
        passwordField.textProperty().bindBidirectional(viewModel.startPasswordProperty());
        // 已存密码不回填，留空保存表示保持不变
        passwordField.promptTextProperty().bind(Bindings.when(viewModel.startPasswordSetProperty())
                .then("已设置，留空表示不修改")
                .otherwise("最大支持16位密码"));
        // 设置密码 Tile 仅在启动密码开启时显示
        passwordTile.visibleProperty().bind(openPassword.selectedProperty());
        passwordTile.managedProperty().bind(openPassword.selectedProperty());
        openPassword.selectedProperty().addListener((observableValue, aBoolean, t1) -> {
            if (!t1){
                passwordField.setText(null);
            }
        });
        openMediaInnerInfo.selectedProperty().bindBidirectional(viewModel.mediaInnerInfoModelProperty());

        // 网格每页数量：Spinner 步进 5
        SpinnerValueFactory.IntegerSpinnerValueFactory countFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 500, viewModel.gridListCountProperty().get(), 5);
        gridListCountSpinner.setValueFactory(countFactory);
        makeSpinnerEditable(gridListCountSpinner);
        gridListCountSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            viewModel.gridListCountProperty().set(newVal);
            viewModel.changeGridItemModel();
        });
        openPlayingHistory.selectedProperty().bindBidirectional(viewModel.userPlayingHistoryProperty());
        reverseSideButtonPaging.selectedProperty().bindBidirectional(viewModel.sideButtonPagingReversedProperty());


        openGridItemDetail.selectedProperty().bindBidirectional(viewModel.gridItemDetailModelProperty());
        openGridItemThumb.setSelected(!openGridItemDetail.isSelected());

        openGridItemDetail.setOnAction(actionEvent -> viewModel.changeGridItemModel());
        openGridItemThumb.setOnAction(actionEvent -> viewModel.changeGridItemModel());

        // 每行显示数量：2~8 列，编辑当前显示模式对应的那一份设置
        SpinnerValueFactory.IntegerSpinnerValueFactory columnsFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(2, 8,
                        Config.setting.getGridColumns(), 1);
        gridColumnsSpinner.setValueFactory(columnsFactory);
        makeSpinnerEditable(gridColumnsSpinner);
        gridColumnsSpinner.valueProperty().addListener((obs, oldVal, newVal) ->
                Config.setting.resolveGridColumns().set(newVal));
        // 在设置页切换大图/小图后，同步显示对应模式的列数
        viewModel.gridItemDetailModelProperty().addListener((obs, oldVal, newVal) -> {
            if (gridColumnsSpinner.getValueFactory() != null) {
                gridColumnsSpinner.getValueFactory().setValue(Config.setting.getGridColumns());
            }
        });

        /*=====================桌面歌词相关=======================*/
        openLrcBorder.selectedProperty().bindBidirectional(viewModel.desktopLRCBorderModelProperty());
        openLrcStroke.selectedProperty().bindBidirectional(viewModel.desktopLRCStrokeModelProperty());
        openLrcBold.selectedProperty().bindBidirectional(viewModel.desktopLRCBoldModelProperty());
        lrcFontSizeSlider.setMax(100);
        lrcFontSizeSlider.setMin(20);
        lrcFontSizeSlider.valueProperty().bindBidirectional(viewModel.desktopLRCFontSizeProperty());

        if (VlcPlayer.getInstance().isDesktopLrcShow()){
            showLrcWindowBtn.setText("关闭");
        }else {
            showLrcWindowBtn.setText("开启");
        }
        showLrcWindowBtn.setOnAction(actionEvent ->{
            if (VlcPlayer.getInstance().isDesktopLrcShow()){
                VlcPlayer.getInstance().desktopLrcShowProperty().set(false);
                showLrcWindowBtn.setText("开启");
            }else {
                VlcPlayer.getInstance().desktopLrcShowProperty().set(true);
                showLrcWindowBtn.setText("关闭");
            }
        } );

        lrcFontColorPicker.setValue(Color.web(Config.setting.desktopLRCFontColor.get())); //桌面歌词字体颜色
        lrcFontColorPicker.setOnAction(actionEvent -> viewModel.updateLrcFontColor(lrcFontColorPicker.getValue()));

        lrcBorderColorPicker.setValue(Color.web(Config.setting.desktopLRCBorderColor.get()));//桌面歌词阴影颜色
        lrcBorderColorPicker.setOnAction(actionEvent -> viewModel.updateLrcBorderColor(lrcBorderColorPicker.getValue()));
        // 阴影颜色 Tile 仅在开启阴影时显示
        lrcBorderTile.visibleProperty().bind(openLrcBorder.selectedProperty());
        lrcBorderTile.managedProperty().bind(openLrcBorder.selectedProperty());

        lrcStrokeColorPicker.setValue(Color.web(Config.setting.desktopLRCStrokeColor.get()));//桌面歌词描边颜色
        lrcStrokeColorPicker.setOnAction(actionEvent -> viewModel.updateLrcStrokeColor(lrcStrokeColorPicker.getValue()));
        // 描边颜色 Tile 仅在开启描边时显示
        lrcStrokeTile.visibleProperty().bind(openLrcStroke.selectedProperty());
        lrcStrokeTile.managedProperty().bind(openLrcStroke.selectedProperty());



        /*=====================播放界面相关=======================*/
        // 封面圆角：Spinner 0=关闭，>0=开启且值为圆角大小
        SpinnerValueFactory.IntegerSpinnerValueFactory radiusFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 100,
                        viewModel.detailAlbumRadiusModelProperty().get() ? (int) viewModel.detailAlbumRadiusSizeProperty().get() : 0);
        detailAlbumRadiusSpinner.setValueFactory(radiusFactory);
        detailAlbumRadiusSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            viewModel.detailAlbumRadiusModelProperty().set(newVal != null && newVal > 0);
            viewModel.detailAlbumRadiusSizeProperty().set(newVal != null ? newVal.doubleValue() : 0);
        });

        detailLrcAlignmentCenterRadio.selectedProperty().bindBidirectional(viewModel.detailLrcAlignmentProperty());
        detailLrcAlignmentLeftRadio.setSelected(!viewModel.detailLrcAlignmentProperty().get());
        detailLrcAlignmentLeftRadio.setOnAction(actionEvent -> detailLrcAlignmentCenterRadio.setSelected(!detailLrcAlignmentLeftRadio.isSelected()));

        subtitleScriptModeComboBox.setItems(FXCollections.observableArrayList(SubtitleScriptMode.values()));
        subtitleScriptModeComboBox.setValue(SubtitleScriptMode.fromConfig(viewModel.subtitleScriptModeProperty().get()));
        subtitleScriptModeComboBox.valueProperty().addListener((observable, oldValue, mode) -> {
            if (mode != null) {
                viewModel.subtitleScriptModeProperty().set(mode.name());
            }
        });
        viewModel.subtitleScriptModeProperty().addListener((observable, oldValue, modeValue) -> {
            SubtitleScriptMode mode = SubtitleScriptMode.fromConfig(modeValue);
            if (subtitleScriptModeComboBox.getValue() != mode) {
                subtitleScriptModeComboBox.setValue(mode);
            }
        });

        /*==========================第三方播放器=====================================*/
        openExternalPlayer.selectedProperty().bindBidirectional(viewModel.externalPlayerEnabledProperty());
        // 未启用时隐藏细节配置，保持基本页简洁
        externalPlayerPathTile.visibleProperty().bind(openExternalPlayer.selectedProperty());
        externalPlayerPathTile.managedProperty().bind(openExternalPlayer.selectedProperty());
        externalPlayerArgsTile.visibleProperty().bind(openExternalPlayer.selectedProperty());
        externalPlayerArgsTile.managedProperty().bind(openExternalPlayer.selectedProperty());
        externalPlayerPathTextField.textProperty().bindBidirectional(viewModel.externalPlayerPathProperty());
        externalPlayerArgsTextField.textProperty().bindBidirectional(viewModel.externalPlayerArgsProperty());
        // 路径变化后刷新识别结果，让用户确认按哪种播放器拼接参数
        viewModel.externalPlayerKindProperty().addListener((observable, oldValue, kind) -> {
            if (kind != null) {
                externalPlayerPathTile.setDescription(kind);
            }
        });
        if (viewModel.externalPlayerKindProperty().get() != null) {
            externalPlayerPathTile.setDescription(viewModel.externalPlayerKindProperty().get());
        }

        // 封面阴影：Spinner 0=关闭，>0=开启且值为阴影大小
        SpinnerValueFactory.IntegerSpinnerValueFactory effectFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 20,
                        viewModel.detailAlbumEffectModelProperty().get() ? (int) viewModel.detailAlbumEffectSizeProperty().get() : 0);
        detailAlbumEffectSpinner.setValueFactory(effectFactory);
        detailAlbumEffectSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            viewModel.detailAlbumEffectModelProperty().set(newVal != null && newVal > 0);
            viewModel.detailAlbumEffectSizeProperty().set(newVal != null ? newVal.doubleValue() : 0);
        });



        /*==========================Galgame相关=====================================*/
        imageTextField.textProperty().bindBidirectional(viewModel.galgameImageProperty());
        openGalgame.selectedProperty().bindBidirectional(viewModel.galgameModelProperty());
        openGalgameBg.selectedProperty().bindBidirectional(viewModel.galgameBgModelProperty());
        // 设置背景图片 Tile 仅在开启自定义图片时显示
        galgameBgImageTile.visibleProperty().bind(openGalgameBg.selectedProperty());
        galgameBgImageTile.managedProperty().bind(openGalgameBg.selectedProperty());
        openGalgameAnimation.selectedProperty().bindBidirectional(viewModel.galgameAnimationModelProperty());



        galgameBgSizeHeightTextField.textProperty().bindBidirectional(viewModel.galgameBgSizeHeightProperty());
        galgameBgSizeWidthTextField.textProperty().bindBidirectional(viewModel.galgameBgSizeWidthProperty());
        galgameBgSizeHeightTextField.setTextFormatter(new TextFormatter<>(change -> Pattern.matches("\\d*", change.getText()) ? change : null));
        galgameBgSizeWidthTextField.setTextFormatter(new TextFormatter<>(change -> Pattern.matches("\\d*", change.getText()) ? change : null));

        galgameSkinComboBox.setItems(viewModel.getGalgameSkins());
        galgameSkinComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(GalgameSkin galgameSkin) {
                return galgameSkin.getName();
            }

            @Override
            public GalgameSkin fromString(String s) {
                return null;
            }
        });

        galgameSkinComboBox.getSelectionModel().select(viewModel.getGalgameSkin());

        galgameSkinComboBox.getSelectionModel().selectedItemProperty().addListener((observableValue, galgameSkin, t1) -> viewModel.updateGalgameSkin(t1));
        /*==========================插件相关=====================================*/
        openProxy.selectedProperty().bindBidirectional(viewModel.proxyModelProperty());
        proxyHostTextField.textProperty().bindBidirectional(viewModel.proxyHostProperty());
        proxyPortTextField.textProperty().bindBidirectional(viewModel.proxyPortProperty());
        // 代理设置 Tile 仅在开启代理时显示
        proxyTile.visibleProperty().bind(openProxy.selectedProperty());
        proxyTile.managedProperty().bind(openProxy.selectedProperty());
        openSyncImageReDown.selectedProperty().bindBidirectional(viewModel.syncImageReDownModelProperty());
        sourcesUrlTextField.textProperty().bindBidirectional(viewModel.sourcesUrlProperty());
        saveCoverTypeRadio1.setSelected(!viewModel.isSaveCoverToAimFolderModel());
        saveCoverTypeRadio1.setOnAction(actionEvent -> viewModel.setSaveCoverToAimFolderModel(!saveCoverTypeRadio1.isSelected()));
        saveCoverTypeRadio2.selectedProperty().bindBidirectional(viewModel.saveCoverToAimFolderModelProperty());
        openSyncSkipAlready.selectedProperty().bindBidirectional(viewModel.syncSkipAlreadyModelProperty());
        openUserChineseTitle.selectedProperty().bindBidirectional(viewModel.userChineseTitleProperty());


        /*==========================缓存管理相关=====================================*/
        openCache.selectedProperty().bindBidirectional(viewModel.cacheEnableProperty());
        cacheMaxSizeTextField.setTextFormatter(new TextFormatter<>(change -> Pattern.matches("\\d*", change.getText()) ? change : null));
        cacheMaxSizeTextField.textProperty().bindBidirectional(viewModel.cacheMaxSizeMBProperty(), new javafx.util.StringConverter<Number>() {
                    @Override
                    public String toString(Number n) { return n == null ? "" : n.toString(); }
                    @Override
                    public Number fromString(String s) {
                        if (s == null || s.isBlank()) return 500;
                        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 500; }
                    }
                });
        cacheInfoLabel.textProperty().bind(viewModel.cacheInfoProperty());
        clearCacheBtn.setOnAction(actionEvent -> viewModel.clearCache());


        /*==========================黑名单相关=====================================*/

        textBlacklistArea.textProperty().bindBidirectional(viewModel.textBlacklistRowProperty());

        /*==========================AI 翻译相关=====================================*/
        aiTranslationEndpointTextField.textProperty().bindBidirectional(viewModel.aiTranslationEndpointProperty());
        aiTranslationModelTextField.textProperty().bindBidirectional(viewModel.aiTranslationModelProperty());
        aiTranslationApiKeyPasswordField.textProperty().bindBidirectional(viewModel.aiTranslationApiKeyProperty());
        aiTranslationApiKeyPasswordField.promptTextProperty().bind(
                Bindings.when(viewModel.aiTranslationApiKeySetProperty())
                        .then("已保存，留空表示不修改")
                        .otherwise("输入 API Key"));
        aiTranslationPromptArea.textProperty().bindBidirectional(viewModel.aiTranslationPromptProperty());
        bindPositiveInteger(aiTranslationBatchSizeTextField, viewModel.aiTranslationBatchSizeProperty(), 1, 50);
        bindPositiveInteger(aiTranslationConcurrencyTextField, viewModel.aiTranslationMaxConcurrencyProperty(), 1, 8);
        bindPositiveInteger(aiTranslationTimeoutTextField, viewModel.aiTranslationTimeoutSecondsProperty(), 5, 300);
        aiTranslationPromptPresetComboBox.getItems().addAll("通用保守翻译", "保留人名与社团", "仅清理与转换");
        aiTranslationPromptPresetComboBox.setValue(viewModel.aiTranslationPromptPresetProperty().get());
        aiTranslationPromptPresetComboBox.valueProperty().bindBidirectional(viewModel.aiTranslationPromptPresetProperty());
        aiTranslationPromptPresetComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.equals(oldValue)) {
                aiTranslationPromptArea.setText(promptForPreset(newValue));
            }
        });



        /*==========================关于相关=====================================*/
        githubLink.setOnAction(actionEvent -> {
            try {
                Desktop.getDesktop().browse(new URI("https://github.com/leck995/Amsr-Player"));
            } catch (IOException | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        });

        aboutTile.setDescription("版本: " + Config.version + "  作者: Leck");

        deferInactiveTabContent();
    }

    /**
     * 未选中 Tab 的内容在首次选中时才挂入场景，避免页面加载时一次性完成所有 Tab 的皮肤与样式初始化。
     */
    private void deferInactiveTabContent() {
        Tab initial = tabPane.getSelectionModel().getSelectedItem();
        if (initial == null && !tabPane.getTabs().isEmpty()) {
            initial = tabPane.getTabs().getFirst();
        }
        for (Tab tab : tabPane.getTabs()) {
            Node content = tab.getContent();
            if (content == null || tab == initial) {
                continue;
            }
            tab.setContent(null);
            tab.setOnSelectionChanged(event -> {
                if (tab.isSelected() && tab.getContent() == null) {
                    tab.setContent(content);
                }
            });
        }
    }

    /**
     * 允许 Spinner 手动输入：空输入回退为当前值，非法输入在提交时恢复。
     */
    private <T> void makeSpinnerEditable(Spinner<T> spinner) {
        spinner.setEditable(true);
        SpinnerValueFactory<T> factory = spinner.getValueFactory();
        StringConverter<T> converter = factory.getConverter();
        factory.setConverter(new StringConverter<>() {
            @Override
            public String toString(T value) {
                return converter.toString(value);
            }

            @Override
            public T fromString(String text) {
                T value = converter.fromString(text);
                return value == null ? factory.getValue() : value;
            }
        });
        spinner.getEditor().setOnAction(event -> {
            try {
                spinner.commitValue();
            } catch (RuntimeException e) {
                spinner.cancelEdit();
            }
        });
    }

    /**
     * 只允许数字输入，并把文本值钳制到 {@code [min, max]} 后写回属性；无法解析的文本按 {@code min} 处理。
     *
     * @param field    输入框
     * @param property 目标属性
     * @param min      下限，同时是解析失败时的回退值
     * @param max      上限
     */
    private void bindPositiveInteger(TextField field, javafx.beans.property.IntegerProperty property, int min, int max) {
        field.setTextFormatter(new TextFormatter<>(change -> Pattern.matches("\\d*", change.getText()) ? change : null));
        field.textProperty().bindBidirectional(property, new javafx.util.StringConverter<Number>() {
            @Override
            public String toString(Number value) {
                return value == null ? String.valueOf(min) : String.valueOf(value.intValue());
            }

            @Override
            public Number fromString(String value) {
                try {
                    return Math.max(min, Math.min(max, Integer.parseInt(value)));
                } catch (NumberFormatException ignored) {
                    return min;
                }
            }
        });
    }

    /**
     * 按预设名称在默认提示词后追加约束说明，未知预设返回默认提示词。
     *
     * @param preset 下拉框选中的预设名称
     * @return 可直接写入提示词输入框的完整文本
     */
    private static String promptForPreset(String preset) {
        String base = cn.tealc.asmr.config.AiTranslationSettings.DEFAULT_PROMPT;
        if ("保留人名与社团".equals(preset)) {
            return base + "\n角色名、社团名、作者名和系列名一律优先保留原文；仅翻译明确的通用描述。";
        }
        if ("仅清理与转换".equals(preset)) {
            return base + "\n不做推测性语义翻译；仅转换明确的常见词，并规范空格、全半角和分隔符。";
        }
        return base;
    }




    /**
     * 二次确认后恢复默认设置：模型内部会立即保存，因此这里只需同步界面控件。
     */
    private void resetToDefaults(ActionEvent event) {
        Alerts.danger().title("恢复默认设置")
                .message("确定将所有设置恢复为默认值吗？当前自定义的选项将被覆盖并立即保存。")
                .ok("恢复", handle -> {
                    viewModel.resetToDefaults();
                    refreshControlsAfterReset();
                    EventBus.publish(NotificationKey.MESSAGE,
                            new MessageInfo(MessageType.SUCCESS, "已恢复默认设置"));
                })
                .cancel().show();
    }

    /**
     * 重置后同步未与配置双向绑定的控件。
     */
    private void refreshControlsAfterReset() {
        gridListCountSpinner.getValueFactory().setValue(viewModel.getGridListCount());
        gridColumnsSpinner.getValueFactory().setValue(Config.setting.getGridColumns());
        detailAlbumRadiusSpinner.getValueFactory().setValue(
                viewModel.isDetailAlbumRadiusModel() ? (int) viewModel.getDetailAlbumRadiusSize() : 0);
        detailAlbumEffectSpinner.getValueFactory().setValue(
                viewModel.isDetailAlbumEffectModel() ? (int) viewModel.getDetailAlbumEffectSize() : 0);
        lrcFontColorPicker.setValue(Color.web(viewModel.getDesktopLRCFontColor()));
        lrcBorderColorPicker.setValue(Color.web(viewModel.getDesktopLRCBorderColor()));
        lrcStrokeColorPicker.setValue(Color.web(viewModel.getDesktopLRCStrokeColor()));
        galgameSkinComboBox.getSelectionModel().select(viewModel.getGalgameSkin());
        openGridItemThumb.setSelected(!viewModel.isGridItemDetailModel());
        detailLrcAlignmentLeftRadio.setSelected(!viewModel.isDetailLrcAlignment());
        saveCoverTypeRadio1.setSelected(!viewModel.isSaveCoverToAimFolderModel());
    }

    /**
     * 保存全部设置。
     * <p>
     * 开启启动密码却未填写且此前没有已存密码时拒绝保存并提示，避免出现“开关开启但无密码”的状态；
     * 其余字段交由 {@link SettingViewModel#save()} 落盘。
     */
    void save(ActionEvent event) {
        boolean passwordTyped = passwordField.getText() != null && !passwordField.getText().isBlank();
        if (openPassword.isSelected() && !passwordTyped && !viewModel.hasStartPassword()) {
            Alerts.warning().message("启动密码未设置，请填写密码或关闭启动密码").cancel("确定").show();
            return;
        }
        viewModel.save();
        EventBus.publish(NotificationKey.MESSAGE,
                new MessageInfo(MessageType.SUCCESS, "设置已保存"));
    }

    /**
     * 请求清除已保存的 AI 翻译 API Key，真正的删除在点击“保存”时由视图模型执行。
     */
    @FXML
    void clearAiTranslationApiKey(ActionEvent event) {
        viewModel.clearAiTranslationApiKey();
    }







    /**
     * 选择外部播放器可执行文件：仅 Windows 附加 {@code *.exe} 过滤，其他平台不加过滤以免选不到文件；
     * 选中后写回路径并刷新识别出的播放器类型。
     */
    @FXML
    void setMediaPlayer(ActionEvent event){
        FileChooser fileChooser=new FileChooser();
        fileChooser.setTitle("选择目标播放器");
        // 非 Windows 平台没有 exe，不加扩展名过滤，避免选不到可执行文件
        if (System.getProperty("os.name", "").toLowerCase().contains("windows")) {
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("可执行程序", "*.exe"),
                    new FileChooser.ExtensionFilter("全部文件", "*.*"));
        }
        File file = fileChooser.showOpenDialog(Main.window);
        if (file != null && file.exists()) {
            viewModel.setExternalPlayerPath(file.getAbsolutePath());
            viewModel.refreshExternalPlayerKind();
        }
    }

    /**
     * 清空自定义参数模板，恢复按播放器类型自动拼接。
     */
    @FXML
    void resetExternalPlayerArgs(ActionEvent event) {
        viewModel.setExternalPlayerArgs("");
    }

    /**
     * 选择图片作为 Galgame 默认背景：把路径回填输入框，并复制覆盖到应用的背景文件，
     * 目标目录不存在时先创建；复制失败抛出异常。
     */
    @FXML
    void importImage(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择目标图片");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("图片", "*.png", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("全部文件", "*.*"));
        File file = fileChooser.showOpenDialog(Main.window);
        if (file != null && file.exists()) {
            imageTextField.setText(file.getPath());
            File bgFile = AppPaths.backgroundFile();
            try {
                Files.createDirectories(bgFile.getParentFile().toPath());
                if (bgFile.exists()){
                    bgFile.delete();
                }
                Files.copy(file.toPath(),bgFile.toPath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }
    }

    /**
     * 在系统文件管理器中打开应用数据目录，便于备份或迁移 data.db。
     */
    @FXML
    void openDataDir(ActionEvent event){
        try {
            FileUtils.openInExplore(AppPaths.dataDir());
        } catch (IOException e) {
            EventBus.publish(NotificationKey.MESSAGE,
                    new MessageInfo(MessageType.WARNING, "打开数据目录失败：" + e.getMessage()));
        }
    }

    /**
     * 展示开源许可：读取 classpath 下的 {@code license.txt}，放进一个独立的 {@link Stage} 显示。
     * 这是设置页唯一不走 {@link cn.tealc.asmr.ui.dialog.Dialogs} 的弹窗，因此没有遮罩与统一按钮区；
     * 资源缺失时抛出异常。
     */
    @FXML
    void showLicense(ActionEvent event){
        Stage stage=new Stage();
        TextArea textArea=new TextArea();
        StringBuilder row = new StringBuilder();
        try (InputStream in = Main.class.getResourceAsStream("/cn/tealc/asmr/other/license.txt")) {
            if (in == null) {
                throw new IllegalStateException("找不到开源许可文件");
            }
            try (BufferedReader br=new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))){
                String line;
                while ((line = br.readLine()) != null) {
                    row.append(line);
                    row.append(System.lineSeparator());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        textArea.setText(row.toString());
        StackPane stackPane=new StackPane(textArea);
        stage.setScene(new Scene(stackPane));
        stage.setTitle("开源许可");
        stage.show();
    }

    /**
     * 批量删除所有作品的主封面与缩略封面文件：确认后在虚拟线程上逐个删除，
     * 结果（删除数量或失败）以消息播报。只删磁盘上的封面文件，不改动数据库记录。
     */
    @FXML
    void deleteCover(ActionEvent event){
        Alerts.warning().title("提醒")
                .message("确认删除所有作品的主封面和缩略封面吗？")
                .ok("确定", handle -> {
                    List<AsmrWork> allWorks = workService.getAllWorks();
                    Task<Integer> task = new Task<>() {
                        @Override
                        protected Integer call() {
                            return allWorks.stream().mapToInt(fileOperationService::deleteCoverFiles).sum();
                        }
                    };
                    task.setOnSucceeded(e -> EventBus.publish(NotificationKey.MESSAGE,
                            new MessageInfo(MessageType.SUCCESS, "已删除 " + task.getValue() + " 个封面文件")));
                    task.setOnFailed(e -> EventBus.publish(NotificationKey.MESSAGE,
                            new MessageInfo(MessageType.ERROR, "批量删除封面失败")));
                    Thread.ofVirtual().start(task);
                })
                .cancel()
                .show();
    }

    /**
     * 测试存储源地址连通性：按钮图标转圈，在守护线程上建立连接，按结果发送成功/失败消息。
     * <p>
     * 仅接受 {@code https://} 开头的地址，否则直接提示先正确填写网址；开启代理时经 HTTP 代理发起，
     * 连接超时 5 秒。只判断能否取到响应码，不校验返回内容。
     *
     * @param event 事件源必须是正在转圈的测试按钮，用于取回其图标做动画
     */
    @FXML
    void checkUrl(ActionEvent event){

        if (Config.setting.sourcesUrl.get() != null && Config.setting.sourcesUrl.get().startsWith("https://")){
            Button checkBtn= (Button) event.getSource();
            RotateTransition rotateTransition=new RotateTransition(Duration.millis(1000),checkBtn.getGraphic());
            rotateTransition.setByAngle(360);
            rotateTransition.setCycleCount(Animation.INDEFINITE);
            rotateTransition.setInterpolator(Interpolator.LINEAR);
            rotateTransition.play();
            Task<Integer> task=new Task<Integer>() {
                @Override
                protected Integer call() throws Exception {
                    try {
                        URL url=new URL(Config.setting.sourcesUrl.get());
                        Proxy proxy;
                        HttpURLConnection connection;
                        if (Config.setting.proxyModel.get()){
                            proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(Config.setting.proxyHost.get(), Integer.parseInt(Config.setting.proxyPort.get())));
                            connection= (HttpURLConnection) url.openConnection(proxy);
                        }else{
                            connection=(HttpURLConnection) url.openConnection();
                        }
                        connection.setConnectTimeout(5000);
                        int responseCode = connection.getResponseCode();
                        System.out.println("当前状态码："+responseCode);

                        return 1;
                    }catch (IOException e){
                        return 0;
                    }
                }
            };
            task.valueProperty().addListener((observableValue, integer, t1) -> {
                if (t1 == 1){
                    EventBus.publish(NotificationKey.MESSAGE, new MessageInfo(MessageType.SUCCESS,"连接可用"));
                }else {
                    EventBus.publish(NotificationKey.MESSAGE, new MessageInfo(MessageType.ERROR,"连接不可用"));

                }
                rotateTransition.stop();
            });
            Thread thread=new Thread(task);
            thread.setDaemon(true);
            thread.start();
        }else{
            EventBus.publish(NotificationKey.MESSAGE, new MessageInfo(MessageType.WARNING,"请先正确填写网址，再进行测试"));
        }

    }


}
