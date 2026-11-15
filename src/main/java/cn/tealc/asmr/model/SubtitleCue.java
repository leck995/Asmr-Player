package cn.tealc.asmr.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 字幕条目，统一表示 LRC/VTT/SRT 解析后的单条字幕。
 * <p>
 * 一条字幕包含开始时间（毫秒）、原始时间标记、主文本，
 * 以及可选的翻译文本。翻译文本仅在某些 LRC 文件中通过
 * 「」【】（）等分隔符内联标注时才会被提取。
 * </p>
 *
 * @author Leck
 */
public class SubtitleCue {
    /** 翻译分隔符模式，支持 「」、【】、（） 等多种格式 */
    private static final Pattern TRANSLATION_PATTERN =
            Pattern.compile("(.+?)[「【（(](.+)[」】）)]$");

    /** 开始时间（毫秒），用于与播放时间同步匹配 */
    private long startTime;
    /** 原始时间标记字符串（如 [00:01.23] 或 00:01:23.456），保留用于调试 */
    private String timeTag;
    /** 主文本（已剥离内联标签） */
    private String text;
    /** 翻译文本，可能为 null */
    private String translation;

    /**
     * 构造字幕条目，自动从文本中提取内联翻译。
     *
     * @param startTime 开始时间（毫秒）
     * @param timeTag    原始时间标记字符串
     * @param text       主文本，可能含翻译分隔符
     */
    public SubtitleCue(long startTime, String timeTag, String text) {
        this.startTime = startTime;
        this.timeTag = timeTag;
        Matcher matcher = TRANSLATION_PATTERN.matcher(text);
        if (matcher.find()) {
            this.text = matcher.group(1);
            this.translation = matcher.group(2);
        } else {
            this.text = text;
        }
    }

    /**
     * 判断是否包含翻译文本。
     *
     * @return 有翻译返回 true
     */
    public boolean hasTranslation() {
        return translation != null;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public String getTimeTag() {
        return timeTag;
    }

    public void setTimeTag(String timeTag) {
        this.timeTag = timeTag;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getTranslation() {
        return translation;
    }

    public void setTranslation(String translation) {
        this.translation = translation;
    }

    @Override
    public String toString() {
        return "SubtitleCue{" +
                "startTime=" + startTime +
                ", timeTag='" + timeTag + '\'' +
                ", text='" + text + '\'' +
                ", translation='" + translation + '\'' +
                '}';
    }
}
