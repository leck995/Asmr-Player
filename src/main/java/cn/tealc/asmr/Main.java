package cn.tealc.asmr;

import cn.tealc.asmr.eventbus.EventBus;

import atlantafx.base.theme.PrimerLight;
import cn.tealc.asmr.dao.AsmrWorkDao;
import cn.tealc.asmr.dao.JdbcUtils;
import cn.tealc.asmr.dao.WorkDao;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.player.VlcPlayer;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.service.PlayHistoryService;
import cn.tealc.asmr.service.WorkService;
import cn.tealc.asmr.service.MetadataSyncService;
import cn.tealc.asmr.service.SaveDataService;
import cn.tealc.asmr.service.CollectionService;
import cn.tealc.asmr.service.DictionaryService;
import cn.tealc.asmr.service.PlaylistService;
import cn.tealc.asmr.service.FileOperationService;
import cn.tealc.asmr.service.MediaLibraryService;
import cn.tealc.asmr.service.CacheService;
import cn.tealc.asmr.service.AnywhereDoorService;
import cn.tealc.asmr.ui.dialog.legacy.NewDialog;
import cn.tealc.asmr.ui.main.MainView;
import cn.tealc.asmr.ui.main.MainViewModel;
import cn.tealc.asmr.ui.navigation.NavigationService;
import cn.tealc.asmr.ui.navigation.WorkSource;
import cn.tealc.asmr.ui.navigation.WorksRoute;
import cn.tealc.asmr.ui.tray.TrayManager;
import cn.tealc.asmr.utils.AppPaths;
import cn.tealc.asmr.utils.CssLoader;
import cn.tealc.asmr.utils.FXResourcesLoader;
import cn.tealc.asmr.mvvm.FluentViewLoader;
import cn.tealc.asmr.mvvm.ViewTuple;
import cn.tealc.asmr.utils.SecureUtils;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.StackPane;
import javafx.scene.Parent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

/**
 * JavaFX 应用主类，负责主窗口构建与全局生命周期。
 * <p>
 * 启动顺序（{@link #start(Stage)}）：
 * <ol>
 *   <li>静态块先执行 {@code AppPaths.init()}，保证 logback 初始化前日志目录就绪；</li>
 *   <li>按设置项 {@code textSmooth} 设置 {@code prism.lcdtext} 字体平滑开关；</li>
 *   <li>{@code JdbcUtils.init()} 建表，随后 {@code Config.loadTextBlacklist()} 读字幕黑名单；</li>
 *   <li>{@code DI.bind(...)} 集中绑定 DAO 与各 Service 单例（见 {@link cn.tealc.asmr.di.DI}）；</li>
 *   <li>创建主 Stage、经 {@code FluentViewLoader} 加载 {@link MainView}、加载 CSS 并订阅
 *       {@code CHANGE_SKIN} 换肤事件；</li>
 *   <li>启用 {@code StageStyle.EXTENDED} 原生标题区并恢复窗口尺寸；</li>
 *   <li>若开启启动密码则先弹密码校验框，通过后进入主窗口，否则直接显示主窗口
 *       （恢复上次播放、安装托盘、注册全局快捷键）。</li>
 * </ol>
 * 另有静态方法 {@link #exit()}（保存设置与播放历史后退出）与 {@link #hide()}（隐藏或最小化）。
 * 打包入口见 {@link App}。
 *
 * @author Leck
 */
public class Main extends Application {
    static {
        // 必须早于任何 logger 初始化，logback 依赖 asmr.log.dir
        AppPaths.init();
    }

