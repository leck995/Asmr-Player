package cn.tealc.asmr.ui.guide;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 单条问答。{@code steps} 与 {@code image}/{@code caption} 可省略。
 * <p>
 * {@code image} 为相对 {@code /cn/tealc/asmr/guide/} 的路径，缺少图片时只跳过图片本身。
 *
 * @author Leck
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GuideItem(String question, List<String> answer, List<String> steps,
                        String image, String caption) {

    public GuideItem {
        answer = answer == null ? List.of() : List.copyOf(answer);
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
