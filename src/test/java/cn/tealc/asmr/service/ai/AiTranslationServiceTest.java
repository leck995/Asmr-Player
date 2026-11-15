package cn.tealc.asmr.service.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 标题翻译本地校验测试。
 */
@DisplayName("AI 标题翻译校验测试")
class AiTranslationServiceTest {

    @ParameterizedTest
    @ValueSource(strings = {"RJ123456", "BJ123456", "VJ123456"})
    void validatesAllSupportedWorkIds(String workId) {
        assertTrue(AiTranslationService.validateTranslation(
                "【" + workId + "】耳かき", "【" + workId + "】掏耳").isEmpty());
        assertTrue(AiTranslationService.validateTranslation(
                "【" + workId + "】耳かき", "掏耳")
                .stream().anyMatch(warning -> warning.contains(workId)));
    }

    @Test
    @DisplayName("保留 RJ 编号的译文可通过校验")
    void validateTranslation_preservesWorkId() {
        List<String> warnings = AiTranslationService.validateTranslation(
                "【RJ01234567】耳かきと添い寝", "【RJ01234567】掏耳与陪睡");

        assertTrue(warnings.isEmpty());
    }

    @Test
    @DisplayName("丢失 RJ 编号的译文会被拦截")
    void validateTranslation_rejectsMissingWorkId() {
        List<String> warnings = AiTranslationService.validateTranslation(
                "【RJ01234567】耳かきと添い寝", "掏耳与陪睡");

        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("RJ01234567")));
    }

    @Test
    @DisplayName("多行解释性回复会被拦截")
    void validateTranslation_rejectsMultilineResponse() {
        List<String> warnings = AiTranslationService.validateTranslation("耳かき", "翻译如下：\n掏耳");

        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("换行")));
    }

    @Test
    @DisplayName("文件基础名称中的 Windows 非法字符会被拦截")
    void validateFileNameTranslation_rejectsIllegalWindowsCharacter() {
        List<String> warnings = AiTranslationService.validateFileNameTranslation("耳かき", "掏耳:第一部分");

        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("Windows")));
    }
}
