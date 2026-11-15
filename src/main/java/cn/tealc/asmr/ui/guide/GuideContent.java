package cn.tealc.asmr.ui.guide;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 教程内容：页头信息与分组问答。
 *
 * @author Leck
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GuideContent(String title, String subtitle, List<GuideSection> sections) {

    public GuideContent {
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