    /** 主窗口，使用 JavaFX 26 原生 StageStyle.EXTENDED + HeaderBar 接管标题区 */
    public static Stage window;
    private static boolean exiting;
    @Override
    public void start(Stage stage) throws IOException {
        System.setProperty("prism.lcdtext", String.valueOf(Config.setting.textSmooth.get()));
        Config.setting.textSmooth.addListener((observableValue, aBoolean, t1) -> {
            System.setProperty("prism.lcdtext", String.valueOf(t1));
        });

        Timer autoGcTimer= new Timer(true);
        autoGcTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.gc();
            }
        },60000,60000);

        JdbcUtils.init();
        // 建表完成后才能读取黑名单；此处也会把旧版文件中的条目迁入数据库
        Config.loadTextBlacklist();


        // DI 绑定：数据访问层与服务层
        DI.bind(WorkDao.class, AsmrWorkDao::new);
        DI.bind(PlayHistoryService.class, PlayHistoryService::new);
        DI.bind(WorkService.class, WorkService::new);
        DI.bind(MetadataSyncService.class, MetadataSyncService::new);
        DI.bind(SaveDataService.class, SaveDataService::new);
        DI.bind(CollectionService.class, CollectionService::new);
        DI.bind(DictionaryService.class, DictionaryService::new);
        DI.bind(PlaylistService.class, PlaylistService::new);
        DI.bind(FileOperationService.class, FileOperationService::new);
        DI.bind(MediaLibraryService.class, MediaLibraryService::new);
        DI.bind(AnywhereDoorService.class, AnywhereDoorService::new);
        DI.bind(CacheService.class, CacheService::new);
        DI.bind(cn.tealc.asmr.service.ExternalPlayerService.class,
                cn.tealc.asmr.service.ExternalPlayerService::new);
        DI.bind(cn.tealc.asmr.task.background.TaskManager.class,
                cn.tealc.asmr.task.background.TaskManager::new);

        Stage mainStage = new Stage();
        window=mainStage;
        mainStage.setTitle("ASMR Player");
        mainStage.setOnCloseRequest(windowEvent -> {
            if (exiting) {
                return;
            }
            windowEvent.consume();
            exit();
        });
        mainStage.getIcons().add(new Image(FXResourcesLoader.loadStr("image/favicon.png")));
        ViewTuple<MainView, MainViewModel> viewTuple = FluentViewLoader.javaView(MainView.class).load();
        Scene scene = new Scene(viewTuple.getView());
        mainStage.setScene(scene);
        cssLoad(scene);
        EventBus.subscribe("CHANGE_SKIN",(s,objects) ->{
            scene.getStylesheets().clear();
            cssLoad(scene);
        });

        initFont();
        mainStage.setFullScreenExitHint("");
        mainStage.initStyle(StageStyle.EXTENDED);
        mainStage.setWidth(Config.setting.stageWidth.get());
        mainStage.setHeight(Config.setting.stageHeight.get());
        mainStage.setMinWidth(1280);
        mainStage.setMinHeight(720);

        if (Config.setting.pKeyModel.get() && Config.setting.startPassword.get() != null
                && !Config.setting.startPassword.get().isEmpty()) {
            showPasswordDialog(mainStage);
        } else {
            showMainWindow(mainStage);
        }
    }

    /**
     * 显示主窗口并执行启动后初始化。
     */
    private void showMainWindow(Stage mainStage) {
        mainStage.show();
        // 恢复上次播放
        if (Config.setting.isUserPlayingHistory()) {
            DI.get(PlayHistoryService.class).resumeLastPlay();
        }
        TrayManager.install();
        initQuicklyKey(mainStage.getScene());
    }

    /**
     * 显示密码验证窗口（NewDialog，带 HeaderBar 标题栏），验证通过后显示主窗口。
     */
    private void showPasswordDialog(Stage mainStage) {
        // 图标
        org.kordamp.ikonli.javafx.FontIcon lockIcon = new org.kordamp.ikonli.javafx.FontIcon("mdoal-lock");
        lockIcon.getStyleClass().add("password-check-icon");

        // 提示文字
        javafx.scene.control.Label hintLabel = new javafx.scene.control.Label("请输入启动密码以继续");
        hintLabel.getStyleClass().add("password-check-hint");

        // 密码输入框
        javafx.scene.control.PasswordField passwordField = new javafx.scene.control.PasswordField();
        passwordField.setPromptText("启动密码");
        passwordField.getStyleClass().add("password-check-field");
        passwordField.setPrefHeight(38);
        passwordField.setMaxWidth(Double.MAX_VALUE);

        // 内容布局
        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(12, lockIcon, hintLabel, passwordField);
        content.getStyleClass().add("password-check-content");
        content.setAlignment(javafx.geometry.Pos.CENTER);
        content.setPrefWidth(360);

        javafx.scene.control.ButtonType okType = new javafx.scene.control.ButtonType("确认", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        javafx.scene.control.ButtonType exitType = new javafx.scene.control.ButtonType("退出", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);

        NewDialog<String> dialog = new NewDialog<>("密码验证", content, okType, exitType);
        dialog.getDialogPane().setPrefWidth(420);
        dialog.getDialogPane().getStylesheets().add(
                Main.class.getResource("/cn/tealc/asmr/css/skin/password-check.css").toExternalForm());
        dialog.setOnShown(e -> {
            // 暗色主题下 EXTENDED 窗口四角透出白色，设 scene 背景透明
            if (dialog.getDialogPane().getScene() != null) {
                dialog.getDialogPane().getScene().setFill(Color.TRANSPARENT);
            }
            passwordField.requestFocus();
        });

        // 确认按钮初始禁用，输入内容后启用
        javafx.scene.control.Button okBtn = (javafx.scene.control.Button) dialog.getDialogPane().lookupButton(okType);
        if (okBtn != null) {
            okBtn.disableProperty().bind(passwordField.textProperty().isEmpty());
        }

        okBtn.setOnAction(e -> {
            if (passwordField.getText().length() >= 4
                    && SecureUtils.matchesHash(passwordField.getText(), Config.setting.startPassword.get())) {
                dialog.close();
                showMainWindow(mainStage);
                DI.get(NavigationService.class).openRoot(new WorksRoute(WorkSource.all()));
            } else {
                passwordField.clear();
                passwordField.requestFocus();
            }
        });
        passwordField.setOnAction(e -> okBtn.fire());

        // 退出按钮
        javafx.scene.control.Button exitBtn = (javafx.scene.control.Button) dialog.getDialogPane().lookupButton(exitType);
        if (exitBtn != null) {
            exitBtn.setOnAction(e -> {
                dialog.close();
                Platform.exit();
            });
        }

        dialog.show();
    }


    public static void main(String[] args) {
        Application.
        launch();
    }

    private void cssLoad(Scene scene){
        CssLoader.setTheme();
        scene.getStylesheets().add(CssLoader.getCss(CssLoader.baseUI));
        scene.getStylesheets().add(CssLoader.getCommonCss(CssLoader.svg));
        scene.getStylesheets().add(CssLoader.getCss(CssLoader.alert));
        scene.getStylesheets().add(CssLoader.getCss(CssLoader.atlantafx));
    }
    private void initFont(){
        boolean contains = Font.getFamilies().contains("Microsoft YaHei");
        if (!contains){
            Font.loadFonts(FXResourcesLoader.loadStream("font/HarmonyOS_Sans_SC_Bold.ttf"),12);
            Font.loadFonts(FXResourcesLoader.loadStream("font/HarmonyOS_Sans_SC_Bold.ttf"),12);
            window.getScene().getRoot().setStyle("-fx-font-family: \"HarmonyOS Sans SC\"");
        }else {
            window.getScene().getRoot().setStyle("-fx-font-family: \"Microsoft YaHei\"");
        }
    }

    private void initQuicklyKey(Scene scene){
        KeyCombination next=new KeyCodeCombination(KeyCode.RIGHT,KeyCombination.SHORTCUT_DOWN);
        KeyCombination pre=new KeyCodeCombination(KeyCode.LEFT,KeyCombination.SHORTCUT_DOWN);
        KeyCombination upVolume=new KeyCodeCombination(KeyCode.UP,KeyCombination.SHORTCUT_DOWN);
        KeyCombination downVolume=new KeyCodeCombination(KeyCode.DOWN,KeyCombination.SHORTCUT_DOWN);
        KeyCombination play=new KeyCodeCombination(KeyCode.P,KeyCombination.SHORTCUT_DOWN);
        KeyCombination forward=new KeyCodeCombination(KeyCode.D,KeyCombination.SHORTCUT_DOWN);
        KeyCombination back=new KeyCodeCombination(KeyCode.A,KeyCombination.SHORTCUT_DOWN);


        scene.getAccelerators().put(next,()->VlcPlayer.getInstance().next());
        scene.getAccelerators().put(pre,()->VlcPlayer.getInstance().pre());
        scene.getAccelerators().put(upVolume,()->VlcPlayer.getInstance().setVolume(VlcPlayer.getInstance().getVolume()+0.1));
        scene.getAccelerators().put(downVolume,()->VlcPlayer.getInstance().setVolume(VlcPlayer.getInstance().getVolume()-0.1));
        scene.getAccelerators().put(play,()->VlcPlayer.getInstance().playOrPause());
        scene.getAccelerators().put(forward,()->VlcPlayer.getInstance().skipTime(Duration.seconds(5)));
        scene.getAccelerators().put(back,()->VlcPlayer.getInstance().skipTime(Duration.seconds(-5)));
        scene.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode()==KeyCode.ESCAPE){
                VlcPlayer.getInstance().pause();
                VlcPlayer.getInstance().desktopLrcShowProperty().set(false);
                hide();
            }
        });
    }



    public static void exit(){
        if (exiting) {
            return;
        }
        exiting = true;
        Config.saveProperties(true);
        DI.get(PlayHistoryService.class).saveHistory();
        JdbcUtils.exit();
        TrayManager.uninstall();
        window.close();
        VlcPlayer.getInstance().release();
        Platform.exit();
        System.exit(0);
    }




    public static void hide(){
        if (TrayManager.isInstalled()) {
            window.hide();
        } else {
            // 没有托盘时隐藏会让窗口无法找回，降级为最小化
            window.setIconified(true);
        }
    }


}
