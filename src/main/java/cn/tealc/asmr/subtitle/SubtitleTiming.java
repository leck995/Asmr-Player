package cn.tealc.asmr.subtitle;

/**
 * 字幕同步时间计算。
 * <p>
 * 内置提前量让字幕比音频时间戳稍早出现；用户延迟在此基础上叠加，
 * 正数表示延后、负数表示提前。播放同步与点击字幕跳转必须共用这里的公式，
 * 否则调整延迟后点击某句会落到相邻句。
 * </p>
 */
public final class SubtitleTiming {

    /** 内置提前量（毫秒）。 */
    public static final long BASE_LEAD_MS = 500;

    public static final int MIN_DELAY_MS = -10_000;
    public static final int MAX_DELAY_MS = 10_000;

    private SubtitleTiming() {
    }

    /**
     * 把用户偏移限制在滑杆允许的范围内。
     */
    public static int clampDelay(int delayMs) {
        return Math.max(MIN_DELAY_MS, Math.min(MAX_DELAY_MS, delayMs));
    }

    /**
     * 计算用于匹配字幕的播放时间。
     *
     * @param currentSeconds 当前播放时间（秒）
     * @param delayMs        用户同步偏移（毫秒）
     * @return 叠加内置提前量与用户偏移后的查找时间（毫秒）
     */
    public static long lookupMillis(double currentSeconds, int delayMs) {
        return Math.round(currentSeconds * 1000) + BASE_LEAD_MS - clampDelay(delayMs);
    }

    /**
     * 计算点击指定字幕行后应跳转到的播放时间。
     *
     * @param cueStartMillis 字幕行时间戳（毫秒）
     * @param delayMs        用户同步偏移（毫秒）
     * @return 补偿用户偏移后的跳转时间（毫秒），不小于 0
     */
    public static long seekTargetMillis(long cueStartMillis, int delayMs) {
        return Math.max(0, cueStartMillis + clampDelay(delayMs));
    }
}
