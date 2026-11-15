package cn.tealc.asmr.player;

import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.SubtitleCue;
import cn.tealc.asmr.model.AudioTrack;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.source.MediaSource;
import cn.tealc.asmr.source.MediaSourceFactory;
import cn.tealc.asmr.ui.player.DesktopLrcDialog;
import cn.tealc.asmr.utils.FileNameUtils;
import cn.tealc.asmr.utils.ImageLoadUtils;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.caprica.vlcj.media.Meta;
import uk.co.caprica.vlcj.player.base.AudioChannel;
import uk.co.caprica.vlcj.player.base.AudioDevice;
import uk.co.caprica.vlcj.player.base.MediaPlayer;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 基于 vlcj 的 VLC 播放器（唯一播放内核）—— 门面单例。
 * <p>
 * 实现 {@link IAudioPlayer} 接口，作为门面协调三个内部组件：
 * <ul>
 *   <li>{@link VlcEngine} —— VLC native 封装与事件监听</li>
 *   <li>{@link Playlist} —— 播放列表与索引管理</li>
 *   <li>{@link LrcSynchronizer} —— 歌词查询与时间同步</li>
 * </ul>
 * 本类保留所有 JavaFX 状态属性，实现 {@link VlcEngine.VlcEngineCallback}
 * 处理 native 事件回调。对外 API 完全不变，调用者零修改。
 * </p>
 * <p>
 * stale 守卫统一使用 {@code currentGeneration} 整数计数器，与 {@link VlcEngine}
 * 的 {@code loadGeneration} 联动，替代原有的 {@code loadingMusic} 引用比对。
 * </p>
 *
 * @author Leck
 */
public class VlcPlayer implements IAudioPlayer, VlcEngine.VlcEngineCallback {
    private static final Logger logger = LoggerFactory.getLogger(VlcPlayer.class);

    /** 默认封面图资源路径 */
    private static final String DEFAULT_COVER_URL =
            VlcPlayer.class.getResource("/cn/tealc/asmr/image/album.jpg").toExternalForm();

    // ==================== 单例 ====================

    private static volatile VlcPlayer instance;

    public static VlcPlayer getInstance() {
        if (instance == null) {
            synchronized (VlcPlayer.class) {
                if (instance == null) {
                    instance = new VlcPlayer();
                }
            }
        }
        return instance;
    }

    // ==================== 内部组件 ====================

    private final VlcEngine vlcEngine;
    private final Playlist playlist;
    private final LrcSynchronizer lrcSynchronizer;

    // ==================== JavaFX 状态属性 ====================

    private final BooleanProperty playing;
    private final BooleanProperty loop;
    private final BooleanProperty isStar;
    private final BooleanProperty mute;
    private final BooleanProperty desktopLrcShow;
    private final SimpleDoubleProperty currentTime;
    private final SimpleDoubleProperty totalTime;
    private final SimpleDoubleProperty bufferedTime;
    private final ObjectProperty<Image> cover;
    private final DoubleProperty volume;
    private final StringProperty title;
    private final StringProperty artist;
    private final SimpleObjectProperty<AudioTrack> playingMusic;
    private final SimpleObjectProperty<AsmrWork> playingWork;
    private final SimpleBooleanProperty ready;

    /**
     * 当前媒体加载世代，load() 时记录，回调时与 VlcEngine 的 loadGeneration 比对，
     * 不匹配则丢弃 stale 事件。
     */
    private volatile int currentGeneration = 0;

    // ==================== 构造函数 ====================

    private VlcPlayer() {
        logger.info("初始化 VlcPlayer");

        // 初始化 JavaFX 属性
        playingMusic = new SimpleObjectProperty<>();
        playing = new SimpleBooleanProperty(false);
        loop = new SimpleBooleanProperty(false);
        isStar = new SimpleBooleanProperty(false);
        mute = new SimpleBooleanProperty(false);
        cover = new SimpleObjectProperty<>(new Image(DEFAULT_COVER_URL));
        title = new SimpleStringProperty("音乐随心");
        artist = new SimpleStringProperty("Pure");
        totalTime = new SimpleDoubleProperty(0.0);
        currentTime = new SimpleDoubleProperty(Double.MAX_VALUE);
        bufferedTime = new SimpleDoubleProperty(0.0);
        volume = new SimpleDoubleProperty(1.0);
        desktopLrcShow = new SimpleBooleanProperty(false);
        ready = new SimpleBooleanProperty(false);

        playingWork = new SimpleObjectProperty<>();
        playingWork.bind(Bindings.createObjectBinding(() -> {
            if (playingMusic.get() != null) {
                return playingMusic.get().getWork();
            }
            return null;
        }, playingMusic));

        // 初始化内部组件
        playlist = new Playlist();
        lrcSynchronizer = new LrcSynchronizer(playingMusic, currentTime, playingWork);
        vlcEngine = new VlcEngine(this);

        initCommonListeners();
        vlcEngine.applyVolume(getVolume());
    }

