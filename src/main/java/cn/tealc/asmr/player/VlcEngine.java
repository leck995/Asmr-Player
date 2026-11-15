package cn.tealc.asmr.player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.log.LogLevel;
import uk.co.caprica.vlcj.log.NativeLog;
import uk.co.caprica.vlcj.media.MediaRef;
import uk.co.caprica.vlcj.player.base.*;
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent;

import java.util.List;

/**
 * VLC native 引擎，封装 vlcj 底层操作与事件监听。
 * <p>
 * 从 {@link VlcPlayer} 中抽取的 native 层职责。持有 vlcj 组件
 * （{@link MediaPlayerFactory} / {@link AudioPlayerComponent} / {@link MediaPlayer}），
 * 封装所有 native 操作与 VLC 事件监听。
 * </p>
 * <p>
 * 不直接持有 JavaFX 状态属性，通过 {@link VlcEngineCallback} 回调接口将
 * native 事件通知上层（VlcPlayer），由 VlcPlayer 负责线程切换与状态更新。
 * 所有回调方法在 VLC native 线程被调用，且携带 {@code generation} 参数
 * 供上层做 stale 守卫。
 * </p>
 *
 * @author Leck
 */
class VlcEngine {

    private static final Logger logger = LoggerFactory.getLogger(VlcEngine.class);

    /**
     * native 事件回调接口，由 {@link VlcPlayer} 实现。
     * <p>
     * 所有方法在 VLC native 线程被调用，实现方需自行做
     * {@code Platform.runLater} 线程切换。每个回调携带 {@code generation}
     * 参数，标识事件所属的媒体加载世代，上层可据此丢弃 stale 事件。
     * </p>
     */
    interface VlcEngineCallback {
        /** 时间更新（已节流至约 4 次/秒），VlcPlayer 负责 Platform.runLater + setCurrentTime */
        void onTimeChanged(double currentTimeSeconds, int generation);

        /** 播放完成，VlcPlayer 决定循环/下一曲/停止，并重置声道 */
        void onFinished(int generation);

        /** VLC 进入播放状态，VlcPlayer 设置 playing 属性，并读取 meta/封面/时长 */
        void onPlaying(MediaPlayer mp, int generation);

        /** 媒体打开中，VlcPlayer 更新 UI 提示（"加载中"/"请稍后"） */
        void onOpening(int generation);

        /** 媒体就绪，VlcPlayer 恢复音频设备 */
        void onMediaPlayerReady(MediaPlayer mp, int generation);

        /** 播放错误，VlcPlayer 设置 ready=false */
        void onError(int generation);

        /** 缓冲进度（已节流），VlcPlayer 更新 bufferedTime */
        void onBuffering(float cache, int generation);
    }

    // ==================== native 组件 ====================

    private final MediaPlayer mediaPlayer;
    private final MediaPlayerFactory mediaPlayerFactory;
    private final AudioPlayerComponent playerComponent;

    /** 回调 */
    private final VlcEngineCallback callback;

    /** VLC 原生日志，持有引用防止 GC 回收回调 */
    private final NativeLog nativeLog;

    // ==================== 内部状态 ====================

    /** 当前音量（0-1），用于 mediaPlayerReady 时恢复 */
    private double currentVolume = 1.0;

    /**
     * 媒体世代计数器：每次 load 自增，事件回调时比对以丢弃旧媒体的 stale 事件。
     * <p>
     * 快速切歌 A→B→C 时，A 的 mediaPlayerReady/finished 等事件可能延迟到达，
     * 此时 loadGeneration 已经是 C 的值，旧事件被直接忽略。
     * 所有事件回调入口统一做世代守卫，不再依赖上层维护 loadingMusic 引用。
     * </p>
     */
    private volatile int loadGeneration = 0;

    /** 当前加载参数（替代原 requirePauseOnReady + startTime 两个可变字段） */
    private volatile LoadOptions loadOptions = new LoadOptions(true, 0);

    /** buffering 节流：上次上报的缓冲值，避免高频回调卡 UI */
    private volatile float lastBufferCache = -1f;

    /** timeChanged 节流：上次上报的时间戳（毫秒），限制回调频率到约 4 次/秒 */
    private volatile long lastTimeUpdateMs = 0L;

    // ==================== 构造函数 ====================

