package cn.tealc.asmr.subtitle.parser;

/**
 * 字幕文本清理工具，用于剥离 VTT/SRT 内联标签和去除 BOM。
 *
 * @author Leck
 */
public final class SubtitleTextCleaner {

    private SubtitleTextCleaner() {}

    /** 匹配所有 WebVTT/SRT 内联标签，如 {@code <c.yellow>}, {@code </i>}, {@code <v Speaker>} 等 */
    private static final java.util.regex.Pattern INLINE_TAG_PATTERN =
            java.util.regex.Pattern.compile("<[^>]+>");

    /** UTF-8 BOM 前缀 */
    private static final String UTF8_BOM = "\uFEFF";

    /**
     * 剥离文本中的所有内联标签（{@code <...>} 格式）。
     *
     * @param text 可能包含内联标签的文本
     * @return 清理后的纯文本
     */
    public static String cleanInlineTags(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return INLINE_TAG_PATTERN.matcher(text).replaceAll("");
    }

    /**
     * 去除文本开头的 UTF-8 BOM 前缀（如果存在）。
     *
     * @param content 原始文本
     * @return 去除 BOM 后的文本
     */
    public static String stripBOM(String content) {
        if (content != null && content.startsWith(UTF8_BOM)) {
            return content.substring(UTF8_BOM.length());
        }
        return content;
    }
}
