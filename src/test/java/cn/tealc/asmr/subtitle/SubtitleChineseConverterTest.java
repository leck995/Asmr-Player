package cn.tealc.asmr.subtitle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 字幕简繁转换器测试。
 */
class SubtitleChineseConverterTest {

    private final SubtitleChineseConverter converter = new SubtitleChineseConverter();

    @Test
    void 保持原文与空值不变() {
        assertEquals("头发与后台", converter.convert("头发与后台", SubtitleScriptMode.ORIGINAL));
        assertNull(converter.convert(null, SubtitleScriptMode.TRADITIONAL));
        assertEquals("", converter.convert("", SubtitleScriptMode.SIMPLIFIED));
    }

    @Test
    void 繁体转换优先匹配词组() {
        assertEquals("頭髮", converter.convert("头发", SubtitleScriptMode.TRADITIONAL));
        assertEquals("後臺", converter.convert("后台", SubtitleScriptMode.TRADITIONAL));
    }

    @Test
    void 简体转换使用单字兜底并保留非中文内容() {
        assertEquals("头发 A-01 🎧", converter.convert("頭髮 A-01 🎧", SubtitleScriptMode.SIMPLIFIED));
    }
}
