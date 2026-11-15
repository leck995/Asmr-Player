package cn.tealc.asmr.ui.library;

import cn.tealc.asmr.eventbus.EventBus;
import atlantafx.base.controls.Tab;
import atlantafx.base.controls.TabLine;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.utils.CssLoader;
import cn.tealc.asmr.mvvm.FluentViewLoader;
import cn.tealc.asmr.mvvm.FXMLView;
import cn.tealc.asmr.mvvm.NotificationObserver;
import cn.tealc.asmr.mvvm.ViewTuple;
import cn.tealc.asmr.ui.component.PageHeader;
import cn.tealc.asmr.ui.anywheredoor.AnywhereDoorView;
import cn.tealc.asmr.ui.anywheredoor.AnywhereDoorViewModel;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.material2.Material2OutlinedAL;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * 媒体库与快捷入口设置对话框。
 * <p>
 * 纯 Tab 容器，各 Tab 内容通过懒加载按需载入：
 * <ul>
 *   <li>Tab "媒体库" → {@link UnifiedMediaLibraryView}</li>
 *   <li>Tab "快捷入口" → {@link AnywhereDoorView}</li>
 *   <li>Tab "存储源" → {@link StorageSourceView}</li>
 * </ul>
 *
 * @author Leck
 */
public class MediaLibraryView implements FXMLView<MediaLibraryViewModel>, Initializable {

    @FXML private StackPane root;
    @FXML private TabLine tabPane;
    @FXML private StackPane contentPane;
    @FXML private PageHeader pageHeader;
    @FXML private Tab localLibraryTab;
    @FXML private Tab anywhereDoorTab;
    @FXML private Tab openListTab;

    private ViewTuple<UnifiedMediaLibraryView, UnifiedMediaLibraryViewModel> localTuple;
    private ViewTuple<AnywhereDoorView, AnywhereDoorViewModel> anywhereTuple;
    private ViewTuple<StorageSourceView, StorageSourceViewModel> openListTuple;
    private final NotificationObserver skinObserver = (key, payload) -> applyStyleSheet();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        applyStyleSheet();
        pageHeader.setIcon(Material2OutlinedAL.FOLDER_OPEN);
        pageHeader.setTitle("管理");
        pageHeader.setSubtitle("管理本地媒体、OpenList 目录与快捷入口。");
        pageHeader.setSeparatorVisible(false);

        // 禁用 Tab 关闭按钮
        tabPane.setTabClosingPolicy(Tab.ClosingPolicy.NO_TABS);

        root.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null) {
                EventBus.unsubscribe(NotificationKey.CHANGE_SKIN, skinObserver);
            } else {
                EventBus.subscribe(NotificationKey.CHANGE_SKIN, skinObserver);
            }
        });

        // Tab 切换时懒加载并缓存，内容显示在 contentPane
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == localLibraryTab) {
                if (localTuple == null) {
                    localTuple = FluentViewLoader.javaView(UnifiedMediaLibraryView.class).load();
                } else {
                    localTuple.getViewModel().refresh();
                }
                contentPane.getChildren().setAll(localTuple.getView());
            } else if (newTab == anywhereDoorTab) {
                if (anywhereTuple == null) {
                    anywhereTuple = FluentViewLoader.fxmlView(AnywhereDoorView.class).load();
                }
                contentPane.getChildren().setAll(anywhereTuple.getView());
            } else if (newTab == openListTab) {
                if (openListTuple == null) {
                    openListTuple = FluentViewLoader.javaView(StorageSourceView.class).load();
                }
                contentPane.getChildren().setAll(openListTuple.getView());
            }
        });

        // 默认加载第一个 Tab
        localTuple = FluentViewLoader.javaView(UnifiedMediaLibraryView.class).load();
        contentPane.getChildren().setAll(localTuple.getView());
    }

    private void applyStyleSheet() {
        root.getStylesheets().clear();
        root.getStylesheets().add(CssLoader.getCss(CssLoader.mediaLibrary));
    }
}
