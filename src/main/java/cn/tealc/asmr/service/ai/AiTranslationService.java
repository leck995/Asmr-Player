package cn.tealc.asmr.service.ai;

import cn.tealc.asmr.di.Inject;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.translation.FileNameTranslationPreviewItem;
import cn.tealc.asmr.model.translation.TranslationItemType;
import cn.tealc.asmr.model.translation.TranslationPreviewItem;
import cn.tealc.asmr.config.AiTranslationSettings;
import cn.tealc.asmr.service.WorkService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 标题翻译业务服务：按批次并发调用兼容接口，并在写库前完成本地校验。
 */
public class AiTranslationService {
    private static final Logger log = LoggerFactory.getLogger(AiTranslationService.class);
    private static final Pattern PROTECTED_TOKEN = Pattern.compile(
            "(?i)\\b(?:RJ|DL|BJ|VJ)\\d{4,}\\b|\\b(?:CD|Track)\\s*\\d+\\b|\\bver\\.?\\s*\\d+(?:\\.\\d+)*\\b|\\b\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}\\b");
    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_FILE_BASE_NAME_LENGTH = 200;
    private static final Pattern WINDOWS_ILLEGAL_FILE_NAME_CHARACTERS = Pattern.compile("[\\\\/:*?\\\"<>|]");

    private final ChatCompletionsClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    private WorkService workService;

    public AiTranslationService() {
        this(new ChatCompletionsClient());
    }

    AiTranslationService(ChatCompletionsClient client) {
        this.client = client;
    }

    /**
     * 按配置的批次和并发数生成预览结果，不写入数据库。
     */
    public List<TranslationPreviewItem> previewWorks(List<AsmrWork> works,
                                                      AiTranslationSettings settings,
                                                      String apiKey) {
        if (works == null || works.isEmpty()) return List.of();
        List<List<AsmrWork>> batches = split(works, clamp(settings.getBatchSize(), 1, 50));
        int concurrency = clamp(settings.getMaxConcurrency(), 1, 8);
        try (ExecutorService executor = Executors.newFixedThreadPool(concurrency, Thread.ofVirtual().factory())) {
            List<CompletableFuture<List<TranslationPreviewItem>>> futures = batches.stream()
                    .map(batch -> CompletableFuture.supplyAsync(() -> previewBatch(batch, settings, apiKey), executor))
                    .toList();
            List<TranslationPreviewItem> result = new ArrayList<>();
            for (CompletableFuture<List<TranslationPreviewItem>> future : futures) {
                result.addAll(future.join());
            }
            return result;
        } catch (Exception e) {
            throw new AiTranslationException("生成翻译预览失败：" + e.getMessage(), e);
        }
    }

    /**
     * 按配置生成文件基础名称翻译预览，不会修改数据库或磁盘文件。
     */
    public List<FileNameTranslationPreviewItem> previewFileNames(List<String> baseNames,
                                                                  AiTranslationSettings settings,
                                                                  String apiKey) {
        if (baseNames == null || baseNames.isEmpty()) return List.of();
        List<String> uniqueBaseNames = baseNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
        if (uniqueBaseNames.isEmpty()) return List.of();
        List<List<String>> batches = split(uniqueBaseNames, clamp(settings.getBatchSize(), 1, 50));
        int concurrency = clamp(settings.getMaxConcurrency(), 1, 8);
        try (ExecutorService executor = Executors.newFixedThreadPool(concurrency, Thread.ofVirtual().factory())) {
            List<CompletableFuture<List<FileNameTranslationPreviewItem>>> futures = batches.stream()
                    .map(batch -> CompletableFuture.supplyAsync(() -> previewFileNameBatch(batch, settings, apiKey), executor))
                    .toList();
            List<FileNameTranslationPreviewItem> result = new ArrayList<>();
            for (CompletableFuture<List<FileNameTranslationPreviewItem>> future : futures) {
                result.addAll(future.join());
            }
            return result;
        } catch (Exception e) {
            throw new AiTranslationException("生成文件名翻译预览失败：" + e.getMessage(), e);
        }
    }

    /**
     * 将用户确认且已通过校验的候选译名写入 titleCN。
     */
    public boolean applyTitle(TranslationPreviewItem item, String title) {
        if (item == null || title == null || title.isBlank()) return false;
        String normalized = title.trim();
        if (normalized.length() > MAX_TITLE_LENGTH) {
            throw new AiTranslationException("中文标题超过 " + MAX_TITLE_LENGTH + " 个字符，无法保存");
        }
        List<String> warnings = validateTranslation(item.sourceText(), normalized);
        if (!warnings.isEmpty()) {
            throw new AiTranslationException("标题未通过本地校验：" + String.join("；", warnings));
        }
        return workService.updateWorkTitleCN(item.work(), normalized);
    }