    /**
     * 初始化 VLC native 引擎。
     *
     * @param callback 事件回调（通常由 VlcPlayer 实现）
     */
    VlcEngine(VlcEngineCallback callback) {
        this.callback = callback;
        logger.info("初始化 VlcEngine");

        // libvlc 返回的字符串（元数据、MRL 等）均为 UTF-8，而 JNA 默认按平台编码
        // （中文 Windows 为 GBK）解码，导致非 ASCII 标题/艺术家显示为乱码。
        // 必须在 JNA 首次加载前设置。
        System.setProperty("jna.encoding", "UTF-8");
        VlcNativeLoader.setup();
        mediaPlayerFactory = new MediaPlayerFactory(
                "--clock-synchro=0",
                "--no-metadata-network-access", // 禁用 VLC 联网拉取元数据/封面，避免本地文件解析时发起 DNS/HTTP 请求拖慢 mediaPlayerReady
                "--no-sub-autodetect-file",      // 禁用字幕自动检测：本程序为纯音频播放器（无 vout），VLC 加载 .vtt 字幕后 webvtt decoder 停止时会因找不到视频输出卡住约 4 秒，导致切歌时 media().play() 被阻塞。歌词由 LrcSynchronizer 独立处理，不依赖 VLC 字幕
                "--no-video-title-show"          // 禁用视频标题显示
        );
        playerComponent = new AudioPlayerComponent(mediaPlayerFactory);
        mediaPlayer = playerComponent.mediaPlayer();

        // 启用 VLC native 日志输出（仅 WARNING 级别，避免生产环境日志风暴）
        nativeLog = mediaPlayerFactory.application().newLog();
        nativeLog.setLevel(LogLevel.WARNING);
        nativeLog.addLogListener((level, module, file, line, name, header, id, message) -> {
            if (level.intValue() >= LogLevel.WARNING.intValue()) {
                logger.warn("[VLC原生] [{}] {}: {}", module, name, message);
            }
        });

        registerVlcEventListeners();
    }

    // ==================== VLC 事件监听 ====================

