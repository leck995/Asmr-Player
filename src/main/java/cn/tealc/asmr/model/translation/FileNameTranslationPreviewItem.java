package cn.tealc.asmr.model.translation;

import java.util.List;

/**
 * 文件基础名称的翻译预览结果。
 */
public record FileNameTranslationPreviewItem(
        String id,
        String sourceBaseName,
        String translatedBaseName,
        String confidence,
        List<String> warnings,
        boolean valid
) {
}
