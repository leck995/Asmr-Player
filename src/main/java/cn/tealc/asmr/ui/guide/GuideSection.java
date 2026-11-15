package cn.tealc.asmr.ui.guide;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 教程分组，如“开始使用”“媒体库”。
 *
 * @author Leck
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GuideSection(String title, List<GuideItem> items) {

    public GuideSection {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
