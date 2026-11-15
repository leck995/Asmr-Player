package cn.tealc.asmr.ui.player;

import cn.tealc.asmr.eventbus.EventBus;


import cn.tealc.asmr.di.Inject;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.BackgroundRef;
import cn.tealc.asmr.model.SubtitleCue;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.service.BackgroundService;
import cn.tealc.asmr.task.player.PlayerBackgroundTask;
import cn.tealc.asmr.mvvm.SceneLifecycle;
import cn.tealc.asmr.mvvm.ViewModel;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;
import javafx.scene.layout.Background;
import javafx.util.Duration;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;


/**
 * 播放详情页的 ViewModel，把播放器状态转发为可绑定的 JavaFX 属性并封装播放命令。
 * <p>
 * 属性本身不存状态：全部通过 {@link PlayerBindingSupport} 与 {@code VlcPlayer} 的
 * 属性双向或单向绑定（{@code desktopLrc}/{@code disorder}/{@code loop}/
 * {@code playing}/{@code mute}/标题/时长/封面 等为双向，
 * {@code ready}/{@code currentTime}/{@code totalTime}/{@code lrcBeans}/{@code work}
 * 为只读单向），因此属性访问器必须视为播放器视图而非本地字段。
 * <p>
 * 封面与背景走 {@link BackgroundService}：候选背景列表 {@code coverRefs} 与当前索引
 * 在切换时惰性扫描，读取与模糊处理在虚拟线程完成，回写前用
 * {@code player.getPlayingWork() != work} 丢弃已切歌的过期结果。
 * 背景属性 {@code backgroud}（拼写沿用字段名）由 {@link PlayerBackgroundTask} 生成。
 *
 * @author Leck
 */
public class PlayerDetailViewModel implements ViewModel, SceneLifecycle {
    private SimpleBooleanProperty desktopLrc, disorder, loop, playing, mute, ready;
    private SimpleStringProperty title;
    private SimpleStringProperty artist;

    private SimpleDoubleProperty currentTime;
    private SimpleDoubleProperty totalTime;
    private SimpleObjectProperty<ObservableList<SubtitleCue>> lrcBeans;

    private final List<BackgroundRef> coverRefs = new ArrayList<>();
    private volatile BackgroundRef currentCover;
    private SimpleIntegerProperty coverIndex;
    private ObjectProperty<Image> cover;

    private SimpleObjectProperty<Background> backgroud;
    private PlayerBindingSupport player;

    private SimpleObjectProperty<AsmrWork> work;

    @Inject
    private BackgroundService backgroundService;

    /**
     * 创建全部属性并完成与播放器属性的绑定。
     * <p>
     * 换作品（{@code work} 变化）时清空候选背景列表重新加载；构造末尾对当前封面
     * 再生成一次背景（封面可能仍为 null，任务内部异常会被 {@code Task} 捕获，不影响构造）。
     */
    public PlayerDetailViewModel() {
        desktopLrc = new SimpleBooleanProperty();
        disorder = new SimpleBooleanProperty();
        loop = new SimpleBooleanProperty();
        playing = new SimpleBooleanProperty();
        mute = new SimpleBooleanProperty();
        ready = new SimpleBooleanProperty();

        title = new SimpleStringProperty();
        artist = new SimpleStringProperty();
        currentTime = new SimpleDoubleProperty();
        totalTime = new SimpleDoubleProperty();
        lrcBeans = new SimpleObjectProperty<>();
        cover = new SimpleObjectProperty<>();
        backgroud = new SimpleObjectProperty<>();
        work = new SimpleObjectProperty<>();
        coverIndex = new SimpleIntegerProperty();
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
        currentTime.bind(player.currentTimeProperty());
        totalTime.bind(player.totalTimeProperty());
        cover.bindBidirectional(player.coverProperty());
        lrcBeans.bind(player.lrcBeansProperty());
        work.bind(player.playingWorkProperty());

        work.addListener((observableValue, works, t1) -> {
            if (t1 != null) {
                coverRefs.clear();
                currentCover = null;
                coverIndex.set(0);
                initCovers();
            }
        });

        updateBackground(cover.get());
    }

    /**
     * 页面挂载时补加载封面候选：构造期绑定的 {@code work} 变化不会再触发，
     * 已有正在播放曲目时主动扫描一次。
     */
    @Override
    public void onViewAdded() {
        if (player.getPlayingMusic() != null) {
            initCovers();
        }
    }

    /**
     * 页面卸载时不做任何释放：与播放器单例的绑定常驻，无需随页面解绑。
     */
    @Override
    public void onViewRemoved() {
    }

    /**
     * 后台加载当前作品的封面/背景并登记为首个候选。
     * <p>
     * 作品为空或无路径时直接返回；回写前校验播放作品未变更，避免切歌后写入旧图。
     */
    private void initCovers() {
        AsmrWork work = player.getPlayingWork();
        if (work == null || work.getFilepath() == null) {
            return;
        }
        Thread.startVirtualThread(() -> {
            BackgroundService.LoadedBackground loaded = backgroundService.load(work);
            Platform.runLater(() -> {
                if (player.getPlayingWork() != work) {
                    return;
                }
                coverRefs.clear();
                if (loaded != null) {
                    coverRefs.add(loaded.ref());
                    currentCover = loaded.ref();
                    coverIndex.set(0);
                }
            });
        });
    }


