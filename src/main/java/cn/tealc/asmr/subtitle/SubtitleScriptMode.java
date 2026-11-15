package cn.tealc.asmr.subtitle;

/**
 * 字幕文本的显示文字体系。
 */
public enum SubtitleScriptMode {
    ORIGINAL("原文"),
    SIMPLIFIED("自动转简体"),
    TRADITIONAL("自动转繁体");

    private final String displayName;

    SubtitleScriptMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 将配置文本解析为显示模式，非法值回退到原文。
     *
     * @param value 配置文本
     * @return 对应的显示模式
     */
    public static SubtitleScriptMode fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return ORIGINAL;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            return ORIGINAL;
        }
    }

    @Override
    public String toString() {
        return displayName;
    }
}
