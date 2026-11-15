package cn.tealc.asmr.ui.player;

import cn.tealc.asmr.player.AudioEffect;
import cn.tealc.asmr.player.VlcPlayer;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.mvvm.ViewModel;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import uk.co.caprica.vlcj.player.base.AudioChannel;
import uk.co.caprica.vlcj.player.base.AudioDevice;

import java.util.Arrays;
import java.util.List;

/**
 * 音频设置弹窗的 ViewModel，负责把下拉框索引翻译成 VLC 参数并下发。
 * <p>
 * 构造时从 {@link VlcPlayer} 单例枚举一次输出设备（此后不再刷新），
 * 同时把 {@code Config.setting} 中已保存的声道/设备索引回填到
 * {@code channelIndex}/{@code deviceIndex}；索引为负（未保存过）时保留默认 0。
 * 索引只用于 UI 回显，真正生效依赖 {@link #setChannel(int)}、
 * {@link #setAudioDevice(int)}、{@link #setAudioEffect(int)} 向播放器下发命令。
 *
 * @author Leck
 */
public class AudioChannelViewModel implements ViewModel {
    public static final String UPDATE_AUDIO_CHANNEL_EVENT = "UPDATE_AUDIO_CHANNEL_EVENT";

    private SimpleIntegerProperty channelIndex = new SimpleIntegerProperty(0);
    private SimpleIntegerProperty deviceIndex = new SimpleIntegerProperty(0);

    private final ObservableList<AudioDevice> audioDevices = FXCollections.observableArrayList();
    private final ObservableList<String> audioDeviceNames = FXCollections.observableArrayList();
    private final ObservableList<AudioEffect> audioEffects = FXCollections.observableArrayList();
    public AudioChannelViewModel() {
        List<AudioDevice> devices = VlcPlayer.getInstance().getAllAudioDevices();
        audioDevices.setAll(devices);

        List<String> list = devices.stream().map(AudioDevice::getLongName).toList();
        audioDeviceNames.setAll(list);
        audioEffects.setAll(Arrays.stream(AudioEffect.values()).toList());


        int channelIndex = Config.setting.getAudioChannel();
        if (channelIndex >= 0){
            this.channelIndex.set(channelIndex);
        }

        int deviceIndex = Config.setting.getAudioOutputDevice();
        if (deviceIndex >= 0){
            this.deviceIndex.set(deviceIndex);
        }


    }

    /**
     * 按下拉框选中序号切换 VLC 声道。
     * <p>
     * 只下发播放命令，不回写 {@code channelIndex}（该属性仅在构造时从配置初始化）。
     * 序号 1~7 依次对应 STEREO/RSTEREO/LEFT/RIGHT/DOLBYS/HEADPHONES/MONO，
     * 其余序号（含 0 与越界值）统一落到 {@code AudioChannel.UNSET}，交回 VLC 自行决定。
     *
     * @param index 声道下拉框选中序号，与 {@code Config.setting.audioChannel} 同一套取值
     */
    public void setChannel(int index){
        AudioChannel channel;
        switch (index){
            case 1 -> channel = AudioChannel.STEREO;
            case 2 -> channel = AudioChannel.RSTEREO;
            case 3 -> channel = AudioChannel.LEFT;
            case 4 -> channel = AudioChannel.RIGHT;
            case 5 -> channel = AudioChannel.DOLBYS;
            case 6 -> channel = AudioChannel.HEADPHONES;
            case 7 -> channel = AudioChannel.MONO;
            default -> channel = AudioChannel.UNSET;
        }
        VlcPlayer.getInstance().setAudioChannel(channel);
    }


    /**
     * 切换音频输出设备。
     * <p>
     * 设备在构造时一次性枚举，因此 index 越界会抛
     * {@code IndexOutOfBoundsException}；调用方应使用
     * {@link #getAudioDeviceNames()} 的选中下标（-1 表示未选中，不可传入）。
     *
     * @param index 输出设备在 {@code audioDeviceNames} 中的下标
     */
    public void setAudioDevice(int index){
        VlcPlayer.getInstance().setAudioDevice(audioDevices.get(index));
    }


    /**
     * 应用均衡器预设。
     *
     * @param index 预设在下拉框中的下标，对应 {@link AudioEffect} 枚举顺序
     */
    public void setAudioEffect(int index){
        AudioEffect audioEffect = audioEffects.get(index);
        VlcPlayer.getInstance().setAudioEffect(audioEffect);
    }

    public int getChannelIndex() {
        return channelIndex.get();
    }

    public SimpleIntegerProperty channelIndexProperty() {
        return channelIndex;
    }

    public ObservableList<AudioDevice> getAudioDevices() {
        return audioDevices;
    }

    public ObservableList<String> getAudioDeviceNames() {
        return audioDeviceNames;
    }

    public int getDeviceIndex() {
        return deviceIndex.get();
    }

    public SimpleIntegerProperty deviceIndexProperty() {
        return deviceIndex;
    }

    public ObservableList<AudioEffect> getAudioEffects() {
        return audioEffects;
    }
}