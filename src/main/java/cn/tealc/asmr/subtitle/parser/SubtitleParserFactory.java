package cn.tealc.asmr.subtitle.parser;

/**
 * 字幕解析器工厂，按文件扩展名分发到对应格式的解析器。
 * <p>
 * 支持的格式：
 * <ul>
 *   <li>{@code .lrc} → {@link LrcParser}</li>
 *   <li>{@code .vtt} → {@link VttParser}</li>
 *   <li>{@code .srt} → {@link SrtParser}</li>
 * </ul>
 * 扩展名匹配大小写不敏感。
 * </p>
 *
 * @author Leck
 */
public final class SubtitleParserFactory {

    private SubtitleParserFactory() {}

    /**
     * 根据文件扩展名获取对应的字幕解析器。
     *
     * @param extension 文件扩展名（不含点，如 {@code "lrc"}、{@code "VTT"}）
     * @return 对应的解析器实例，不支持的格式返回 null
     */
    public static SubtitleParser forExtension(String extension) {
        if (extension == null) {
            return null;
        }
        return switch (extension.toLowerCase()) {
            case "lrc" -> new LrcParser();
            case "vtt" -> new VttParser();
            case "srt" -> new SrtParser();
            default -> null;
        };
    }
}
