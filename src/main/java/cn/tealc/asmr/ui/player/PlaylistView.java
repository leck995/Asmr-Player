package cn.tealc.asmr.ui.player;

import cn.tealc.asmr.eventbus.EventBus;


import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.factory.ViewFactory;
import cn.tealc.asmr.service.FileOperationService;
import cn.tealc.asmr.ui.component.EmptyState;
import cn.tealc.asmr.ui.component.OverlayLayer;
import cn.tealc.asmr.ui.dialog.Alerts;
import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.AudioTrack;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.model.message.MessageType;
import cn.tealc.asmr.player.VlcPlayer;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.subtitle.SubtitleDisplayService;
import cn.tealc.asmr.subtitle.SubtitleScriptMode;
import cn.tealc.asmr.ui.collection.AddToSheetView;
import cn.tealc.asmr.ui.collection.AddToSheetViewModel;
import cn.tealc.asmr.ui.work.WorkDetailView;
import cn.tealc.asmr.ui.work.WorkDetailViewModel;
import cn.tealc.asmr.utils.CssLoader;
import cn.tealc.asmr.utils.FileNameUtils;
import com.jfoenix.controls.JFXDialogLayout;
import cn.tealc.asmr.mvvm.FluentViewLoader;
import cn.tealc.asmr.mvvm.ViewTuple;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.util.Callback;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;

/**
 * 播放队列面板内容，展示 {@link VlcPlayer} 的当前队列并提供播放、移除、删除与来源跳转。
 * <p>
 * 自身只是 {@code JFXDialogLayout} 内容体，由 {@code MainView} 包进 {@code JFXDialog}
 * 展示（内容容器被偏移到窗口右下角）；{@code galgame} 参数只追加样式类切换配色，行为一致。
 * <p>
 * 列表项直接引用播放器的 {@code ObservableList}，双击行播放，单击被事件过滤器吞掉；
 * 正在播放的行通过 {@code playing} 伪类与喇叭图标高亮，切歌后统一 {@code refresh}
 * 一次，避免复用单元格残留旧高亮。
 *
 * @author Leck
 */
public class PlaylistView extends JFXDialogLayout {

    /** 当前正在播放的行，供 CSS 单独高亮。 */
    private static final javafx.css.PseudoClass PLAYING = javafx.css.PseudoClass.getPseudoClass("playing");

    private final ListView<AudioTrack> listView;
    private final VlcPlayer player;
    private final Label songSizeLabel;

