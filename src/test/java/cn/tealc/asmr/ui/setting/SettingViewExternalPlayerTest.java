package cn.tealc.asmr.ui.setting;

import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.theme.PrimerLight;
import cn.tealc.asmr.BaseDatabaseTest;
import cn.tealc.asmr.dao.AsmrWorkDao;
import cn.tealc.asmr.dao.WorkDao;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.mvvm.FluentViewLoader;
import cn.tealc.asmr.mvvm.ViewTuple;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.utils.CssLoader;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 设置页第三方播放器配置区的加载与交互测试。
 * <p>
 * 覆盖 FXML 图标字面量有效性、控件绑定以及识别结果展示。
 */
class SettingViewExternalPlayerTest extends BaseDatabaseTest {

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        fx(() -> {
            Platform.setImplicitExit(false);
            return null;
        });
    }

    @BeforeEach
    void bindServices() {
        DI.bind(WorkDao.class, AsmrWorkDao::new);
    }

    @Test
    void thirdPartyPlayerSectionLoadsAndBinds() throws Exception {
        boolean previousEnabled = Config.setting.externalPlayerEnabled.get();
        String previousPath = Config.setting.externalPlayerPath.get();
        String previousArgs = Config.setting.externalPlayerArgs.get();
        try {
            Config.setting.externalPlayerEnabled.set(false);
            Config.setting.externalPlayerPath.set("");
            Config.setting.externalPlayerArgs.set("");

            ViewTuple<SettingView, SettingViewModel> tuple = fx(() -> FluentViewLoader.fxmlView(SettingView.class).load());
            attach(tuple.getView());

            fx(() -> {
                // FXML 中的图标字面量必须有效，否则加载即失败；能走到这里说明全部通过
                Node toggle = tuple.getView().lookup("#openExternalPlayer");
                assertNotNull(toggle, "启用第三方播放器开关应存在");
                assertEquals("第三方播放器", groupTitleBeforeExternalSection(tuple.getView()));

                TextField pathField = (TextField) tuple.getView().lookup("#externalPlayerPathTextField");
                TextField argsField = (TextField) tuple.getView().lookup("#externalPlayerArgsTextField");
                Button selectBtn = (Button) tuple.getView().lookup("#externalPlayerSelectBtn");
                assertNotNull(pathField);
                assertNotNull(argsField);
                assertNotNull(selectBtn);
                assertEquals("选择", selectBtn.getText());

                // 关闭状态下细节配置隐藏
                Node pathTile = tuple.getView().lookup("#externalPlayerPathTile");
                assertFalse(pathTile.isVisible());
                assertFalse(pathTile.isManaged());
                return null;
            });

            // 打开开关后细节配置出现，且开关写回配置
            fx(() -> {
                ToggleSwitch toggle = (ToggleSwitch) tuple.getView().lookup("#openExternalPlayer");
                toggle.setSelected(true);
                return null;
            });
            fx(() -> {
                assertTrue(Config.setting.externalPlayerEnabled.get());
                Node pathTile = tuple.getView().lookup("#externalPlayerPathTile");
                assertTrue(pathTile.isVisible());
                assertTrue(pathTile.isManaged());
                return null;
            });
        } finally {
            Config.setting.externalPlayerEnabled.set(previousEnabled);
            Config.setting.externalPlayerPath.set(previousPath);
            Config.setting.externalPlayerArgs.set(previousArgs);
        }
    }

    @Test
    void playerKindIsReportedInTileDescription() throws Exception {
        String previousPath = Config.setting.externalPlayerPath.get();
        try {
            Config.setting.externalPlayerPath.set("C:/Tools/mpv.exe");
            ViewTuple<SettingView, SettingViewModel> tuple = fx(() -> FluentViewLoader.fxmlView(SettingView.class).load());
            attach(tuple.getView());

            fx(() -> {
                SettingViewModel viewModel = tuple.getViewModel();
                viewModel.refreshExternalPlayerKind();
                // 路径不存在时如实提示，不假装识别成功
                assertNotNull(viewModel.externalPlayerKindProperty().get());
                assertTrue(viewModel.externalPlayerKindProperty().get().contains("路径无效"));
                return null;
            });
        } finally {
            Config.setting.externalPlayerPath.set(previousPath);
        }
    }

    @Test
    void resetArgsActionClearsTemplate() throws Exception {
        String previousArgs = Config.setting.externalPlayerArgs.get();
        try {
            Config.setting.externalPlayerArgs.set("--fs {url}");
            ViewTuple<SettingView, SettingViewModel> tuple = fx(() -> FluentViewLoader.fxmlView(SettingView.class).load());
            attach(tuple.getView());

            fx(() -> {
                Button reset = (Button) tuple.getView().lookup("#externalPlayerResetArgsBtn");
                assertNotNull(reset);
                reset.fire();
                return null;
            });
            fx(() -> {
                assertEquals("", Config.setting.externalPlayerArgs.get());
                assertEquals("", ((TextField) tuple.getView().lookup("#externalPlayerArgsTextField")).getText());
                return null;
            });
        } finally {
            Config.setting.externalPlayerArgs.set(previousArgs);
        }
    }

    /**
     * 找到「第三方播放器」分组标题，确认新分区放在基本页中。
     */
    private static String groupTitleBeforeExternalSection(Node view) {
        return view.lookupAll(".setting-group-title").stream()
                .filter(Label.class::isInstance)
                .map(Label.class::cast)
                .map(Label::getText)
                .filter("第三方播放器"::equals)
                .findFirst()
                .orElse("");
    }

    private static void attach(Node view) throws Exception {
        fx(() -> {
            StackPane root = new StackPane(view);
            root.getStylesheets().add(new PrimerLight().getUserAgentStylesheet());
            root.getStylesheets().add(CssLoader.getCommonCss("/white-skin/atlantafx.css"));
            new Scene(root, 1000, 700);
            root.resize(1000, 700);
            root.applyCss();
            root.layout();
            return null;
        });
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(12, TimeUnit.SECONDS);
    }
}