    /**
     * 切换到下一张封面/背景。
     * <p>
     * 候选列表已有多于一项时直接推进索引；否则先在虚拟线程扫描作品目录，
     * 扫描抛异常或候选只有一项时发布 {@code MESSAGE} 警告并放弃切换。
     * 无正在播放的作品时静默返回。
     * <p>
     * 扫描结果回到 FX 线程后再次校验播放作品未变更，然后把当前图所在位置写入
     * {@code coverIndex} 并调用 {@link #advanceCover(AsmrWork)} 完成切换。
     */
    public void nextCover() {
        AsmrWork work = player.getPlayingWork();
        if (work == null) {
            return;
        }
        if (coverRefs.size() > 1) {
            advanceCover(work);
            return;
        }
        Thread.startVirtualThread(() -> {
            List<BackgroundRef> candidates;
            try {
                candidates = backgroundService.scanCandidates(work);
            } catch (RuntimeException e) {
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
                BackgroundRef current = currentCover;
                coverRefs.clear();
                coverRefs.addAll(candidates);
                coverIndex.set(current == null ? -1 : coverRefs.indexOf(current));
                advanceCover(work);
            });
        });
    }

    /**
     * 按环形索引推进到下一张候选并加载图片。
     * <p>
     * 候选列表为空时不做事；切换后在虚拟线程记住用户选择（{@code remember}）
     * 并读取图片字节，回到 FX 线程时丢弃已切歌或解码失败的结果。
     *
     * @param work 发起切换时正在播放的作品，用于校验结果是否过期
     */
    private void advanceCover(AsmrWork work) {
        if (coverRefs.isEmpty()) {
            return;
        }
        int next = (coverIndex.get() + 1) % coverRefs.size();
        coverIndex.set(next);
        BackgroundRef ref = coverRefs.get(next);
        Thread.startVirtualThread(() -> backgroundService.remember(work, ref));
        Thread.startVirtualThread(() -> {
            byte[] bytes = backgroundService.read(work, ref);
            Image image = toImage(bytes);
            Platform.runLater(() -> {
                if (player.getPlayingWork() != work) {
                    return;
                }
                if (image != null) {
                    currentCover = ref;
                    cover.set(image);
                    updateBackground(image);
                }
            });
        });
    }

    /**
     * 把背景图片字节解码为封面图。
     *
     * @param bytes 图片二进制，可为 null
     * @return 最长边不超过 650 像素、保持宽高比的图片；字节为空或解码失败时返回 null
     */
    private Image toImage(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return new Image(new ByteArrayInputStream(bytes), 650, 650, true, true);
        } catch (RuntimeException e) {
            return null;
        }
    }


    /**
     * 由封面图生成页面背景并写入 {@code backgroud} 属性。
     * <p>
     * 注意此处直接调用 {@code task.run()}，{@link PlayerBackgroundTask} 的缩放、
     * 高斯模糊与亮度调整是在当前调用线程同步完成的（并未另起线程），
     * 成功回调也随之同步触发；{@code image} 为 null 时任务内部异常被捕获，
     * 背景保持上一次的值。
     *
     * @param image 作为背景来源的封面图，可为 null
     */
    public void updateBackground(Image image) {
        PlayerBackgroundTask task = new PlayerBackgroundTask(image);
        task.setOnSucceeded(workerStateEvent -> {
            backgroud.set(task.getValue());
        });
        task.run();
    }


    /**
     * 切到下一首并立即播放，由播放队列决定实际曲目；队列为空时无操作。
     */
    public void next() {
        player.next();
    }

    /**
     * 切到上一首并立即播放；队列为空时无操作。
     */
    public void pre() {
        player.pre();
    }

    /**
     * 播放/暂停切换，播放器未就绪时不产生任何动作。
     */
    public void play() {
        player.playOrPause();
    }

    /**
     * 请求主视图弹出播放队列面板（非 Galgame 样式）。
     */
    public void playlist() {
        EventBus.publish(NotificationKey.MAIN_PLAY_LIST, false);
    }

    /**
     * 跳转到指定播放位置。
     *
     * @param time 目标位置，单位为秒（与 {@code currentTime}/{@code totalTime} 同一量纲），
     *             包装为 {@link Duration} 后下发；传 null 会拆箱抛空指针
     */
    public void seek(Double time) {
        player.seek(Duration.seconds(time));
    }

    /**
     * 请求主视图移除播放页覆盖层，回到作品列表。
     */
    public void back() {
        EventBus.publish("MAIN_ROOT_REMOVE");
    }

    /**
     * 请求把播放页切换为 Galgame 模式（{@code TO_PLAY} 事件携带参数 1）。
     */
    public void gotoGalgame() {
        EventBus.publish("TO_PLAY", 1);
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

    public IntegerProperty lrcSelectedIndexProperty() {
        return player.lrcSelectedIndexProperty();
    }

    public Image getCover() {
        return cover.get();
    }

    public ObjectProperty<Image> coverProperty() {
        return cover;
    }

    public void setCover(Image cover) {
        this.cover.set(cover);
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

    public Background getBackgroud() {
        return backgroud.get();
    }

    public SimpleObjectProperty<Background> backgroudProperty() {
        return backgroud;
    }
}