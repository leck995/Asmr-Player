package cn.tealc.asmr.model.translation;

import cn.tealc.asmr.model.AsmrWork;

import java.util.List;

/**
 * 供界面预览和用户确认的单项翻译结果。
 */
public record TranslationPreviewItem(
        AsmrWork work,
        String id,
        TranslationItemType type,
        String sourceText,
        String translatedText,
        String confidence,
        List<String> warnings,
        boolean valid
) {
    public boolean requiresConfirmation() {
        return !valid || !"high".equalsIgnoreCase(confidence) || !warnings.isEmpty();
    }
}