    private void registerVlcEventListeners() {
        mediaPlayer.events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {

            @Override
            public void timeChanged(MediaPlayer mediaPlayer, long newTime) {
                super.timeChanged(mediaPlayer, newTime);
                long now = System.currentTimeMillis();
                if (now - lastTimeUpdateMs < 250) return;
                lastTimeUpdateMs = now;
                callback.onTimeChanged((double) newTime / 1000, loadGeneration);
            }

            @Override
            public void finished(MediaPlayer mediaPlayer) {
                super.finished(mediaPlayer);
                callback.onFinished(loadGeneration);
            }

            @Override
            public void playing(MediaPlayer mediaPlayer) {
                super.playing(mediaPlayer);
                callback.onPlaying(mediaPlayer, loadGeneration);
            }

            @Override
            public void opening(MediaPlayer mediaPlayer) {
                super.opening(mediaPlayer);
                callback.onOpening(loadGeneration);
            }

            @Override
            public void mediaPlayerReady(MediaPlayer mediaPlayer) {
                super.mediaPlayerReady(mediaPlayer);
                final int gen = loadGeneration;

                // 应用音量（native 线程直接调用）
                mediaPlayer.audio().setVolume((int) (currentVolume * 100));

                // loadOptions 处理（在 native 线程异步执行，带世代守卫）
                mediaPlayer.submit(() -> {
                    if (loadGeneration != gen) return;
                    LoadOptions opts = loadOptions;
                    if (opts.hasStartTime()) {
                        long millis = (long) (opts.startTimeSeconds() * 1000);
                        mediaPlayer.controls().setTime(millis);
                        callback.onTimeChanged(opts.startTimeSeconds(), gen);
                    }
                    if (!opts.autoPlay()) {
                        mediaPlayer.controls().setPause(true);
                    }
                });

                callback.onMediaPlayerReady(mediaPlayer, gen);
            }

            @Override
            public void error(MediaPlayer mediaPlayer) {
                super.error(mediaPlayer);
                logger.error("无法播放歌曲:{}", mediaPlayer.media().info().mrl());
                callback.onError(loadGeneration);
            }

            @Override
            public void buffering(MediaPlayer mediaPlayer, float newCache) {
                super.buffering(mediaPlayer, newCache);
                if (Math.abs(newCache - lastBufferCache) < 0.05f) return;
                lastBufferCache = newCache;
                callback.onBuffering(newCache, loadGeneration);
            }

            @Override
            public void mediaChanged(MediaPlayer mediaPlayer, MediaRef media) {
                super.mediaChanged(mediaPlayer, media);
            }
        });
    }

    // ==================== native 操作 ====================

    /**
     * 加载媒体。
     * <p>
     * {@code media().play(url)} 内部自动停止旧媒体，无需显式调用 stop()。
     * 同时自增 {@link #loadGeneration}，使旧媒体的 stale 事件被丢弃。
     * </p>
     *
     * @param url      媒体 URL
     * @param options  加载参数（自动播放 / 起始时间）
     */
    void load(String url, LoadOptions options) {
        this.loadOptions = options;
        loadGeneration++;
        final int gen = loadGeneration;
        mediaPlayer.submit(() -> {
            if (options.autoPlay()) {
                mediaPlayer.media().play(url);
            } else {
                mediaPlayer.media().prepare(url);
            }
        });
    }

    /** native 播放 */
    void play() {
        mediaPlayer.submit(() -> mediaPlayer.controls().play());
    }

    /** native 暂停 */
    void pause() {
        mediaPlayer.submit(() -> mediaPlayer.controls().pause());
    }

    /** 停止后重新播放（用于循环播放） */
    void stopAndPlay() {
        mediaPlayer.submit(() -> {
            mediaPlayer.controls().stop();
            mediaPlayer.controls().play();
        });
    }

    /** 停止播放 */
    void stop() {
        mediaPlayer.submit(() -> mediaPlayer.controls().stop());
    }

    /** 跳转到指定时间（毫秒） */
    void seek(long millis) {
        mediaPlayer.submit(() -> mediaPlayer.controls().setTime(millis));
    }

    /** 相对跳转（毫秒，正数快进/负数后退） */
    void skipTime(long millis) {
        mediaPlayer.submit(() -> mediaPlayer.controls().skipTime(millis));
    }

    /** 设置音量（0-1 范围） */
    void applyVolume(double volume01) {
        this.currentVolume = volume01;
        mediaPlayer.submit(() -> mediaPlayer.audio().setVolume((int) (volume01 * 100)));
    }

    /** 设置静音 */
    void applyMute(boolean mute) {
        mediaPlayer.submit(() -> mediaPlayer.audio().setMute(mute));
    }

    /** 设置声道 */
    void setAudioChannel(AudioChannel channel) {
        mediaPlayer.submit(() -> mediaPlayer.audio().setChannel(channel));
    }

    /** 设置音频输出设备 */
    void setAudioDevice(AudioDevice device) {
        mediaPlayer.submit(() -> mediaPlayer.audio().setOutputDevice(null, device.getDeviceId()));
    }

    /** 获取所有可用音频输出设备 */
    List<AudioDevice> getAllAudioDevices() {
        return mediaPlayer.audio().outputDevices();
    }

    /** 设置均衡器预设 */
    void setAudioEffect(AudioEffect audioEffect) {
        mediaPlayer.submit(() -> applyEqualizer(audioEffect));
    }

    /**
     * 恢复音频输出设备与均衡器（在 native 线程内直接调用，不额外提交任务）。
     *
     * @param device 音频输出设备
     * @param effect 均衡器预设
     */
    void restoreAudioOutput(AudioDevice device, AudioEffect effect) {
        mediaPlayer.audio().setOutputDevice(null, device.getDeviceId());
        applyEqualizer(effect);
    }

    private void applyEqualizer(AudioEffect effect) {
        Equalizer equalizer = mediaPlayerFactory.equalizer().newEqualizer(effect.getPresetName());
        mediaPlayer.audio().setEqualizer(equalizer);
    }

    /** 在 native 线程提交任务 */
    void submit(Runnable task) {
        mediaPlayer.submit(task);
    }

    /** 释放 native 资源 */
    void release() {
        if (nativeLog != null) {
            nativeLog.release();
        }
        if (playerComponent != null) {
            playerComponent.release();
        }
        if (mediaPlayerFactory != null) {
            mediaPlayerFactory.release();
        }
    }
}
