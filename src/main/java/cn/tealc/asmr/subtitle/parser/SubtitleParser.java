package cn.tealc.asmr.subtitle.parser;

import cn.tealc.asmr.model.SubtitleCue;

import java.util.List;

/**
 * 字幕解析器接口，将字幕文本内容解析为 {@link SubtitleCue} 列表。
 * <p>
 * 每种字幕格式（LRC、VTT、SRT）各有一个实现类。
 * </p>
 *
 * @author Leck
 */
public interface SubtitleParser {

    /**
     * 解析字幕文本内容。
     *
     * @param content 字幕文件的完整文本内容
     * @return 解析后的字幕条目列表，按时间升序排列；输入为 null 返回 null
     */
    List<SubtitleCue> parse(String content);
}
