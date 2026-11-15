package cn.tealc.asmr.subtitle.parser;

import cn.tealc.asmr.model.SubtitleCue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SRT 格式字幕解析器。
 * <p>
 * 支持标准 SubRip SRT 格式，包括：
 * <ul>
 *   <li>cue 序号行（自动跳过）</li>
 *   <li>时间行 {@code HH:MM:SS,mmm --> HH:MM:SS,mmm}（逗号分隔毫秒）</li>
 *   <li>多行 cue 文本（用 \n 拼接）</li>
 *   <li>内联标签（如 {@code <i>}, {@code <b>}，自动剥离）</li>
 * </ul>
 * 兼容 Windows（CRLF）和 Unix（LF）换行。
 * </p>
 *
 * @author Leck
 */
public class SrtParser implements SubtitleParser {

    /** 匹配 SRT 时间行，如 {@code 00:00:01,234 --> 00:00:05,678} */
    private static final Pattern TIME_LINE_PATTERN =
            Pattern.compile("(\\d{1,2}:\\d{2}:\\d{2},\\d{3})\\s*-->\\s*");

    @Override
    public List<SubtitleCue> parse(String content) {
        if (content == null) {
            return null;
        }
        // 去 BOM + 统一换行
        content = SubtitleTextCleaner.stripBOM(content);
        content = content.replace("\r\n", "\n").replace("\r", "\n");

        List<SubtitleCue> cues = new ArrayList<>();
        // 按 cue 块分割（空行分隔）
        String[] blocks = content.split("\n\n");
        for (String block : blocks) {
            parseBlock(block, cues);
        }
        Collections.sort(cues, (a, b) -> Long.compare(a.getStartTime(), b.getStartTime()));
        return cues;
    }

    /**
     * 解析单个 SRT cue 块。
     * <p>
     * 块结构：
     * <pre>
     * 1                     ← 序号行，跳过
     * 00:00:01,234 --> 00:00:05,678
     * 第一行文本
     * 第二行文本
     * </pre>
     * </p>
     *
     * @param block 单个 cue 块文本
     * @param cues  解析结果收集列表
     */
    private void parseBlock(String block, List<SubtitleCue> cues) {
        if (block == null || block.isBlank()) {
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
            return;
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
     * 将 SRT 时间字符串（{@code HH:MM:SS,mmm}）解析为毫秒值。
     * <p>
     * SRT 的毫秒分隔符是逗号 {@code ,} 而非点 {@code .}。
     * </p>
     *
     * @param time SRT 时间字符串
     * @return 毫秒值
     */
    private long parseTime(String time) {
        String[] parts = time.split(":");
        if (parts.length != 3) {
            return 0;
        }
        long hours = Long.parseLong(parts[0]);
        long minutes = Long.parseLong(parts[1]);
        String[] secParts = parts[2].split(",");
        long seconds = Long.parseLong(secParts[0]);
        long millis = Long.parseLong(secParts[1]);
        return hours * 3600000 + minutes * 60000 + seconds * 1000 + millis;
    }
}
