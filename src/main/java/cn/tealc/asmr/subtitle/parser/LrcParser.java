package cn.tealc.asmr.subtitle.parser;

import cn.tealc.asmr.model.SubtitleCue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LRC 格式字幕解析器。
 * <p>
 * 支持标准 LRC 时间标记 {@code [mm:ss.xx]} 或 {@code [mm:ss.xxx]}，
 * 以及同一行多时间标记（共享歌词文本）的写法。
 * </p>
 *
 * @author Leck
 */
public class LrcParser implements SubtitleParser {

    private static final Logger logger = LoggerFactory.getLogger(LrcParser.class);

    /** 匹配整行 LRC，如 {@code [00:01.23][00:05.67]歌词文本} */
    private static final Pattern LINE_PATTERN =
            Pattern.compile("((\\[\\d{2,3}:\\d{2}\\.\\d{2,3}\\])+)(.+)");

    /** 从时间标记中提取分、秒、毫秒，如 {@code [00:01.23]} → 分=00, 秒=01, 毫秒=23 */
    private static final Pattern TIME_PATTERN =
            Pattern.compile("\\[(\\d{2,3}):(\\d{2})\\.(\\d{2,3})\\]");

    @Override
    public List<SubtitleCue> parse(String content) {
        if (content == null) {
            logger.warn("parse: content 为 null");
            return null;
        }
        logger.info("parse: content 长度={}, 前80字符=[{}]", content.length(), content.substring(0, Math.min(80, content.length())));
        // 统一换行：去除 \r\n 中的 \r，兼容 Windows/Unix 格式
        content = content.replace("\r\n", "\n").replace("\r", "\n");
        List<SubtitleCue> cues = new ArrayList<>();
        String[] lines = content.split("\n");
        logger.info("parse: 分割为 {} 行", lines.length);
        for (String line : lines) {
            List<SubtitleCue> entryList = parseLine(line);
            if (entryList != null && !entryList.isEmpty()) {
                cues.addAll(entryList);
            }
        }
        logger.info("parse: 解析到 {} 条字幕", cues.size());
        Collections.sort(cues, (a, b) -> Long.compare(a.getStartTime(), b.getStartTime()));
        return cues;
    }

    /**
     * 解析单行 LRC，可能产出多个条目（当一行有多个时间标记时）。
     *
     * @param s 单行文本
     * @return 解析出的条目列表，无时间标记返回 null
     */
    private List<SubtitleCue> parseLine(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        s = s.trim();
        if (s.isEmpty()) {
            return null;
        }
        Matcher lineMatcher = LINE_PATTERN.matcher(s);
        if (!lineMatcher.find()) {
            return null;
        }
        String timeTags = lineMatcher.group(1);
        String text = lineMatcher.group(3).trim();
        if (text.isEmpty()) {
            return null;
        }
        List<SubtitleCue> entryList = new ArrayList<>();
        Matcher timeMatcher = TIME_PATTERN.matcher(timeTags);
        while (timeMatcher.find()) {
            long min = Long.parseLong(timeMatcher.group(1));
            long sec = Long.parseLong(timeMatcher.group(2));
            long mil = Long.parseLong(timeMatcher.group(3));
            long time = min * 60000 + sec * 1000 + scaleMillis(mil);
            entryList.add(new SubtitleCue(time, timeTags, text));
        }
        return entryList;
    }

    /**
     * 将毫秒部分归一化为实际毫秒值。
     * <p>
     * 2 位数字（如 {@code 23}）表示百分之一秒，需 ×10 → 230ms；
     * 3 位数字（如 {@code 230}）已是毫秒，×1 即可。
     * 边界值 {@code 100} 按 3 位处理（×1）。
     * </p>
     *
     * @param mil 原始毫秒字段值
     * @return 实际毫秒值
     */
    private long scaleMillis(long mil) {
        return mil >= 100 ? mil : mil * 10;
    }
}
