package cn.tealc.asmr.ui.player;

import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.SubtitleCue;
import cn.tealc.asmr.model.AudioTrack;
import cn.tealc.asmr.player.VlcPlayer;
import javafx.beans.property.*;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;

/**
 * 播放器属性绑定支持类，封装从 {@link VlcPlayer} 单例拉取全部标准属性引用的逻辑。
 * <p>
 * 消费方（PlayerDetailViewModel / GalgameDetailViewModel / DashboardView 等）
 * 持有此工具类后可直接引用 player 属性进行绑定，避免三处重复编写
 * {@code VlcPlayer.getInstance().xxxProperty()} 调用链。
 * </p>
 * <p>
 * 此类不持有任何自身状态，仅转发 player 的属性引用，无内存泄漏风险。
 * </p>
 *
 * @author Leck
 */
public final class PlayerBindingSupport {

    private final VlcPlayer player;

    /**
     * 构造并绑定到全局 {@link VlcPlayer} 单例。
     */
    public PlayerBindingSupport() {
        this.player = VlcPlayer.getInstance();
    }

    // ==================== 播放状态属性 ====================

    public BooleanProperty playingProperty() { return player.playingProperty(); }
    public BooleanProperty readyProperty() { return player.readyProperty(); }
    public BooleanProperty loopProperty() { return player.loopProperty(); }
    public BooleanProperty disorderProperty() { return player.disorderProperty(); }
    public BooleanProperty muteProperty() { return player.muteProperty(); }
    public BooleanProperty desktopLrcShowProperty() { return player.desktopLrcShowProperty(); }

    // ==================== 时间/进度属性 ====================

    public DoubleProperty currentTimeProperty() { return player.currentTimeProperty(); }
    public DoubleProperty totalTimeProperty() { return player.totalTimeProperty(); }
    public DoubleProperty bufferedTimeProperty() { return player.bufferedTimeProperty(); }
    public DoubleProperty volumeProperty() { return player.volumeProperty(); }

    // ==================== 媒体信息属性 ====================

    public ObjectProperty<Image> coverProperty() { return player.coverProperty(); }
    public StringProperty titleProperty() { return player.titleProperty(); }
    public StringProperty artistProperty() { return player.artistProperty(); }
    public ObjectProperty<AudioTrack> playingMusicProperty() { return player.playingMusicProperty(); }
    public ObjectProperty<AsmrWork> playingWorkProperty() { return player.playingWorkProperty(); }

    // ==================== 歌词属性 ====================

    public ObjectProperty<ObservableList<SubtitleCue>> lrcBeansProperty() { return player.lrcBeansProperty(); }
    public IntegerProperty lrcSelectedIndexProperty() { return player.lrcSelectedIndexProperty(); }
    public StringProperty lrcSelectedTextProperty() { return player.lrcSelectedTextProperty(); }

    // ==================== 播放列表 ====================

    public ObservableList<AudioTrack> getMusics() { return player.getMusics(); }
    public IntegerProperty musicIndexProperty() { return player.musicIndexProperty(); }

    // ==================== 命令转发 ====================

    public void playOrPause() { player.playOrPause(); }
    public void next() { player.next(); }
    public void pre() { player.pre(); }
    public void seek(javafx.util.Duration time) { player.seek(time); }

    // ==================== 状态查询 ====================

    public boolean isReady() { return player.isReady(); }
    public double getCurrentTime() { return player.getCurrentTime(); }
    public double getTotalTime() { return player.getTotalTime(); }
    public AudioTrack getPlayingMusic() { return player.getPlayingMusic(); }
    public AsmrWork getPlayingWork() { return player.getPlayingWork(); }
    public String getLrcSelectedText() { return player.getLrcSelectedText(); }
    public int getMusicIndex() { return player.getMusicIndex(); }
    public int getPlayingIndexInList() { return player.getPlayingIndexInList(); }
}
