package cn.tealc.asmr.player;

import javafx.util.Duration;

/**
 * 切歌加载参数值对象。
 * <p>
 * 封装媒体加载时的选项（是否自动播放、起始时间），替代 VlcEngine 中散落的
 * {@code requirePauseOnReady} / {@code startTime} 两个可变实例字段，
 * 消除 mutable state 竞态风险。
 * </p>
 *
 * @author Leck
 */
record LoadOptions(boolean autoPlay, double startTimeSeconds) {

    /**
     * 从 autoPlay 和 Duration 构建加载参数。
     *
     * @param autoPlay true 自动播放，false 加载后暂停
     * @param time     恢复播放的起始时间，null 或非正数表示从头播放
     */
    static LoadOptions of(boolean autoPlay, Duration time) {
        double seconds = (time == null) ? 0 : time.toSeconds();
        return new LoadOptions(autoPlay, seconds);
    }

    /**
     * 是否需要恢复播放位置。
     *
     * @return true 当 startTimeSeconds &gt; 0
     */
    boolean hasStartTime() {
        return startTimeSeconds > 0;
    }
}
