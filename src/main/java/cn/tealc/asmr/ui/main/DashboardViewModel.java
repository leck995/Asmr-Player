package cn.tealc.asmr.ui.main;

import cn.tealc.asmr.eventbus.EventBus;
import javafx.scene.layout.Pane;
import cn.tealc.asmr.mvvm.FluentViewLoader;
import cn.tealc.asmr.mvvm.ViewModel;
import cn.tealc.asmr.mvvm.ViewTuple;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.ui.dialog.Dialogs;
import cn.tealc.asmr.ui.player.AudioChannelView;
import cn.tealc.asmr.ui.player.AudioChannelViewModel;
import cn.tealc.asmr.ui.player.PlayerBindingSupport;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.image.Image;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * 底部播放控制条 ViewModel。
 * <p>
 * 持有 {@link PlayerBindingSupport} 并暴露播放器状态属性与命令方法，
 * 使 {@link DashboardView} 只做属性绑定与事件转发，不再直接操作播放器。
 *
 * @author Leck
 */
public class DashboardViewModel implements ViewModel {

    private final PlayerBindingSupport player = new PlayerBindingSupport();

    // ==================== 播放状态属性 ====================

    private final BooleanProperty playing = new SimpleBooleanProperty();
    private final BooleanProperty loop = new SimpleBooleanProperty();
    private final BooleanProperty disorder = new SimpleBooleanProperty();
    private final BooleanProperty mute = new SimpleBooleanProperty();
    private final BooleanProperty desktopLrcShow = new SimpleBooleanProperty();
    private final BooleanProperty ready = new SimpleBooleanProperty();

    // ==================== 媒体信息属性 ====================

    private final StringProperty title = new SimpleStringProperty();
    private final StringProperty artist = new SimpleStringProperty();
    private final ObjectProperty<Image> cover = new SimpleObjectProperty<>();

    // ==================== 时间/进度属性 ====================

    private final SimpleDoubleProperty currentTime = new SimpleDoubleProperty();
    private final SimpleDoubleProperty totalTime = new SimpleDoubleProperty();

    public DashboardViewModel() {
        playing.bindBidirectional(player.playingProperty());
        loop.bindBidirectional(player.loopProperty());
        disorder.bindBidirectional(player.disorderProperty());
        mute.bindBidirectional(player.muteProperty());
        desktopLrcShow.bindBidirectional(player.desktopLrcShowProperty());
        ready.bind(player.readyProperty());
        title.bindBidirectional(player.titleProperty());
        artist.bindBidirectional(player.artistProperty());
        cover.bindBidirectional(player.coverProperty());
        currentTime.bind(player.currentTimeProperty());
        totalTime.bind(player.totalTimeProperty());
    }

    // ==================== 命令方法 ====================

    /**
     * 切换播放/暂停，未就绪时不执行。
     */
    public void togglePlay() {
        if (!player.isReady()) {
            playing.set(false);
            return;
        }
        player.playOrPause();
    }

    /** 下一首。 */
    public void next() {
        player.next();
    }

    /** 上一首。 */
    public void pre() {
        player.pre();
    }

    /** 跳转到指定时间（秒）。 */
    public void seek(double seconds) {
        if (!player.isReady()) return;
        player.seek(Duration.seconds(seconds));
    }

    /** 打开播放列表。 */
    public void openPlaylist() {
        EventBus.publish(NotificationKey.MAIN_PLAY_LIST, false);
    }

    /** 跳转到详情播放界面。 */
    public void gotoDetail() {
        EventBus.publish(NotificationKey.TO_PLAY);
    }

    /** 打开音频声道设置弹窗。 */
    public void openAudioChannel() {
        ViewTuple<AudioChannelView, AudioChannelViewModel> viewTuple =
                FluentViewLoader.fxmlView(AudioChannelView.class).load();
        Dialogs.show(viewTuple.getCodeBehind());
    }

    // ==================== 属性访问器 ====================

    public boolean isPlaying() { return playing.get(); }
    public BooleanProperty playingProperty() { return playing; }
    public void setPlaying(boolean playing) { this.playing.set(playing); }

    public boolean isLoop() { return loop.get(); }
    public BooleanProperty loopProperty() { return loop; }
    public void setLoop(boolean loop) { this.loop.set(loop); }

    public boolean isDisorder() { return disorder.get(); }
    public BooleanProperty disorderProperty() { return disorder; }
    public void setDisorder(boolean disorder) { this.disorder.set(disorder); }

    public boolean isMute() { return mute.get(); }
    public BooleanProperty muteProperty() { return mute; }
    public void setMute(boolean mute) { this.mute.set(mute); }

    public boolean isDesktopLrcShow() { return desktopLrcShow.get(); }
    public BooleanProperty desktopLrcShowProperty() { return desktopLrcShow; }
    public void setDesktopLrcShow(boolean desktopLrcShow) { this.desktopLrcShow.set(desktopLrcShow); }

    public boolean isReady() { return ready.get(); }
    public BooleanProperty readyProperty() { return ready; }

    public String getTitle() { return title.get(); }
    public StringProperty titleProperty() { return title; }
    public void setTitle(String title) { this.title.set(title); }

    public String getArtist() { return artist.get(); }
    public StringProperty artistProperty() { return artist; }
    public void setArtist(String artist) { this.artist.set(artist); }

    public Image getCover() { return cover.get(); }
    public ObjectProperty<Image> coverProperty() { return cover; }
    public void setCover(Image cover) { this.cover.set(cover); }

    public double getCurrentTime() { return currentTime.get(); }
    public SimpleDoubleProperty currentTimeProperty() { return currentTime; }
    public void setCurrentTime(double currentTime) { this.currentTime.set(currentTime); }

    public double getTotalTime() { return totalTime.get(); }
    public SimpleDoubleProperty totalTimeProperty() { return totalTime; }
    public void setTotalTime(double totalTime) { this.totalTime.set(totalTime); }
}