    private List<TranslationPreviewItem> previewBatch(List<AsmrWork> works,
                                                       AiTranslationSettings settings,
                                                       String apiKey) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("targetLanguage", "zh-CN");
        request.put("items", works.stream().map(this::toRequestItem).toList());
        request.put("responseFormat", Map.of("items", List.of(Map.of(
                "id", "与输入 id 完全一致",
                "translatedText", "翻译后的简体中文标题",
                "confidence", "high、medium 或 low"
        ))));
        request.put("rules", List.of(
                "仅返回一个合法 JSON 对象，不要 Markdown 或解释",
                "每个输入项必须返回一次，id 不得变化",
                "protectedTokens 必须逐字保留",
                "不确定时保留原文并将 confidence 设为 low"
        ));
        try {
            String content = client.complete(settings, apiKey, objectMapper.writeValueAsString(request));
            return parseResponse(works, content);
        } catch (Exception e) {
            String warning = e instanceof AiTranslationException ? e.getMessage() : "生成请求失败：" + e.getMessage();
            return works.stream().map(work -> invalid(work, warning)).toList();
        }
    }

    private List<FileNameTranslationPreviewItem> previewFileNameBatch(List<String> baseNames,
                                                                       AiTranslationSettings settings,
                                                                       String apiKey) {
        Map<String, String> baseNameById = new LinkedHashMap<>();
        for (int index = 0; index < baseNames.size(); index++) {
            baseNameById.put(fileNameItemId(index), baseNames.get(index));
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("targetLanguage", "zh-CN");
        request.put("items", baseNameById.entrySet().stream()
                .map(entry -> toFileNameRequestItem(entry.getKey(), entry.getValue()))
                .toList());
        request.put("responseFormat", Map.of("items", List.of(Map.of(
                "id", "与输入 id 完全一致",
                "translatedText", "翻译后的简体中文文件基础名称或目录名称；文件不含扩展名",
                "confidence", "high、medium 或 low"
        ))));
        request.put("rules", List.of(
                "仅返回一个合法 JSON 对象，不要 Markdown 或解释",
                "每个输入项必须返回一次，id 不得变化",
                "sourceText 是文件基础名称或目录名称；文件名称不含扩展名，不要添加、删除或猜测扩展名",
                "protectedTokens 必须逐字保留",
                "不确定时保留原文并将 confidence 设为 low"
        ));
        try {
            String content = client.complete(settings, apiKey, objectMapper.writeValueAsString(request));
            log.info("AI 文件名翻译原始响应：{}", content);
            return parseFileNameResponse(baseNameById, content);
        } catch (Exception e) {
            String warning = e instanceof AiTranslationException ? e.getMessage() : "生成请求失败：" + e.getMessage();
            log.warn("AI 文件名翻译请求失败：{}", warning, e);
            return baseNameById.entrySet().stream()
                    .map(entry -> invalidFileName(entry.getKey(), entry.getValue(), warning))
                    .toList();
        }
    }

    private Map<String, Object> toRequestItem(AsmrWork work) {
        return Map.of(
                "id", itemId(work),
                "type", TranslationItemType.WORK_TITLE.name(),
                "sourceText", work.getTitle(),
                "protectedTokens", protectedTokens(work.getTitle())
        );
    }

    private Map<String, Object> toFileNameRequestItem(String id, String baseName) {
        return Map.of(
                "id", id,
                "type", TranslationItemType.FILE_NAME.name(),
                "sourceText", baseName,
                "protectedTokens", protectedTokens(baseName)
        );
    }

    private List<TranslationPreviewItem> parseResponse(List<AsmrWork> works, String content) {
        try {
            JsonNode itemsNode = objectMapper.readTree(stripCodeFence(content)).path("items");
            if (!itemsNode.isArray()) throw new AiTranslationException("AI 返回格式无效：缺少 items 数组");
            Map<String, JsonNode> responseById = new HashMap<>();
            for (JsonNode node : itemsNode) {
                if (node.hasNonNull("id")) responseById.put(node.get("id").asText(), node);
            }
            List<TranslationPreviewItem> result = new ArrayList<>();
            for (AsmrWork work : works) {
                JsonNode node = responseById.get(itemId(work));
                if (node == null || !node.path("translatedText").isTextual()) {
                    result.add(invalid(work, "AI 未返回该作品的有效翻译结果"));
                    continue;
                }
                String translated = node.path("translatedText").asText().trim();
                List<String> warnings = validateTranslation(work.getTitle(), translated);
                result.add(new TranslationPreviewItem(work, itemId(work), TranslationItemType.WORK_TITLE,
                        work.getTitle(), translated, node.path("confidence").asText("low"), warnings, warnings.isEmpty()));
            }
            return result;
        } catch (Exception e) {
            String warning = e instanceof AiTranslationException ? e.getMessage() : "AI 返回内容不是有效 JSON";
            return works.stream().map(work -> invalid(work, warning)).toList();
        }
    }

    private List<FileNameTranslationPreviewItem> parseFileNameResponse(Map<String, String> baseNameById, String content) {
        try {
            JsonNode itemsNode = objectMapper.readTree(stripCodeFence(content)).path("items");
            if (!itemsNode.isArray()) throw new AiTranslationException("AI 返回格式无效：缺少 items 数组");
            Map<String, JsonNode> responseById = new HashMap<>();
            for (JsonNode node : itemsNode) {
                if (node.hasNonNull("id")) responseById.put(node.get("id").asText(), node);
            }
            List<FileNameTranslationPreviewItem> result = new ArrayList<>();
            for (Map.Entry<String, String> entry : baseNameById.entrySet()) {
                String id = entry.getKey();
                String baseName = entry.getValue();
                JsonNode node = responseById.get(id);
                if (node == null || !node.path("translatedText").isTextual()) {
                    log.warn("AI 文件名翻译未返回有效结果：基础名称={}，期望 id={}", baseName, id);
                    result.add(invalidFileName(id, baseName, "AI 未返回该文件名的有效翻译结果"));
                    continue;
                }
                String translated = node.path("translatedText").asText().trim();
                List<String> warnings = validateFileNameTranslation(baseName, translated);
                if (!warnings.isEmpty()) {
                    log.warn("AI 文件名翻译未通过本地校验：基础名称={}，译文={}，原因={}",
                            baseName, translated, String.join("；", warnings));
                }
                result.add(new FileNameTranslationPreviewItem(id, baseName, translated,
                        node.path("confidence").asText("low"), warnings, warnings.isEmpty()));
            }
            return result;
        } catch (Exception e) {
            String warning = e instanceof AiTranslationException ? e.getMessage() : "AI 返回内容不是有效 JSON";
            log.warn("AI 文件名翻译响应解析失败：{}，原始响应：{}", warning, content, e);
            return baseNameById.entrySet().stream()
                    .map(entry -> invalidFileName(entry.getKey(), entry.getValue(), warning))
                    .toList();
        }
    }

    static List<String> validateTranslation(String source, String translated) {
        List<String> warnings = new ArrayList<>();
        if (translated == null || translated.isBlank()) {
            warnings.add("译文为空");
            return warnings;
        }
        if (translated.length() > MAX_TITLE_LENGTH) warnings.add("译文超过 " + MAX_TITLE_LENGTH + " 个字符");
        if (translated.indexOf('\n') >= 0 || translated.indexOf('\r') >= 0) warnings.add("译文包含换行");
        if (translated.contains("\u0060\u0060\u0060") || translated.startsWith("{") || translated.startsWith("[")) warnings.add("译文包含非标题格式内容");
        for (String token : protectedTokens(source)) {
            if (!translated.contains(token)) warnings.add("未保留关键标识：" + token);
        }
        return warnings;
    }

    static List<String> validateFileNameTranslation(String source, String translated) {
        List<String> warnings = new ArrayList<>();
        if (translated == null || translated.isBlank()) {
            warnings.add("译文为空");
            return warnings;
        }
        if (translated.length() > MAX_FILE_BASE_NAME_LENGTH) warnings.add("译文超过 " + MAX_FILE_BASE_NAME_LENGTH + " 个字符");
        if (translated.indexOf('\n') >= 0 || translated.indexOf('\r') >= 0) warnings.add("译文包含换行");
        if (translated.contains("\u0060\u0060\u0060") || translated.startsWith("{") || translated.startsWith("[")) warnings.add("译文包含非文件名格式内容");
        if (WINDOWS_ILLEGAL_FILE_NAME_CHARACTERS.matcher(translated).find()) warnings.add("译文包含 Windows 文件名非法字符");
        if (translated.endsWith(".") || translated.endsWith(" ") || ".".equals(translated) || "..".equals(translated)) warnings.add("译文不是有效文件基础名称");
        for (String token : protectedTokens(source)) {
            if (!translated.contains(token)) warnings.add("未保留关键标识：" + token);
        }
        return warnings;
    }

    private static List<String> protectedTokens(String text) {
        if (text == null || text.isBlank()) return List.of();
        Matcher matcher = PROTECTED_TOKEN.matcher(text);
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) tokens.add(matcher.group());
        return tokens;
    }

    private static String itemId(AsmrWork work) {
        return "work:" + work.getId() + ":title";
    }

    private static String fileNameItemId(int index) {
        return "file:" + index;
    }

    private static TranslationPreviewItem invalid(AsmrWork work, String warning) {
        return new TranslationPreviewItem(work, itemId(work), TranslationItemType.WORK_TITLE,
                work.getTitle(), work.getTitle(), "low", List.of(warning), false);
    }

    private static FileNameTranslationPreviewItem invalidFileName(String id, String baseName, String warning) {
        return new FileNameTranslationPreviewItem(id, baseName, baseName, "low", List.of(warning), false);
    }

    private static String stripCodeFence(String value) {
        String result = value == null ? "" : value.trim();
        String fence = "\u0060\u0060\u0060";
        if (result.startsWith(fence)) {
            int firstLineEnd = result.indexOf('\n');
            int lastFence = result.lastIndexOf(fence);
            if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
                result = result.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        return result;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static <T> List<List<T>> split(Collection<T> values, int size) {
        List<T> source = new ArrayList<>(values);
        List<List<T>> result = new ArrayList<>();
        for (int start = 0; start < source.size(); start += size) {
            result.add(source.subList(start, Math.min(source.size(), start + size)));
        }
        return result;
    }
}
