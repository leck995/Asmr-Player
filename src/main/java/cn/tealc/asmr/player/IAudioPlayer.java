package cn.tealc.asmr.player;

import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.SubtitleCue;
import cn.tealc.asmr.model.AudioTrack;
import javafx.beans.property.*;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;
import javafx.util.Duration;
import uk.co.caprica.vlcj.player.base.AudioChannel;
import uk.co.caprica.vlcj.player.base.AudioDevice;

import java.util.List;

/**
 * 音频播放器接口（精简版，JavaFX 友好）。
 * <p>
 * 定义播放器契约：播放控制、播放列表管理、状态查询（JavaFX Property）、
 * 以及 VLC 特有的声道/设备/均衡器控制。
 * 唯一实现为 {@link VlcPlayer}。
 * </p>
 * <p>
 * 移除了冗余的值 getter（与 property accessor 重复）和错误的伪 property
 * 方法（{@code audioChannelProperty()} 等实为 getter 却命名为 property）。
 * </p>
 *
 * @author Leck
 */
public interface IAudioPlayer {

    // ==================== 播放控制 ====================

    void playOrPause();

    void next();

    void pre();

    void seek(Duration time);

    void skipTime(Duration time);

    void load(AudioTrack music, boolean autoPlay, Duration time);

    // ==================== 播放列表操作 ====================

    void initAndPlay(List<AudioTrack> musics, int index, boolean autoPlay, Duration time);

    void addAndPlay(List<AudioTrack> musics, int index);

    void add(AudioTrack music);

    void removeMusic(int index);

    void clearPlayingList();

    int getMusicIndex();

    int getPlayingIndexInList();

    // ==================== 生命周期 ====================

    void dispose();

    void release();

    // ==================== 声道/设备/均衡器 ====================

    void setAudioChannel(AudioChannel channel);

    void setAudioDevice(AudioDevice device);

    void setAudioEffect(AudioEffect audioEffect);

    List<AudioDevice> getAllAudioDevices();

    // ==================== JavaFX 状态属性 ====================

    BooleanProperty playingProperty();

    BooleanProperty readyProperty();

    DoubleProperty currentTimeProperty();

    DoubleProperty totalTimeProperty();

    DoubleProperty volumeProperty();

    BooleanProperty muteProperty();

    ObjectProperty<Image> coverProperty();

    StringProperty titleProperty();

    StringProperty artistProperty();

    ObjectProperty<AudioTrack> playingMusicProperty();

    ObjectProperty<AsmrWork> playingWorkProperty();

    BooleanProperty loopProperty();

    BooleanProperty disorderProperty();

    BooleanProperty desktopLrcShowProperty();

    ObservableList<AudioTrack> getMusics();

    ObjectProperty<ObservableList<SubtitleCue>> lrcBeansProperty();

    IntegerProperty lrcSelectedIndexProperty();

    StringProperty lrcSelectedTextProperty();
}
