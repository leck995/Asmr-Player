package cn.tealc.asmr.ui.galgame;

import cn.tealc.asmr.eventbus.EventBus;

import cn.tealc.asmr.di.Inject;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.BackgroundRef;
import cn.tealc.asmr.model.GalgameSaveData;
import cn.tealc.asmr.model.SubtitleCue;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.service.BackgroundService;
import cn.tealc.asmr.service.SaveDataService;
import cn.tealc.asmr.service.WorkService;
import cn.tealc.asmr.ui.player.PlayerBindingSupport;
import cn.tealc.asmr.utils.AppPaths;
import cn.tealc.asmr.utils.ImageLoadUtils;
import cn.tealc.asmr.utils.ImageUtils;
import cn.tealc.asmr.utils.URLUtils;
import cn.tealc.asmr.mvvm.SceneLifecycle;
import cn.tealc.asmr.mvvm.ViewModel;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Galgame 播放界面的视图模型：把播放器状态转发为界面属性，并管理背景图集与存档写入。
 * <p>
 * 播放、进度、字幕等属性在构造时一次性绑定到 {@link PlayerBindingSupport}，本类不直接操作播放器，
 * 也不需要随帧轮询；{@code playing}/{@code loop}/{@code mute} 等为双向绑定，界面可直接改写。
 * <p>
 * 背景由 {@link BackgroundService} 提供，读取与解码都在虚拟线程执行，回填前会校验当前播放作品
 * 是否仍是发起请求时那一个，避免切歌后旧结果覆盖新背景。界面双击播放区域调用
 * {@link #changeBackground()}：已有多个候选时直接轮播，否则先扫描作品目录；选中的背景会被记住。
 * 没有可用背景时回退用户设置的默认背景图，该回退受 {@code galgameBgModel} 开关控制。
 *
 * @author Leck
 */
public class GalgameDetailViewModel implements ViewModel, SceneLifecycle {
    @Inject
    private WorkService workService;
    @Inject
    private SaveDataService saveDataService;
    @Inject
    private BackgroundService backgroundService;
    private static final Logger LOG= LoggerFactory.getLogger(GalgameDetailViewModel.class);
    private final SimpleBooleanProperty desktopLrc = new SimpleBooleanProperty();
    private final SimpleBooleanProperty disorder = new SimpleBooleanProperty();
    private final SimpleBooleanProperty loop = new SimpleBooleanProperty();
    private final SimpleBooleanProperty playing = new SimpleBooleanProperty();
    private final SimpleBooleanProperty mute = new SimpleBooleanProperty();
    private final SimpleBooleanProperty ready = new SimpleBooleanProperty();
    private final SimpleStringProperty title = new SimpleStringProperty();
    private final SimpleStringProperty artist = new SimpleStringProperty();
    private final SimpleStringProperty currentLrc = new SimpleStringProperty();
    private final SimpleIntegerProperty currentLrcIndex = new SimpleIntegerProperty();
    private final SimpleDoubleProperty currentTime = new SimpleDoubleProperty();
    private final SimpleDoubleProperty totalTime = new SimpleDoubleProperty();
    private final SimpleObjectProperty<ObservableList<SubtitleCue>> lrcBeans = new SimpleObjectProperty<>();
    private final ObservableList<BackgroundRef> backgroundRefs = FXCollections.observableArrayList();
    private volatile BackgroundRef currentBackground;
    private final SimpleIntegerProperty backgroundIndex = new SimpleIntegerProperty(0);
    private final SimpleObjectProperty<Image> backgroundImage = new SimpleObjectProperty<>();
    private final SimpleObjectProperty<Image> cover = new SimpleObjectProperty<>();
    private final SimpleObjectProperty<AsmrWork> work = new SimpleObjectProperty<>();

    private PlayerBindingSupport player;

    public GalgameDetailViewModel() {
        player = new PlayerBindingSupport();
        // 直接绑定到 player 属性（不再需要解绑逻辑）
        desktopLrc.bindBidirectional(player.desktopLrcShowProperty());
        disorder.bindBidirectional(player.disorderProperty());
        loop.bindBidirectional(player.loopProperty());
        playing.bindBidirectional(player.playingProperty());
        mute.bindBidirectional(player.muteProperty());
        ready.bind(player.readyProperty());
        title.bindBidirectional(player.titleProperty());
        artist.bindBidirectional(player.artistProperty());
        currentLrc.bind(player.lrcSelectedTextProperty());
        currentLrcIndex.bind(player.lrcSelectedIndexProperty());
        currentTime.bind(player.currentTimeProperty());
        totalTime.bind(player.totalTimeProperty());
        cover.bind(player.coverProperty());
        lrcBeans.bind(player.lrcBeansProperty());
        work.bind(player.playingWorkProperty());

        work.addListener((observableValue, works, t1) -> {
            if (t1 != null) {
                backgroundRefs.clear();
                currentBackground = null;
                backgroundIndex.set(0);
                initBackgrounds();
            }
        });
    }

    /**
     * 视图加入场景时补一次背景初始化：构造期的监听只在作品变化时触发，直接打开界面时需要主动加载。
     */
    @Override
    public void onViewAdded() {
        if (player.getPlayingMusic() != null) {
            initBackgrounds();
        }
    }

    /**
     * 视图移出场景的回调；属性绑定与背景记录都是长生命周期的，此处无需清理。
     */
    @Override
    public void onViewRemoved() {
    }

    /**
     * 播放下一曲。
     */
    public void next() {
        player.next();
    }

    /**
     * 播放上一曲。
     */
    public void pre() {
        player.pre();
    }

    /**
     * 播放或暂停，由按钮的选中状态驱动。
     */
    public void play() {
        player.playOrPause();
    }

    /**
     * 请求主界面展开播放列表。
     */
    public void playlist() {
        EventBus.publish("MAIN_PLAY_LIST");
    }

    /**
     * 跳转到指定播放位置。
     *
     * @param time 目标播放位置；进度条拖动传入的是由界面秒值包装的 {@link Duration}
     */
    public void seek(Duration time) {
        player.seek(time);
    }

    /**
     * 退出 Galgame 界面，请求主界面移除当前根视图。
     */
    public void back() {
        EventBus.publish("MAIN_ROOT_REMOVE");
    }

    /**
     * 切换到传统播放界面。
     */
    public void gotoPlayer() {
        EventBus.publish("TO_PLAY", 0);
    }

    /**
     * 预留的封面切换入口，当前无实现。
     */
    public void changeCover() {

    }

    /**
     * 保存一条 Galgame 存档：截图压缩为 180x300 的 PNG 落入 {@link AppPaths#saveDir()}，
     * 文件名取保存时刻的毫秒时间戳，同时把播放位置、当前字幕、选中背景等写入存档记录。
     * <p>
     * 截图写盘失败时仅打印异常，存档记录仍会写入（缩略图指向的文件不存在）；播放位置以毫秒保存。
     * 当前没有背景时不写 {@code background} 字段。
     *
     * @param tempImage 界面截图，由视图层在本方法调用前完成抓取
     */
    public void saveData(WritableImage tempImage) {
        String path = player.getPlayingWork().getFilepath();
        if (path != null) {
            Date date = new Date();
            SimpleDateFormat sf = new SimpleDateFormat("yyyy/MM/dd");
            File file = new File(AppPaths.saveDir(), date.getTime() + ".png");
            File parentFile = file.getParentFile();
            if (!parentFile.exists()) {
                parentFile.mkdirs();
            }
            try {
                ImageIO.write(
                        SwingFXUtils.fromFXImage(ImageUtils.resize(tempImage, 180, 300), null)
                        , "png", file
                );
            } catch (IOException e) {
                e.printStackTrace();
            }

            GalgameSaveData galgameSaveData = new GalgameSaveData();
            galgameSaveData.setTitle(URLUtils.getFileName(path));
            galgameSaveData.setChapter(URLUtils.getFileName(player.getPlayingMusic().getName()));
            galgameSaveData.setFilepath(player.getPlayingMusic().getUrl());
            if (currentBackground != null && currentBackground.path() != null) {
                galgameSaveData.setBackground(currentBackground.path());
            }
            galgameSaveData.setText(player.getLrcSelectedText());
            galgameSaveData.setThumb(file.getPath());
            galgameSaveData.setSaveDate(sf.format(date));
            galgameSaveData.setTime(Duration.seconds(player.getCurrentTime()).toMillis());

            AsmrWork work = workService.getByFilepath(player.getPlayingWork().getFilepath());
            galgameSaveData.setAsmrId(work.getId());
            saveDataService.addSave(galgameSaveData);
            EventBus.publish("MESSAGE", new MessageInfo(cn.tealc.asmr.model.message.MessageType.SUCCESS, "存档已保存"));

        }
    }


    /**
     * 加载作品当前背景：优先读取记录，未命中或读取失败时由服务重扫目录。
     */
    private void initBackgrounds() {
        AsmrWork work = player.getPlayingWork();
        if (work == null || work.getFilepath() == null) {
            applyDefaultBackground();
            return;
        }
        Thread.startVirtualThread(() -> {
            BackgroundService.LoadedBackground loaded = backgroundService.load(work);
            Image image = loaded == null ? null
                    : toImage(loaded.bytes(), backgroundWidth(), backgroundHeight());
            Platform.runLater(() -> {
                if (player.getPlayingWork() != work) {
                    return;
                }
                applyLoadedBackground(loaded, image);
            });
        });
    }

    private void applyLoadedBackground(BackgroundService.LoadedBackground loaded, Image image) {
        if (loaded == null || image == null) {
            currentBackground = null;
            applyDefaultBackground();
            return;
        }
        BackgroundRef ref = loaded.ref();
        currentBackground = ref;
        int index = backgroundRefs.indexOf(ref);
        if (index < 0) {
            backgroundRefs.setAll(ref);
            index = 0;
        }
        backgroundIndex.set(index);
        backgroundImage.set(image);
    }

    /**
     * 清空当前背景，并在开启 Galgame 背景模式时回退到用户设置的默认背景图。
     */
    private void applyDefaultBackground() {
        backgroundImage.set(null);
        if (!Config.setting.galgameBgModel.get()) {
            return;
        }
        String defaultBackground = ImageLoadUtils.getDefaultBackground();
        if (defaultBackground != null) {
            backgroundImage.set(new Image(defaultBackground,
                    backgroundWidth(), backgroundHeight(), true, true, true));
        }
    }

    /**
     * 循环切换到下一个背景：已有多个候选时直接轮播，否则先在虚拟线程重扫作品目录。
     * <p>
     * 扫描失败或候选不足一张时发送警告消息并保持当前背景不变。
     */
    public void changeBackground() {
        AsmrWork work = player.getPlayingWork();
        if (work == null) {
            return;
        }
        if (backgroundRefs.size() > 1) {
            advanceBackground(work);
            return;
        }
        Thread.startVirtualThread(() -> {
            List<BackgroundRef> candidates;
            try {
                candidates = backgroundService.scanCandidates(work);
            } catch (RuntimeException e) {
                LOG.warn("扫描背景失败: workId={}, path={}", work.getId(), work.getFilepath(), e);
                Platform.runLater(() -> EventBus.publish("MESSAGE",
                        new MessageInfo(cn.tealc.asmr.model.message.MessageType.WARNING, "背景扫描失败")));
                return;
            }
            Platform.runLater(() -> {
                if (player.getPlayingWork() != work) {
                    return;
                }
                if (candidates.size() <= 1) {
                    EventBus.publish("MESSAGE",
                            new MessageInfo(cn.tealc.asmr.model.message.MessageType.WARNING, "无其他背景，不可切换"));
                    return;
                }
                BackgroundRef current = currentBackground;
                backgroundRefs.setAll(candidates);
                backgroundIndex.set(current == null ? -1 : backgroundRefs.indexOf(current));
                advanceBackground(work);
            });
        });
    }

    /**
     * 直接切换到指定背景（背景缩略图栏点击），并把选择记为作品的默认背景。
     * <p>
     * {@code ref} 不在候选列表中时不改动索引，但仍会加载并记住它。
     *
     * @param ref 目标背景，为 null 或没有正在播放的作品时忽略本次调用
     */
    public void changeBackground(BackgroundRef ref) {
        AsmrWork work = player.getPlayingWork();
        if (work == null || ref == null) {
            return;
        }
        int index = backgroundRefs.indexOf(ref);
        if (index >= 0) {
            backgroundIndex.set(index);
        }
        Thread.startVirtualThread(() -> backgroundService.remember(work, ref));
        loadBackground(work, ref);
    }

    /**
     * 索引向后轮播（到末尾回到第一张）并记住、加载新背景；候选为空时不做任何事。
     */
    private void advanceBackground(AsmrWork work) {
        if (backgroundRefs.isEmpty()) {
            return;
        }
        int next = (backgroundIndex.get() + 1) % backgroundRefs.size();
        backgroundIndex.set(next);
        BackgroundRef ref = backgroundRefs.get(next);
        Thread.startVirtualThread(() -> backgroundService.remember(work, ref));
        loadBackground(work, ref);
    }

    /**
     * 在虚拟线程读取并解码背景，成功后回填 {@code backgroundImage}；读取失败发送“背景加载失败”警告。
     * <p>
     * 回填前校验当前播放作品，切歌后的迟到结果会被丢弃。
     */
    private void loadBackground(AsmrWork work, BackgroundRef ref) {
        Thread.startVirtualThread(() -> {
            byte[] bytes = backgroundService.read(work, ref);
            Image image = toImage(bytes, backgroundWidth(), backgroundHeight());
            Platform.runLater(() -> {
                if (player.getPlayingWork() != work) {
                    return;
                }
                if (image == null) {
                    EventBus.publish("MESSAGE",
                            new MessageInfo(cn.tealc.asmr.model.message.MessageType.WARNING, "背景加载失败"));
                    return;
                }
                currentBackground = ref;
                backgroundImage.set(image);
            });
        });
    }

    /**
     * 供界面在后台线程加载背景缩略图，按指定尺寸等比解码。
     *
     * @param ref    目标背景，可为 null
     * @param width  期望宽度，像素
     * @param height 期望高度，像素
     * @return 解码后的图片；没有正在播放的作品、{@code ref} 为 null 或读取解码失败时返回 null
     */
    public Image loadBackgroundImage(BackgroundRef ref, double width, double height) {
        AsmrWork work = player.getPlayingWork();
        if (work == null || ref == null) {
            return null;
        }
        return toImage(backgroundService.read(work, ref), width, height);
    }

    /**
     * 把背景字节解码为等比图片，空数据或解码失败返回 null（解码异常只记日志，不外抛）。
     */
    private Image toImage(byte[] bytes, double width, double height) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return new Image(new ByteArrayInputStream(bytes), width, height, true, true);
        } catch (RuntimeException e) {
            LOG.warn("背景图片解码失败", e);
            return null;
        }
    }

    /**
     * 背景解码的目标宽度，取自设置项，配置值非法时用 1920 兜底。
     */
    private double backgroundWidth() {
        return parseSize(Config.setting.galgameBgSizeWidth.get(), 1920);
    }

    /**
     * 背景解码的目标高度，取自设置项，配置值非法时用 1080 兜底。
     */
    private double backgroundHeight() {
        return parseSize(Config.setting.galgameBgSizeHeight.get(), 1080);
    }

    private static double parseSize(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }


    public boolean isDesktopLrc() {
        return desktopLrc.get();
    }

    public SimpleBooleanProperty desktopLrcProperty() {
        return desktopLrc;
    }

    public void setDesktopLrc(boolean desktopLrc) {
        this.desktopLrc.set(desktopLrc);
    }

    public String getTitle() {
        return title.get();
    }

    public SimpleStringProperty titleProperty() {
        return title;
    }

    public void setTitle(String title) {
        this.title.set(title);
    }

    public String getArtist() {
        return artist.get();
    }

    public SimpleStringProperty artistProperty() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist.set(artist);
    }

    public double getCurrentTime() {
        return currentTime.get();
    }

    public SimpleDoubleProperty currentTimeProperty() {
        return currentTime;
    }

    public void setCurrentTime(double currentTime) {
        this.currentTime.set(currentTime);
    }

    public double getTotalTime() {
        return totalTime.get();
    }

    public SimpleDoubleProperty totalTimeProperty() {
        return totalTime;
    }

    public void setTotalTime(double totalTime) {
        this.totalTime.set(totalTime);
    }

    public ObservableList<SubtitleCue> getLrcBeans() {
        return lrcBeans.get();
    }

    public SimpleObjectProperty<ObservableList<SubtitleCue>> lrcBeansProperty() {
        return lrcBeans;
    }

    public void setLrcBeans(ObservableList<SubtitleCue> lrcBeans) {
        this.lrcBeans.set(lrcBeans);
    }

    public Image getCover() {
        return cover.get();
    }

    public SimpleObjectProperty<Image> coverProperty() {
        return cover;
    }

    public void setCover(Image cover) {
        this.cover.set(cover);
    }

    public PlayerBindingSupport getPlayer() {
        return player;
    }

    public boolean isDisorder() {
        return disorder.get();
    }

    public SimpleBooleanProperty disorderProperty() {
        return disorder;
    }

    public void setDisorder(boolean disorder) {
        this.disorder.set(disorder);
    }

    public boolean isLoop() {
        return loop.get();
    }

    public SimpleBooleanProperty loopProperty() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop.set(loop);
    }

    public boolean isPlaying() {
        return playing.get();
    }

    public SimpleBooleanProperty playingProperty() {
        return playing;
    }

    public void setPlaying(boolean playing) {
        this.playing.set(playing);
    }

    public boolean isMute() {
        return mute.get();
    }

    public SimpleBooleanProperty muteProperty() {
        return mute;
    }

    public void setMute(boolean mute) {
        this.mute.set(mute);
    }

    public boolean isReady() {
        return ready.get();
    }

    public SimpleBooleanProperty readyProperty() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready.set(ready);
    }


    public String getCurrentLrc() {
        return currentLrc.get();
    }

    public SimpleStringProperty currentLrcProperty() {
        return currentLrc;
    }

    public void setCurrentLrc(String currentLrc) {
        this.currentLrc.set(currentLrc);
    }

    public int getCurrentLrcIndex() {
        return currentLrcIndex.get();
    }

    public SimpleIntegerProperty currentLrcIndexProperty() {
        return currentLrcIndex;
    }

    public void setCurrentLrcIndex(int currentLrcIndex) {
        this.currentLrcIndex.set(currentLrcIndex);
    }

    public Image getBackgroundImage() {
        return backgroundImage.get();
    }

    public SimpleObjectProperty<Image> backgroundImageProperty() {
        return backgroundImage;
    }

    public void setBackgroundImage(Image backgroundImage) {
        this.backgroundImage.set(backgroundImage);
    }

    public ObservableList<BackgroundRef> getBackgroundRefs() {
        return backgroundRefs;
    }
}