    // ==================== 公共监听器（JavaFX 属性 → native 操作映射） ====================

    private void initCommonListeners() {
        // 播放/暂停状态变更 → 驱动 native 播放器
        // 依赖 JavaFX 属性"值未变不触发 listener"特性 + vlcEngine 操作幂等性，
        // 无需 internalUpdate 防循环标志：native onPlaying 回调设 playing=true 时，
        // 若值已为 true 则不触发 listener；若 false→true 则 listener 调 vlcEngine.play()，
        // 但 VLC 已在播放（onPlaying 就是 VLC 开始播放时触发的），controls().play() 幂等，无副作用。
        playing.addListener((observableValue, aBoolean, t1) -> {
            if (t1) {
                vlcEngine.play();
            } else {
                vlcEngine.pause();
            }
        });

        // 音量与全局配置双向绑定（统一 0-1 范围）
        volume.bindBidirectional(Config.setting.volume);
        volume.addListener((observable, oldValue, newValue) -> {
            setMute(newValue.doubleValue() == 0);
            vlcEngine.applyVolume(newValue.doubleValue());
        });

        // 静音状态 → 驱动 native 播放器
        mute.addListener((observableValue, aBoolean, t1) -> vlcEngine.applyMute(t1));

        // 桌面歌词：懒加载，第一次 set(true) 时才创建 DesktopLrcDialog 单例，
        // 避免未使用时占用内存。之后由 DesktopLrcDialog 自身 listener 响应
        desktopLrcShow.addListener((obs, old, show) -> {
            if (show) DesktopLrcDialog.getInstance();
        });
    }

    // ==================== VlcEngineCallback 实现 ====================

    public void onTimeChanged(double currentTimeSeconds, int generation) {
        if (generation != currentGeneration) return;
        Platform.runLater(() -> setCurrentTime(currentTimeSeconds));
    }

    public void onFinished(int generation) {
        if (generation != currentGeneration) return;
        Platform.runLater(() -> {
            if (isLoop()) {
                vlcEngine.stopAndPlay();
            } else {
                if (Config.setting.stopPlayOnEnd.get()
                        && playlist.getMusicIndex() == playlist.getMusics().size() - 1) {
                    setPlaying(false);
                } else {
                    next();
                }
            }
        });

        vlcEngine.submit(() -> {
            if (getAudioDevice() != null) {
                Config.setting.setAudioChannel(0);
                setAudioChannel(AudioChannel.UNSET);
            }
        });
    }

    public void onPlaying(MediaPlayer mp, int generation) {
        if (generation != currentGeneration) return;

        // 在 native 线程读取 meta（日志证实 taglib meta reader 在 onPlaying 之前即完成）
        String metaTitle;
        String metaArtist;
        if (isOnline()) {
            String filePath = URLDecoder.decode(mp.media().info().mrl(), StandardCharsets.UTF_8);
            if (getPlayingMusic().getHideName() != null) {
                metaTitle = getPlayingMusic().getHideName().substring(0, getPlayingMusic().getHideName().lastIndexOf("."));
            } else {
                metaTitle = filePath.substring(filePath.lastIndexOf("/") + 1, filePath.lastIndexOf("."));
            }
            metaArtist = "";
        } else {
            String artist = mp.media().meta().get(Meta.ARTIST);
            String musicName = mp.media().meta().get(Meta.TITLE);
            if (musicName != null && !musicName.isEmpty()) {
                metaTitle = musicName;
            } else {
                String hideName = getPlayingMusic() != null ? getPlayingMusic().getHideName() : null;
                metaTitle = FileNameUtils.getNameWithSuffix(hideName);
            }
            metaArtist = (artist != null ? artist : "");
        }
        String finalMetaTitle = metaTitle;
        String finalMetaArtist = metaArtist;
        String imageUrl = mp.media().meta().get(Meta.ARTWORK_URL);
        double duration = (double) mp.media().info().duration() / 1000;

        // 加载封面（虚拟线程，避免阻塞 native 事件队列导致切歌 play() 被阻塞）
        Thread.startVirtualThread(() -> {
            if (generation != currentGeneration) return;
            Image coverImage;
            if (imageUrl != null) {
                coverImage = new Image(imageUrl);
            } else {
                coverImage = ImageLoadUtils.getCoverImage(getPlayingWork().getFilepath());
            }
            if (coverImage != null && (getCover().getUrl() == null || !coverImage.getUrl().equals(getCover().getUrl()))) {
                Platform.runLater(() -> {
                    if (generation != currentGeneration) return;
                    setCover(coverImage);
                });
            }
        });

        Platform.runLater(() -> {
            if (generation != currentGeneration) return;
            playing.set(true);
            setReady(true);
            setTitle(finalMetaTitle);
            setArtist(finalMetaArtist);
            setTotalTime(duration);
        });
    }