    /**
     * 组装播放队列面板：头部标题与曲目数、曲目列表、底部操作条（字幕转换/查看作品/清空）。
     * <p>
     * 构造时若已有正在播放的曲目，会滚动定位并选中该行；清空按钮把计数标签重置为“共0首”。
     *
     * @param galgame 为 true 时追加 {@code galgame} 样式类，配合 Galgame 播放页配色
     */
    public PlaylistView(boolean galgame) {
        player = VlcPlayer.getInstance();
        getStylesheets().add(CssLoader.getCss(CssLoader.playlist));
        getStyleClass().add("playlist");
        if (galgame) {
            getStyleClass().add("galgame");
        }

        HBox headPane = new HBox(8);
        headPane.setAlignment(Pos.CENTER_LEFT);
        songSizeLabel = new Label(String.format("共%d首", player.getMusics().size()));
        // 字号交由 CSS 控制，避免代码里的固定值覆盖样式表
        songSizeLabel.getStyleClass().add("songs-size");
        Label title = new Label("播放队列");
        FontIcon titleIcon = new FontIcon(MaterialDesignP.PLAYLIST_MUSIC);
        titleIcon.getStyleClass().add("playlist-title-icon");
        title.setGraphic(titleIcon);
        title.setGraphicTextGap(7);
        title.getStyleClass().add("songs-title");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        headPane.getChildren().addAll(title, headerSpacer, songSizeLabel);
        setHeading(headPane);


        listView = new ListView<>();
        listView.getStyleClass().add("songs-list-view");
        EmptyState emptyState = EmptyState.of(MaterialDesignP.PLAYLIST_MUSIC,
                "播放队列为空", "从作品或歌单开始播放后，曲目会显示在这里。");
        emptyState.setCompact(true);
        listView.setPlaceholder(emptyState);
        listView.setItems(player.getMusics());
        if (player.getPlayingMusic() != null) {
            listView.scrollTo(player.getMusicIndex());
            listView.getSelectionModel().select(player.getPlayingMusic());
        }

        listView.setCellFactory(cell -> new ItemCell());
        // 选中高亮跟随播放项：切歌后选中行始终指向正在播放的曲目，避免错位
        player.playingMusicProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                return;
            }
            int index = listView.getItems().indexOf(newValue);
            if (index >= 0) {
                listView.getSelectionModel().select(index);
            }
            // 单元格是复用的，切歌后统一刷新可见行，避免旧行残留播放高亮
            listView.refresh();
        });
        listView.addEventFilter(MouseEvent.ANY, click -> {
            if (click.getEventType() == MouseEvent.MOUSE_PRESSED && click.getClickCount() != 2) {
                click.consume();
            }
            if (click.getClickCount() == 2) {
                if (click.getEventType() == MouseEvent.MOUSE_CLICKED) {
                    player.play(listView.getSelectionModel().getSelectedIndex());
                }
            }
        });

        setBody(listView);
        Button toFolderBtn = new Button("查看作品");
        toFolderBtn.setGraphic(createActionIcon(MaterialDesignF.FOLDER_OPEN));
        toFolderBtn.setTooltip(new Tooltip("打开ASMR目录"));
        toFolderBtn.getStyleClass().addAll("to-folder-btn");
        toFolderBtn.setOnAction(actionEvent -> {
            AsmrWork work = player.getPlayingWork();
            if (work != null) {
                ViewTuple<WorkDetailView, WorkDetailViewModel> load = ViewFactory.workDetailView(work);
                OverlayLayer.show((Pane) load.getView());
            }

        });
        Button clearBtn = new Button("清空");
        clearBtn.setGraphic(createActionIcon(MaterialDesignD.DELETE_SWEEP));
        clearBtn.setTooltip(new Tooltip("清空歌曲列表"));
        clearBtn.getStyleClass().addAll("clear-songs-btn");
        clearBtn.setOnAction(actionEvent -> {
            player.clearPlayingList();
            songSizeLabel.setText(String.format("共%d首", 0));
        });
        HBox actionBar = new HBox(6, createSubtitleModeButton(), toFolderBtn, clearBtn);
        actionBar.setAlignment(Pos.CENTER_RIGHT);
        actionBar.setFillHeight(true);
        actionBar.getStyleClass().add("playlist-action-bar");
        setActions(actionBar);
    }

    /**
     * 创建播放列表底部的字幕文字体系切换菜单。
     */
    private MenuButton createSubtitleModeButton() {
        MenuButton button = new MenuButton();
        button.textProperty().bind(Bindings.createStringBinding(
                () -> "字幕转换 · " + SubtitleDisplayService.getCurrentMode().getDisplayName(),
                SubtitleDisplayService.modeProperty()));
        button.setGraphic(createActionIcon(MaterialDesignT.TRANSLATE));
        button.getStyleClass().add("subtitle-mode-btn");
        button.setTooltip(new Tooltip("切换播放字幕的简繁显示方式"));

        ToggleGroup modeGroup = new ToggleGroup();
        for (SubtitleScriptMode mode : SubtitleScriptMode.values()) {
            RadioMenuItem item = new RadioMenuItem(mode.getDisplayName());
            item.setToggleGroup(modeGroup);
            item.setUserData(mode);
            item.setSelected(mode == SubtitleDisplayService.getCurrentMode());
            item.setOnAction(event -> SubtitleDisplayService.modeProperty().set(mode.name()));
            button.getItems().add(item);
        }
        SubtitleDisplayService.modeProperty().addListener((observable, oldValue, newValue) -> {
            SubtitleScriptMode currentMode = SubtitleScriptMode.fromConfig(newValue);
            modeGroup.getToggles().stream()
                    .filter(toggle -> toggle.getUserData() == currentMode)
                    .findFirst()
                    .ifPresent(toggle -> modeGroup.selectToggle(toggle));
        });
        return button;
    }

    private FontIcon createActionIcon(org.kordamp.ikonli.Ikon icon) {
        FontIcon fontIcon = new FontIcon(icon);
        fontIcon.getStyleClass().add("playlist-action-icon");
        return fontIcon;
    }

    /**
     * 播放队列单元格：喇叭图标 + 标题/作者 + 文件后缀，右键菜单提供播放、来源、移除与删除。
     * <p>
     * “删除”会二次确认后删除磁盘上的音频文件，不可撤销；失败时发布错误消息并抛
     * {@code RuntimeException}。
     */
    class ItemCell extends ListCell<AudioTrack> {
        private final HBox content;
        private final Label name = new Label();
        private final Label artist = new Label();
        private final Label type = new Label();
        private final FontIcon playingIcon = new FontIcon(Material2OutlinedMZ.VOLUME_UP);

        public ItemCell() {
            playingIcon.getStyleClass().add("playing-icon");
            // 未播放时必须同时取消 managed，否则会占据左侧宽度，导致所有行文字错位
            playingIcon.setVisible(false);
            playingIcon.setManaged(false);

            name.getStyleClass().add("name");
            artist.getStyleClass().add("artist");
            type.getStyleClass().add("type");
            // 类型标签在行尾固定宽度，避免不同后缀长度造成标题宽度跳动
            type.setMinWidth(Region.USE_PREF_SIZE);

            VBox textBox = new VBox(2, name, artist);
            textBox.setAlignment(Pos.CENTER_LEFT);
            textBox.getStyleClass().add("text-box");
            HBox.setHgrow(textBox, Priority.ALWAYS);
            // 文本区需允许收缩，否则超长标题会把类型标签挤出可视区
            textBox.setMinWidth(0);
            name.setMaxWidth(Double.MAX_VALUE);
            artist.setMaxWidth(Double.MAX_VALUE);

            content = new HBox(10, playingIcon, textBox, type);
            content.setAlignment(Pos.CENTER_LEFT);
            content.getStyleClass().add("cell-content");
            setGraphic(content);
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }

        /**
         * 按当前播放项刷新播放高亮。
         * <p>
         * 只有本行对应的曲目正在播放时才显示图标与伪类，其余行一律清除，
         * 避免切歌后多行同时呈现播放状态。
         *
         * @param music 本行绑定的曲目，空行传 null
         */
        private void refreshPlayingState(AudioTrack music) {
            AudioTrack playing = player.getPlayingMusic();
            boolean isPlaying = music != null && playing != null && playing.equals(music);
            playingIcon.setVisible(isPlaying);
            playingIcon.setManaged(isPlaying);
            pseudoClassStateChanged(PLAYING, isPlaying);
        }

        /**
         * 复用单元格时重新绑定曲目数据，并整份重建右键菜单。
         * <p>
         * 菜单项的处理器直接捕获当前 {@code music}，因此每次刷新都必须重建，
         * 否则旧行复用后会对错误的曲目操作。
         *
         * @param music 本行曲目，空行为 null（此时清空文本、隐藏内容并清除播放高亮）
         * @param b     {@code ListCell} 的空行标记，本实现未使用
         */
        @Override
        protected void updateItem(AudioTrack music, boolean b) {
            super.updateItem(music, b);
            if (music != null) {
                this.setDisable(false);
                content.setVisible(true);
                name.setText(FileNameUtils.getNameWithSuffix(music.getName()));
                artist.setText(music.getArtist());
                type.setText(FileNameUtils.getSuffix(music.getName()));
                setTooltip(new Tooltip(music.getName()));

                // 当前播放项显示播放图标与高亮
                refreshPlayingState(music);

                MenuItem playItem = new MenuItem("播放", new FontIcon(Material2OutlinedMZ.PLAY_CIRCLE_FILLED_WHITE));
                playItem.setOnAction(event -> {
                    player.play(listView.getItems().indexOf(music));
                    listView.getSelectionModel().select(music);
                });
                MenuItem removeItem = new MenuItem("移除", new FontIcon(Material2OutlinedAL.CANCEL));
                removeItem.setOnAction(event -> {
                    player.removeMusic(listView.getItems().indexOf(music));
                    listView.getSelectionModel().select(player.getPlayingIndexInList());
                    songSizeLabel.setText(String.format("共%d首", player.getMusics().size()));
                });

                MenuItem deleteItem = new MenuItem("删除", new FontIcon(Material2OutlinedAL.DELETE_OUTLINE));
                deleteItem.getStyleClass().add("work-delete-menu-item");
                deleteItem.setOnAction(event -> {
                    Label targetLabel = new Label(FileNameUtils.getNameWithSuffix(music.getName()));
                    targetLabel.setWrapText(true);
                    targetLabel.getStyleClass().add("delete-file-target");
                    VBox details = new VBox(6, new Label("删除目标"), targetLabel);
                    details.getStyleClass().add("delete-file-details");

                    Alerts.danger()
                            .title("删除音频文件")
                            .message("将永久删除该音频文件，且无法撤销。")
                            .content(details)
                            .ok("删除", handle -> {
                        AsmrWork work = music.getWork();
                        AsmrFile target = new AsmrFile(music.getName(), music.getUrl(),
                                FileNameUtils.getSuffix(music.getName()), false, music.getName(), 0, null);
                        Thread.startVirtualThread(() -> {
                            FileOperationService.OperationResult result =
                                    DI.get(FileOperationService.class).deleteFile(work, target);
                            Platform.runLater(() -> {
                                if (!result.success()) {
                                    EventBus.publish(NotificationKey.MESSAGE,
                                            new MessageInfo(MessageType.ERROR, result.message()));
                                    return;
                                }
                                player.removeMusic(listView.getItems().indexOf(music));
                                listView.getSelectionModel().select(player.getPlayingIndexInList());
                                songSizeLabel.setText(String.format("共%d首", player.getMusics().size()));
                            });
                        });
                    })
                            .cancel()
                            .show();
                });


                MenuItem sourceMI = new MenuItem("来源", new FontIcon(Material2OutlinedAL.COMPUTER));
                sourceMI.setOnAction(event -> {
                    ViewTuple<WorkDetailView, WorkDetailViewModel> viewTuple = ViewFactory.workDetailView(getItem().getWork());
                    OverlayLayer.show((Pane) viewTuple.getView());
                });


                ContextMenu contextMenu = new ContextMenu(playItem, sourceMI, removeItem, deleteItem);
                setContextMenu(contextMenu);
            } else {
                name.setText(null);
                artist.setText(null);
                content.setVisible(false);
                // 清空复用的单元格时一并清除播放态，避免样式残留到新行
                refreshPlayingState(null);
                setContextMenu(null);
                this.setDisable(true);
            }
        }
    }
}
