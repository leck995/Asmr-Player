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
 * WebVTT 格式字幕解析器。
 * <p>
 * 支持标准 WebVTT 格式，包括：
 * <ul>
 *   <li>{@code WEBVTT} 头行（自动跳过）</li>
 *   <li>cue 标识符行（如纯数字或 {@code cue-1}，自动跳过）</li>
 *   <li>{@code NOTE} 块（自动跳过）</li>
 *   <li>多行 cue 文本（用 \n 拼接）</li>
 *   <li>内联标签（如 {@code <c.yellow>}, {@code <i>}, {@code <v Speaker>}，自动剥离）</li>
 *   <li>时间格式 {@code HH:MM:SS.mmm} 和 {@code MM:SS.mmm}</li>
 * </ul>
 * 兼容 Windows（CRLF）和 Unix（LF）换行。
 * </p>
 *
 * @author Leck
 */
public class VttParser implements SubtitleParser {

    private static final Logger logger = LoggerFactory.getLogger(VttParser.class);

    /** 匹配 cue 时间行，如 {@code 00:00:01.234 --> 00:00:05.678} 或 {@code 00:01.234 --> 00:05.678} */
    private static final Pattern TIME_LINE_PATTERN =
            Pattern.compile("(\\d{1,2}:\\d{2}:\\d{2}\\.\\d{3}|\\d{1,2}:\\d{2}\\.\\d{3})\\s*-->\\s*");

    @Override
    public List<SubtitleCue> parse(String content) {
        if (content == null) {
            logger.warn("parse: content 为 null");
            return null;
        }
        logger.info("parse: content 长度={}, 前80字符=[{}]", content.length(), content.substring(0, Math.min(80, content.length())));
        // 去 BOM + 统一换行
        content = SubtitleTextCleaner.stripBOM(content);
        content = content.replace("\r\n", "\n").replace("\r", "\n");

        List<SubtitleCue> cues = new ArrayList<>();
        // 按 cue 块分割（空行分隔）
        String[] blocks = content.split("\n\n");
        logger.info("parse: 分割为 {} 个块", blocks.length);
        for (String block : blocks) {
            parseBlock(block, cues);
        }
        logger.info("parse: 解析到 {} 条字幕", cues.size());
        Collections.sort(cues, (a, b) -> Long.compare(a.getStartTime(), b.getStartTime()));
        return cues;
    }

    /**
     * 解析单个 cue 块。
     * <p>
     * 块结构可能是：
     * <pre>
     * cue-identifier       ← 可选，跳过
     * 00:00:01.234 --> 00:00:05.678
     * 第一行文本
     * 第二行文本
     * </pre>
     * 或 NOTE 块（整块跳过）。
     * </p>
     *
     * @param block 单个 cue 块文本
     * @param cues  解析结果收集列表
     */
    private void parseBlock(String block, List<SubtitleCue> cues) {
        if (block == null || block.isBlank()) {
            return;
        }
        // 跳过 WEBVTT 头行和 NOTE 块
        String trimmed = block.trim();
        if (trimmed.startsWith("WEBVTT") || trimmed.startsWith("NOTE")) {
            return;
        }

        String[] lines = block.split("\n");
        int timeLineIndex = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("-->")) {
                timeLineIndex = i;
                break;
            }
        }
        if (timeLineIndex == -1) {
            return; // 没有时间行，不是有效 cue
        }

        String timeLine = lines[timeLineIndex];
        Matcher timeMatcher = TIME_LINE_PATTERN.matcher(timeLine);
        if (!timeMatcher.find()) {
            return;
        }
        String startTimeStr = timeMatcher.group(1);
        long startTime = parseTime(startTimeStr);

        // cue 文本：时间行之后的所有行
        StringBuilder textBuilder = new StringBuilder();
        for (int i = timeLineIndex + 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            if (textBuilder.length() > 0) {
                textBuilder.append("\n");
            }
            textBuilder.append(SubtitleTextCleaner.cleanInlineTags(line));
        }
        String text = textBuilder.toString().trim();
        if (text.isEmpty()) {
            return;
        }
        cues.add(new SubtitleCue(startTime, startTimeStr, text));
    }

    /**
     * 将 VTT 时间字符串解析为毫秒值。
     * <p>
     * 支持两种格式：
     * <ul>
     *   <li>{@code HH:MM:SS.mmm}（如 {@code 01:00:23.456}）</li>
     *   <li>{@code MM:SS.mmm}（如 {@code 00:23.456}）</li>
     * </ul>
     * </p>
     *
     * @param time VTT 时间字符串
     * @return 毫秒值
     */
    private long parseTime(String time) {
        String[] parts = time.split(":");
        long hours = 0, minutes = 0, seconds = 0, millis = 0;
        if (parts.length == 3) {
            hours = Long.parseLong(parts[0]);
            minutes = Long.parseLong(parts[1]);
            String[] secParts = parts[2].split("\\.");
            seconds = Long.parseLong(secParts[0]);
            millis = Long.parseLong(secParts[1]);
        } else if (parts.length == 2) {
            minutes = Long.parseLong(parts[0]);
            String[] secParts = parts[1].split("\\.");
            seconds = Long.parseLong(secParts[0]);
            millis = Long.parseLong(secParts[1]);
        }
        return hours * 3600000 + minutes * 60000 + seconds * 1000 + millis;
    }
}