    public void onOpening(int generation) {
        if (generation != currentGeneration) return;
        Platform.runLater(() -> {
            setTitle("加载中");
            setArtist("请稍后");
        });
    }

    public void onMediaPlayerReady(MediaPlayer mp, int generation) {
        if (generation != currentGeneration) return;

        // 恢复音频输出设备与均衡器（native 线程）
        vlcEngine.submit(() -> {
            if (generation != currentGeneration) return;
            if (getAudioDevice() != null) {
                vlcEngine.restoreAudioOutput(getAudioDevice(), getAudioEffect());
            }
        });
    }

    public void onError(int generation) {
        Platform.runLater(() -> setReady(false));
    }

    public void onBuffering(float cache, int generation) {
        if (generation != currentGeneration) return;
        Platform.runLater(() -> setBufferedTime(cache));
    }

    // ==================== 播放列表操作 ====================

    public void init(List<AudioTrack> musics) {
        playlist.init(musics);
    }

    public void init(List<AudioTrack> musics, int index) {
        playlist.init(musics);
        playlist.setMusicIndex(index);
        load(playlist.getMusics().get(index), false, Duration.ZERO);
    }

    public void initAndPlay(List<AudioTrack> musics, int index) {
        initAndPlay(musics, index, true, Duration.ZERO);
    }

    @Override
    public void initAndPlay(List<AudioTrack> musics, int index, boolean autoPlay, Duration time) {
        playlist.init(musics);
        playlist.setMusicIndex(index);
        load(playlist.getMusics().get(index), autoPlay, time);
    }

    @Override
    public void add(AudioTrack music) {
        playlist.add(music);
    }

    public void add(List<AudioTrack> musics) {
        playlist.add(musics);
    }

    @Override
    public void addAndPlay(List<AudioTrack> musics, int index) {
        AudioTrack music = musics.get(index);
        playlist.add(musics);
        int newIndex = playlist.getMusics().indexOf(music);
        playlist.setMusicIndex(newIndex);
        load(playlist.getMusics().get(newIndex), true, Duration.ZERO);
    }

    public void addAndPlay(AudioTrack music) {
        addAndPlay(List.of(music), 0);
    }

    @Override
    public void removeMusic(int index) {
        boolean emptied = playlist.removeMusic(index);
        if (emptied) {
            clearPlayingList();
        } else if (index == playlist.getMusicIndex()) {
            // 移除的是当前播放曲目，切到下一首
            next();
        }
    }

    @Override
    public void clearPlayingList() {
        // 先停止 native 播放，再清空列表与歌词，最后重置 UI
        vlcEngine.stop();
        playlist.clear();
        lrcSynchronizer.clear();
        dispose();
    }

    // ==================== 播放控制 ====================

    public void play(int index) {
        play(index, true, Duration.ZERO);
    }

    public void play(int index, Duration time) {
        play(index, true, time);
    }

    private void play(int index, boolean autoPlay, Duration time) {
        playlist.setMusicIndex(index);
        load(playlist.getMusics().get(index), autoPlay, time);
    }

    /** 播放当前曲目。 */
    public void play() {
        if (isReady()) {
            vlcEngine.play();
        }
    }

    /** 暂停当前曲目。 */
    public void pause() {
        if (isReady()) {
            vlcEngine.pause();
        }
    }

