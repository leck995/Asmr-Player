package cn.tealc.asmr.ui.galgame;

import cn.tealc.asmr.eventbus.EventBus;
import cn.tealc.asmr.Main;
import cn.tealc.asmr.factory.NodeFactory;
import cn.tealc.asmr.factory.ViewFactory;
import cn.tealc.asmr.model.BackgroundRef;
import cn.tealc.asmr.model.SubtitleCue;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.model.message.MessageType;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.subtitle.SubtitleDisplayService;
import cn.tealc.asmr.subtitle.SubtitleTiming;
import cn.tealc.asmr.ui.dialog.Dialogs;
import cn.tealc.asmr.ui.player.AudioChannelView;
import cn.tealc.asmr.ui.player.AudioChannelViewModel;
import cn.tealc.asmr.ui.player.SubtitleSyncDialog;
import cn.tealc.asmr.utils.CssLoader;
import cn.tealc.asmr.utils.TimeFormatUtils;
import cn.tealc.asmr.utils.URLUtils;
import com.jfoenix.controls.JFXDialog;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXSlider;
import cn.tealc.asmr.mvvm.FluentViewLoader;
import cn.tealc.asmr.mvvm.InjectViewModel;
import cn.tealc.asmr.mvvm.JavaView;
import cn.tealc.asmr.mvvm.ViewModel;
import cn.tealc.asmr.mvvm.ViewTuple;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.util.Callback;
import javafx.util.Duration;
import org.controlsfx.control.HiddenSidesPane;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignW;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.nio.file.Paths;
import java.util.*;

/**
 * Galgame（视觉小说式）播放界面：全屏背景图 + 滚动字幕 + 悬停显隐的标题栏与侧边栏。
 * <p>
 * 布局与样式来自当前皮肤：{@link cn.tealc.asmr.model.galgame.GalgameSkinRegistry} 内置 classic、
 * simple 两套皮肤，各提供一份 {@code galgame.fxml} 与 {@code galgame.css}，本类按
 * {@code Config.setting.getGalgameSkin()} 取资源路径加载，资源缺失直接抛异常而不回退。
 * 控件不是 {@code @FXML} 注入，而是在加载出的根节点上用 {@code lookup("#xxx")} 按 id 查找，
 * 换皮肤必须保留这些 id。
 * <p>
 * 子界面的承载方式各不相同：存档列表交给 {@link Dialogs} 弹窗，音效面板同样使用 {@link Dialogs}，
 * 音量弹层复用 {@code ContextMenu}，文本字幕则是挂在本节点内的
 * {@code JFXDialog}（{@link #showLrcDialog()}），不属于 {@link Dialogs} 弹窗体系。
 * 保存存档时对本面板截图，需临时隐藏标题栏按钮，见 {@link #headerTrailingBox}。
 *
 * @author Leck
 */
public class GalgameDetailView extends StackPane implements JavaView<GalgameDetailViewModel>, Initializable {
    @InjectViewModel
    private GalgameDetailViewModel viewModel;
    private StackPane bgPane;
    private ContextMenu soundPopup;
    private SimpleDoubleProperty currentTime;
    /** 拖动进度条期间为 true，用于让进度条数值不被播放进度回调覆盖 */
    public Boolean isMouseMove = false;
    private JFXDialog lrcDialog;
    private String asmrWorkFolder;
    /** 内嵌标题栏右侧按钮容器，截图时需临时隐藏 */
    private HBox headerTrailingBox;

    private WeakReference<ImageView> imageViewRef;


    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        createPane();
    }

    /**
     * 按当前皮肤装配整个界面：加载布局、逐个绑定控件、拼出背景侧栏与原生标题栏，并套用皮肤 CSS。
     * <p>
     * 布局与样式资源由皮肤给出，任一资源缺失都会抛出 {@link IllegalStateException}；
     * 标注为可选的控件（背景切换、循环、乱序、文本字幕按钮等）查找不到时跳过，
     * 其余控件视为皮肤必须提供，缺失会在使用处直接失败。
     */
    private void createPane(){
        URL fxmlResource = getClass().getResource(Config.setting.getGalgameSkin().getFxml());
        if (fxmlResource == null) {
            throw new IllegalStateException("内置 Galgame 皮肤布局不存在：" + Config.setting.getGalgameSkin().getFxml());
        }
        Parent parent;
        try {
            FXMLLoader galgameFL=new FXMLLoader(fxmlResource);
            parent=galgameFL.load();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }




        AnchorPane mainPane= (AnchorPane) parent.lookup("#mainPane");

        Label text= (Label) parent.lookup("#text");
        text.textProperty().bind(viewModel.currentLrcProperty());


        /*
         * 时间进度条
         * */
        JFXSlider songSlider= (JFXSlider) parent.lookup("#songSlider");
        songSlider.setMin(0);
        songSlider.maxProperty().bind(viewModel.totalTimeProperty());
        currentTime = new SimpleDoubleProperty();
        currentTime.bind(viewModel.currentTimeProperty());
        currentTime.addListener((obs,oldValue,newValue)->{
            if (!isMouseMove) {
                songSlider.setValue(newValue.doubleValue());
            }
        });

        if (currentTime.getValue() != null) songSlider.setValue(currentTime.getValue());
        //设置播放条显示格式
        songSlider.setValueFactory(slider ->
                Bindings.createStringBinding(() -> (
                        TimeFormatUtils.formatToClock(slider.getValue())
                ) , slider.valueProperty()));
        /*进度条按下监听*/
        songSlider.setOnMousePressed(mouseEvent -> isMouseMove = true);
        /*进度条释放监听，跳转到指定时间*/
        songSlider.setOnMouseReleased(mouseEvent -> {
            if (!viewModel.isReady())
                return;
            isMouseMove = false;
            viewModel.seek(Duration.seconds(songSlider.getValue()));
        });


        /*
         * 时间，信息文本
         * */
        Label currentTimeLabel= (Label) parent.lookup("#currentTimeLabel");
        currentTimeLabel.getStyleClass().add("galgame-time");
        currentTimeLabel.textProperty().bind(Bindings
                .createStringBinding(() -> (
                                TimeFormatUtils.formatToClock(songSlider.getValue())
                ) , songSlider.valueProperty()));

        Label totalTimeLabel=(Label) parent.lookup("#totalTimeLabel");
        totalTimeLabel.getStyleClass().add("galgame-time");
        totalTimeLabel.textProperty().bind(Bindings
                .createStringBinding(() -> (
                                TimeFormatUtils.formatToClock(viewModel.totalTimeProperty().get())
                ) ,viewModel.totalTimeProperty()));

        Label titleLabel=(Label) parent.lookup("#titleLabel");
        if (titleLabel!=null){
            titleLabel.textProperty().bind(viewModel.titleProperty());
        }

        Label nameLabel=(Label) parent.lookup("#nameLabel");
        if (nameLabel == null){
            nameLabel=new Label();
        }
        nameLabel.textProperty().bind(viewModel.artistProperty());




        /*
        * 功能按钮
        * */
        Button saveBtn= (Button) parent.lookup("#saveBtn");
        saveBtn.setTooltip(new Tooltip("保存"));
        Button loadBtn=(Button) parent.lookup("#loadBtn");
        loadBtn.setTooltip(new Tooltip("读取"));
        loadBtn.setOnAction(actionEvent -> {
            ViewTuple<GalgameLoadView, GalgameLoadViewModel> view = ViewFactory.galgameLoadView();
            Dialogs.show(view.getCodeBehind());
        });

        ToggleButton playBtn= (ToggleButton) parent.lookup("#playBtn");
        playBtn.setTooltip(new Tooltip("播放"));
        playBtn.selectedProperty().bindBidirectional(viewModel.playingProperty());
        playBtn.setOnAction(actionEvent -> {
            if (!viewModel.isReady()){
                playBtn.setSelected(false);
            }
        });

        Button preBtn= (Button) parent.lookup("#preBtn");
        preBtn.setTooltip(new Tooltip("上一曲"));
        preBtn.setOnAction(actionEvent -> viewModel.pre());

        Button nextBtn=(Button) parent.lookup("#nextBtn");
        nextBtn.setTooltip(new Tooltip("下一曲"));
        nextBtn.setOnAction(actionEvent -> viewModel.next());

        ToggleButton muteBtn=(ToggleButton) parent.lookup("#muteBtn");
        muteBtn.setTooltip(new Tooltip("音量"));
        muteBtn.selectedProperty().bind(viewModel.muteProperty());
        muteBtn.addEventFilter(MouseEvent.MOUSE_PRESSED, (EventHandler<Event>) event -> {
            if (soundPopup == null) {
                soundPopup = NodeFactory.createVolumePopupMenu();
            }
            NodeFactory.showVolumePopup(soundPopup, muteBtn);
            event.consume();
        });



        ToggleButton imageBtn=(ToggleButton) parent.lookup("#imageBtn");
        if (imageBtn != null){
            imageBtn.setTooltip(new Tooltip("悬浮背景"));
            imageBtn.setOnAction(actionEvent -> {
                if (imageBtn.isSelected()){
                    showImageView();
                }else {
                    hideImageView();
                }
            });
        }

        ToggleButton loopBtn=(ToggleButton) parent.lookup("#loopBtn");
        if (loopBtn != null){
            loopBtn.setTooltip(new Tooltip("循环播放"));
            loopBtn.selectedProperty().bindBidirectional(viewModel.loopProperty());
        }

        ToggleButton disOrderBtn=(ToggleButton) parent.lookup("#disOrderBtn");
        if (disOrderBtn != null){
            disOrderBtn.setTooltip(new Tooltip("乱序播放"));
            disOrderBtn.selectedProperty().bindBidirectional(viewModel.disorderProperty());
        }
        ToggleButton lrcBtn=(ToggleButton) parent.lookup("#lrcBtn");
        lrcBtn.setTooltip(new Tooltip("桌面歌词"));
        lrcBtn.selectedProperty().bindBidirectional(viewModel.desktopLrcProperty());

        Button listBtn= (Button) parent.lookup("#listBtn");
        listBtn.setTooltip(new Tooltip("播放列表"));
        listBtn.setOnAction(actionEvent -> {
            EventBus.publish(NotificationKey.MAIN_PLAY_LIST,true);
        });
        Button backBtn=(Button) parent.lookup("#backBtn");
        backBtn.setTooltip(new Tooltip("隐藏"));

        Button closeBtn=(Button) parent.lookup("#closeBtn");
        closeBtn.setTooltip(new Tooltip("返回"));
        closeBtn.setOnAction(actionEvent -> {
            EventBus.publish("MAIN_ROOT_REMOVE");
        });

        Button changeModelBtn=(Button) parent.lookup("#changeModelBtn");//切换视图按钮,切换到传统播放界面
        changeModelBtn.setTooltip(new Tooltip("传统模式"));
        changeModelBtn.setOnAction(actionEvent -> {
            EventBus.publish("TO_PLAY",0);
        });

        Button lrcHistoryBtn=(Button) parent.lookup("#lrcHistoryBtn");//歌词文本按钮
        if (lrcHistoryBtn !=null){
            lrcHistoryBtn.setTooltip(new Tooltip("文本字幕"));
            lrcHistoryBtn.setOnAction(actionEvent -> {
                if (viewModel.isReady())
                    showLrcDialog();
            });
        }
        Button audioEffectBtn=(Button) parent.lookup("#audioEffectBtn");
        audioEffectBtn.setTooltip(new Tooltip("音效"));
        audioEffectBtn.setOnAction(actionEvent -> {
            ViewTuple<AudioChannelView, AudioChannelViewModel> viewTuple = FluentViewLoader.fxmlView(AudioChannelView.class).load();
            Dialogs.show(viewTuple.getCodeBehind());
        });


        Pane messagePane= (Pane) parent.lookup("#messagePane");

        messagePane.setBackground(new Background(new BackgroundFill(new LinearGradient(
                1.0,0.0,1.0,1.0,true,CycleMethod.NO_CYCLE,
                new Stop(0.0,Color.TRANSPARENT),
                new Stop(1.0,Color.web("eee7e7"))), null, null)));

        backBtn.setOnAction(actionEvent -> {
            messagePane.setVisible(false);
        });



        /*
         * 背景图片预览界面
         * */
        Node bgImagePane;
        if (Objects.nonNull(viewModel.getPlayer().getPlayingWork()) && Objects.nonNull(viewModel.getPlayer().getPlayingWork().getFilepath())){
            if(viewModel.getPlayer().getPlayingMusic().isOnline()){
                bgImagePane=createBgThumbPane();
            }else {
                asmrWorkFolder=URLUtils.getFolderParentPath(viewModel.getPlayer().getPlayingWork().getFilepath());
                bgImagePane=createBgThumbPane();
            }
        }else{
            asmrWorkFolder=null;
            bgImagePane=createBgThumbPane();
        }




        /*
        * 标题栏相关：使用原生 HeaderBar（NO_LEFT 风格，右侧按钮悬停显隐）
        * */
        HeaderBar headerBar = new HeaderBar();
        headerBar.getStyleClass().addAll("headbar", "embed-headbar");
        headerTrailingBox = new HBox();
        headerTrailingBox.getStyleClass().add("trailing");
        headerTrailingBox.setOpacity(0);
        headerTrailingBox.setOnMouseExited(mouseEvent -> headerTrailingBox.setOpacity(0));
        headerTrailingBox.setOnMouseEntered(mouseEvent -> headerTrailingBox.setOpacity(1));
        // 系统按钮：全屏/最小化/最大化/关闭（从左到右）
        Button fullBtn = new Button(null, new FontIcon(MaterialDesignF.FULLSCREEN));
        Button minBtn = new Button(null, new FontIcon(MaterialDesignW.WINDOW_MINIMIZE));
        Button maxBtn = new Button(null, new FontIcon(MaterialDesignW.WINDOW_MAXIMIZE));
        Button headerCloseBtn = new Button(null, new FontIcon(MaterialDesignC.CLOSE));
        HeaderBar.setButtonType(minBtn, HeaderButtonType.ICONIFY);
        HeaderBar.setButtonType(maxBtn, HeaderButtonType.MAXIMIZE);
        fullBtn.setOnAction(mouseEvent -> Main.window.setFullScreen(!Main.window.isFullScreen()));
        headerCloseBtn.getStyleClass().add("close-btn");
        // 最大化状态切换图标（最大化↔还原）
        Main.window.maximizedProperty().addListener((obs, old, isMax) ->
                maxBtn.setGraphic(new FontIcon(isMax
                        ? MaterialDesignW.WINDOW_RESTORE
                        : MaterialDesignW.WINDOW_MAXIMIZE)));
        // 全屏状态切换图标（全屏↔退出全屏）
        Main.window.fullScreenProperty().addListener((obs, old, isFull) ->
                fullBtn.setGraphic(new FontIcon(isFull
                        ? MaterialDesignF.FULLSCREEN_EXIT
                        : MaterialDesignF.FULLSCREEN)));
        headerCloseBtn.setOnAction(mouseEvent -> {
            EventBus.publish(NotificationKey.EXIT);
        });
        Button subtitleSyncBtn = new Button(null, new FontIcon(Material2OutlinedMZ.TUNE));
        subtitleSyncBtn.getStyleClass().add("subtitle-sync-header-btn");
        subtitleSyncBtn.setTooltip(new Tooltip("字幕设置"));
        subtitleSyncBtn.setOnAction(event -> SubtitleSyncDialog.show());

        HBox systemBox = new HBox(subtitleSyncBtn, fullBtn, minBtn, maxBtn, headerCloseBtn);
        systemBox.getStyleClass().add("system-func");
        headerTrailingBox.getChildren().add(systemBox);
        headerBar.setRight(headerTrailingBox);

        BorderPane border = new BorderPane();
        border.setTop(headerBar);
        border.setCenter(mainPane);

        HiddenSidesPane sidesPane=new HiddenSidesPane();
        sidesPane.setContent(border);
        sidesPane.setLeft(bgImagePane);


        /*
        * 背景图片
        * */
        bgPane=new StackPane();
        bgPane.backgroundProperty().bind(
                Bindings.createObjectBinding(() -> {
                            if (viewModel.getBackgroundImage() != null){
                                return new Background(new BackgroundImage(viewModel.getBackgroundImage(),
                                        BackgroundRepeat.NO_REPEAT,
                                        BackgroundRepeat.NO_REPEAT,
                                        BackgroundPosition.CENTER,
                                        new BackgroundSize(1.0,1.0,true,true,true,true)));
                            }else {
                                return null;
                            }

                            },
                        viewModel.backgroundImageProperty())
        );

        mainPane.setOnMouseClicked(click ->{

            if (click.getButton() == MouseButton.PRIMARY){
                if (click.getClickCount() ==2){
                    viewModel.changeBackground();
                }
                if (!messagePane.isVisible()){
                    messagePane.setVisible(true);
                }
            }
            if (click.getButton() == MouseButton.SECONDARY){
                messagePane.setVisible(false);
            }


        });

        getChildren().addAll(bgPane,sidesPane);

        URL cssResource = getClass().getResource(Config.setting.getGalgameSkin().getCss());
        if (cssResource == null) {
            throw new IllegalStateException("内置 Galgame 皮肤样式不存在：" + Config.setting.getGalgameSkin().getCss());
        }
        getStylesheets().add(cssResource.toExternalForm());



        setOnScroll(scroll ->{
            if (viewModel.isReady() && scroll.getDeltaY() > 0)
                showLrcDialog();
        });


        /*==========================动画效果设置==============================*/

        if (Config.setting.galgameAnimationModel.get()){
            ImageView dialogImage= (ImageView) parent.lookup("#dialogImage");
            if (dialogImage != null){
                RotateTransition rt = new RotateTransition(Duration.millis(3000), dialogImage); //雪花动画
                rt.setByAngle(360);
                rt.setCycleCount(Animation.INDEFINITE);
                rt.setInterpolator(Interpolator.LINEAR);
                rt.play();
            }
        }



        Timeline timeline=new Timeline();
        timeline.getKeyFrames().add(new KeyFrame(Duration.millis(300),new KeyValue(saveBtn.rotateProperty(),45)));
        timeline.getKeyFrames().add(new KeyFrame(Duration.millis(600),new KeyValue(saveBtn.rotateProperty(),-45)));
        timeline.getKeyFrames().add(new KeyFrame(Duration.millis(900),new KeyValue(saveBtn.rotateProperty(),0)));
        // 保存存档：先隐藏标题栏按钮，再对整块界面截图交给视图模型落库，截完恢复按钮可见
        saveBtn.setOnAction(actionEvent -> {
            if (viewModel.isReady()){
                timeline.play();
                headerTrailingBox.setOpacity(0.0);
                SnapshotParameters params = new SnapshotParameters();
                params.setFill(Color.TRANSPARENT);// 设置透明背景或其他颜色
                WritableImage tempImage = snapshot(params,null);
                viewModel.saveData(tempImage);
                headerTrailingBox.setOpacity(1.0);
            }
        });





        LinearGradient paint = new LinearGradient(
                0.0, 0.0, 1.0, 0.0, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, new Color(0.98, 0.78, 0.83, 1.0)),
                new Stop(1.0, new Color(0.72, 0.6, 0.57, 1.0)));
        setBackground(new Background(new BackgroundFill(paint,null,null)));

    }

    /**
     * 构建左侧背景缩略图栏：每个候选背景一格，点击即切换到该背景。
     * <p>
     * 缩略图在虚拟线程上按需解码（240x180），完成后回到 JavaFX 线程回填；
     * 背景列表变化时整体重建，首次构建与列表监听共用同一段逻辑。
     */
    private Node createBgThumbPane(){
        Label title=new Label("设置背景图片");
        title.setAlignment(Pos.CENTER);
        VBox bgThumbPane=new VBox();
        bgThumbPane.setSpacing(15);
        Runnable rebuild = () -> {
            bgThumbPane.getChildren().clear();
            for (BackgroundRef ref : viewModel.getBackgroundRefs()) {
                ImageView thumbBg = new ImageView();
                thumbBg.setFitWidth(240);
                thumbBg.setFitHeight(180);
                thumbBg.setPreserveRatio(true);
                thumbBg.setOnMouseClicked(mouseEvent -> viewModel.changeBackground(ref));
                bgThumbPane.getChildren().add(thumbBg);
                Thread.startVirtualThread(() -> {
                    Image image = viewModel.loadBackgroundImage(ref, 240, 180);
                    if (image != null) {
                        Platform.runLater(() -> thumbBg.setImage(image));
                    }
                });
            }
        };
        viewModel.getBackgroundRefs().addListener((ListChangeListener<BackgroundRef>) change -> rebuild.run());
        rebuild.run();


        ScrollPane scrollPane=new ScrollPane(bgThumbPane);
        scrollPane.setOnScroll(scrollEvent -> scrollEvent.consume());
        scrollPane.setOnMouseClicked(mouseEvent -> mouseEvent.consume());
        VBox vBox=new VBox(title,scrollPane);
        vBox.getStyleClass().add("galgame-bg-thumb");
        return vBox;
    }





    /**
     * 悬浮显示当前背景原图：尺寸跟随窗口（各边留 10px），节点用弱引用缓存复用，避免重复构建。
     * <p>
     * 显示期间隐藏 {@code bgPane}，避免背景与图片叠加显示。
     */
    private void showImageView(){
        if (imageViewRef == null || imageViewRef.get() == null){
            ImageView imageView=new ImageView();
            imageView.setPreserveRatio(true);
            imageView.fitWidthProperty().bind(Main.window.widthProperty().add(-10));
            imageView.fitHeightProperty().bind(Main.window.heightProperty().add(-10));
            imageView.imageProperty().bind(viewModel.backgroundImageProperty());
            imageViewRef=new WeakReference<>(imageView);
            getChildren().add(1,imageView);
        }else {
            ImageView imageView = imageViewRef.get();
            assert imageView != null;
            imageView.fitWidthProperty().bind(Main.window.widthProperty().add(-10));
            imageView.fitHeightProperty().bind(Main.window.heightProperty().add(-10));
            imageView.imageProperty().bind(viewModel.backgroundImageProperty());
            getChildren().add(1,imageView);
        }
        bgPane.setVisible(false);
    }

    /**
     * 移除悬浮背景图：解除尺寸与图片绑定并清空图片对象以释放解码结果，随后恢复 {@code bgPane}。
     */
    private void hideImageView(){
        ImageView imageView = imageViewRef.get();
        if (imageView!= null){
            imageView.imageProperty().unbind();
            imageView.fitWidthProperty().unbind();
            imageView.fitHeightProperty().unbind();
            imageView.setImage(null);
            getChildren().remove(imageView);
            bgPane.setVisible(true);
        }
    }

    /**
     * 打开文本字幕浮层：列出全部字幕行（含译文），点击行内按钮跳转到该行的起始时间。
     * <p>
     * 浮层是挂在本节点上的 {@code JFXDialog}，不属于 {@link Dialogs} 弹窗体系，因此不受
     * 遮罩点击关闭等统一约定约束；同一实例只创建一次，已存在时直接返回。
     * 打开时滚动并选中当前行，关闭时移除字幕显示模式的监听并清空容器。
     */
    public  void showLrcDialog(){
        if (lrcDialog !=null && lrcDialog.getChildren().size() > 0 ){
            return;
        }

        JFXDialogLayout content = new JFXDialogLayout();
        content.getStyleClass().add("galgame-lrc-dialog");

        content.setHeading(new Label("歌词浏览"));

        AnchorPane anchorPane=new AnchorPane();

        ListView<SubtitleCue> listView=new ListView<>();
        listView.getStyleClass().add("galgame-lrc-list-view");
        listView.setItems(viewModel.getLrcBeans());
        listView.setCellFactory(new Callback<ListView<SubtitleCue>, ListCell<SubtitleCue>>() {
            LrcGotoBtnListener lrcGotoBtnListener=new LrcGotoBtnListener();
            @Override
            public ListCell<SubtitleCue> call(ListView<SubtitleCue> songInformationListView) {
                ListCell<SubtitleCue> cell=new ListCell<SubtitleCue>(){
                    @Override
                    protected void updateItem(SubtitleCue lrcBean, boolean b) {
                        super.updateItem(lrcBean, b);
                        if (lrcBean!=null){
                            String text = SubtitleDisplayService.convert(lrcBean.getText());
                            String translation = SubtitleDisplayService.convert(lrcBean.getTranslation());
                            if (translation != null && !translation.isBlank()){
                                setText(text+"\n"+translation);
                            }else{
                                setText(text);
                            }

                            setWrapText(true);
                            Button gotoBtn=new Button();
                            gotoBtn.setGraphic(new Region());
                            gotoBtn.getStyleClass().addAll("galgame-skip-btn","galgame-goto-btn");
                            setGraphic(gotoBtn);
                            setGraphicTextGap(20.0);
                            gotoBtn.setId(String.valueOf(lrcBean.getStartTime()));
                            gotoBtn.setOnAction(lrcGotoBtnListener);
                        }else {
                            this.setText(null);
                            setGraphic(null);
                        }
                    }
                };
                return cell;
            }
        });



        anchorPane.getChildren().addAll(listView);
        AnchorPane.setTopAnchor(listView,0.0);
        AnchorPane.setBottomAnchor(listView,0.0);
        AnchorPane.setLeftAnchor(listView,0.0);
        AnchorPane.setRightAnchor(listView,0.0);

        content.setBody(anchorPane);

        lrcDialog = new JFXDialog(this, content, JFXDialog.DialogTransition.CENTER);
        javafx.beans.value.ChangeListener<String> modeListener = (observable, oldValue, newValue) -> listView.refresh();
        StackPane dialogContainer = (StackPane) lrcDialog.getChildren().get(0);
        dialogContainer.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT,null,null)));
        dialogContainer.setPrefHeight(this.getHeight()-200);
        dialogContainer.setPrefWidth(500);
        double offsetX = this.getWidth()/2;
        dialogContainer.setTranslateX(offsetX-260);
        dialogContainer.setAlignment(Pos.BOTTOM_RIGHT);

        lrcDialog.setAlignment(Pos.BOTTOM_RIGHT);
        lrcDialog.show();

        lrcDialog.setOnDialogClosed(event -> {
            SubtitleDisplayService.modeProperty().removeListener(modeListener);
            StackPane  node = (StackPane) event.getSource();
            node.getChildren().clear();
            node=null;
            System.gc();
        });

        lrcDialog.setOnDialogOpened(event ->{
            listView.scrollTo(viewModel.getCurrentLrcIndex());
            listView.getSelectionModel().select(viewModel.getCurrentLrcIndex());
        } );
        SubtitleDisplayService.modeProperty().addListener(modeListener);



    }



    /**
     * 字幕行的跳转按钮监听：按钮 id 存的是该行的起始时间（毫秒），
     * 点击后按当前字幕延迟补偿，再定位播放进度。
     */
    class LrcGotoBtnListener implements EventHandler<ActionEvent>{
        @Override
        public void handle(ActionEvent event) {
            Button button = (Button) event.getSource();
            long targetMs = SubtitleTiming.seekTargetMillis(
                    Long.parseLong(button.getId()), Config.setting.subtitleDelayMs.get());
            viewModel.seek(Duration.millis(targetMs));
        }
    }
}