    @Override
    public void playOrPause() {
        if (isReady()) {
            if (isPlaying()) {
                vlcEngine.pause();
            } else {
                vlcEngine.play();
            }
        }
    }

    @Override
    public void pre() {
        if (playlist.getMusics().isEmpty()) return;
        playlist.moveToPrev();
        play(playlist.getMusicIndex());
    }

    @Override
    public void next() {
        if (playlist.getMusics().isEmpty()) return;
        playlist.moveToNext();
        play(playlist.getMusicIndex());
    }

    @Override
    public void load(AudioTrack music, boolean autoPlay, Duration time) {
        // 立即在 FX 线程刷新 UI，不等 native opening 事件
        currentGeneration++;
        setPlayingMusic(music);
        setReady(false);
        setTitle("加载中");
        setArtist("请稍后");
        setCurrentTime(0.0);
        setTotalTime(0.0);
        setBufferedTime(0.0);

        String playUrl = resolvePlayUrl(music);
        vlcEngine.load(playUrl, LoadOptions.of(autoPlay, time));
    }

    @Override
    public void seek(Duration time) {
        vlcEngine.seek((long) time.toMillis());
    }

    @Override
    public void skipTime(Duration time) {
        vlcEngine.skipTime((long) time.toMillis());
    }

    /**
     * 停止播放并重置 UI 状态。
     * <p>
     * 语义为"停止播放+重置状态"，native 停止由 {@link #clearPlayingList()} 或
     * 上层显式调用 {@link VlcEngine#stop()} 负责。
     * </p>
     */
    @Override
    public void dispose() {
        setPlaying(false);
        setReady(false);
        setCurrentTime(0.0);
        setCover(new Image(DEFAULT_COVER_URL));
        setTitle("音乐随心");
        setArtist("Pure");
        setTotalTime(0);
        setPlayingMusic(null);
    }

    @Override
    public void release() {
        vlcEngine.release();
    }

    // ==================== STRM 解析 ====================

    private String resolvePlayUrl(AudioTrack music) {
        if (music.getWork() == null) {
            return music.getUrl();
        }
        MediaSource source = MediaSourceFactory.forWork(music.getWork());
        return source.resolvePlayUrl(music.getUrl());
    }

    // ==================== 声道/设备/均衡器 ====================

    private final SimpleObjectProperty<AudioChannel> audioChannel = new SimpleObjectProperty<>();
    private final SimpleObjectProperty<AudioDevice> audioDevice = new SimpleObjectProperty<>();
    private final SimpleObjectProperty<AudioEffect> audioEffect = new SimpleObjectProperty<>();

    @Override
    public void setAudioChannel(AudioChannel channel) {
        this.audioChannel.set(channel);
        vlcEngine.setAudioChannel(channel);
    }

    public AudioChannel getAudioChannel() {
        return audioChannel.get();
    }

    @Override
    public void setAudioDevice(AudioDevice device) {
        this.audioDevice.set(device);
        vlcEngine.setAudioDevice(device);
    }

    public AudioDevice getAudioDevice() {
        return audioDevice.get();
    }

    @Override
    public List<AudioDevice> getAllAudioDevices() {
        return vlcEngine.getAllAudioDevices();
    }

    @Override
    public void setAudioEffect(AudioEffect audioEffect) {
        this.audioEffect.set(audioEffect);
        vlcEngine.setAudioEffect(audioEffect);
    }

    public AudioEffect getAudioEffect() {
        return audioEffect.get();
    }

    // ==================== 辅助方法 ====================

    private boolean isOnline() {
        AudioTrack music = getPlayingMusic();
        return music != null && music.getWork() != null && music.getWork().getStorageType() != 0;
    }

    public boolean isReady() {
        return ready.get();
    }

    // ==================== 访问器方法 ====================

    public boolean isPlaying() { return playing.get(); }
    public BooleanProperty playingProperty() { return playing; }
    public void setPlaying(boolean playing) { this.playing.set(playing); }

    public boolean isDisorder() { return playlist.isDisorder(); }
    public BooleanProperty disorderProperty() { return playlist.disorderProperty(); }
    public void setDisorder(boolean disorder) { playlist.setDisorder(disorder); }

    public boolean isLoop() { return loop.get(); }
    public BooleanProperty loopProperty() { return loop; }
    public void setLoop(boolean loop) { this.loop.set(loop); }

    public boolean isIsStar() { return isStar.get(); }
    public BooleanProperty isStarProperty() { return isStar; }
    public void setIsStar(boolean isStar) { this.isStar.set(isStar); }

    public boolean isMute() { return mute.get(); }
    public BooleanProperty muteProperty() { return mute; }
    public void setMute(boolean mute) { this.mute.set(mute); }

    public boolean isDesktopLrcShow() { return desktopLrcShow.get(); }
    public BooleanProperty desktopLrcShowProperty() { return desktopLrcShow; }
    public void setDesktopLrcShow(boolean desktopLrcShow) { this.desktopLrcShow.set(desktopLrcShow); }

    public double getCurrentTime() { return currentTime.get(); }
    public SimpleDoubleProperty currentTimeProperty() { return currentTime; }
    public void setCurrentTime(double currentTime) { this.currentTime.set(currentTime); }

    public double getTotalTime() { return totalTime.get(); }
    public SimpleDoubleProperty totalTimeProperty() { return totalTime; }
    public void setTotalTime(double totalTime) { this.totalTime.set(totalTime); }

    public double getBufferedTime() { return bufferedTime.get(); }
    public SimpleDoubleProperty bufferedTimeProperty() { return bufferedTime; }
    public void setBufferedTime(double bufferedTime) { this.bufferedTime.set(bufferedTime); }

    public Image getCover() { return cover.get(); }
    public ObjectProperty<Image> coverProperty() { return cover; }
    public void setCover(Image cover) { this.cover.set(cover); }

    public double getVolume() { return volume.get(); }
    public DoubleProperty volumeProperty() { return volume; }
    public void setVolume(double volume) { this.volume.set(volume); }

    public String getTitle() { return title.get(); }
    public StringProperty titleProperty() { return title; }
    public void setTitle(String title) { this.title.set(title); }

    public String getArtist() { return artist.get(); }
    public StringProperty artistProperty() { return artist; }
    public void setArtist(String artist) { this.artist.set(artist); }

    public ObservableList<AudioTrack> getMusics() { return playlist.getMusics(); }

    /**
     * 获取当前播放曲目在列表中的索引。
     *
     * @return 当前播放索引，-1 表示列表为空
     */
    public int getPlayingIndexInList() { return playlist.getMusicIndex(); }

    public ObservableList<SubtitleCue> getLrcBeans() { return lrcSynchronizer.getLrcBeans(); }
    public ObjectProperty<ObservableList<SubtitleCue>> lrcBeansProperty() { return lrcSynchronizer.lrcBeansProperty(); }
    public void setLrcBeans(ObservableList<SubtitleCue> lrcBeans) { lrcSynchronizer.lrcBeansProperty().set(lrcBeans); }

    public int getLrcSelectedIndex() { return lrcSynchronizer.getLrcSelectedIndex(); }
    public SimpleIntegerProperty lrcSelectedIndexProperty() { return lrcSynchronizer.lrcSelectedIndexProperty(); }
    public void setLrcSelectedIndex(int lrcSelectedIndex) { lrcSynchronizer.lrcSelectedIndexProperty().set(lrcSelectedIndex); }

    public String getLrcSelectedText() { return lrcSynchronizer.getLrcSelectedText(); }
    public SimpleStringProperty lrcSelectedTextProperty() { return lrcSynchronizer.lrcSelectedTextProperty(); }
    public void setLrcSelectedText(String lrcSelectedText) { lrcSynchronizer.lrcSelectedTextProperty().set(lrcSelectedText); }

    public AudioTrack getPlayingMusic() { return playingMusic.get(); }
    public SimpleObjectProperty<AudioTrack> playingMusicProperty() { return playingMusic; }
    public void setPlayingMusic(AudioTrack playingMusic) { this.playingMusic.set(playingMusic); }

    public BooleanProperty readyProperty() { return ready; }
    public void setReady(boolean ready) { this.ready.set(ready); }

    public int getMusicIndex() { return playlist.getMusicIndex(); }
    public SimpleIntegerProperty musicIndexProperty() { return playlist.musicIndexProperty(); }
    public void setMusicIndex(int musicIndex) { playlist.setMusicIndex(musicIndex); }

    public AsmrWork getPlayingWork() { return playingWork.get(); }
    public SimpleObjectProperty<AsmrWork> playingWorkProperty() { return playingWork; }
}
