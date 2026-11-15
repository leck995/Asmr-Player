package cn.tealc.asmr.config;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * AI 标题翻译配置。
 * <p>
 * 保存 AI 翻译所需的接口参数和密钥；API Key 在 JSON 中加密存储，读取后以明文供调用方使用。
 */
public class AiTranslationSettings {

    public static final String DEFAULT_ENDPOINT = "https://api.openai.com/v1/chat/completions";
    public static final String DEFAULT_PROMPT = """
            你是一个本地媒体库的名称本地化助手。将输入的作品标题或文件基础名称翻译为简体中文，仅用于本地展示。
            保留 RJ、DL、BJ、VJ 编号、社团名、作者名、角色名、版本号、日期、CD 编号和技术标识。
            不确定的人名、拟声词和专有名词保留原文，禁止编造原文不存在的信息。
            只返回指定 JSON，不能输出 Markdown、代码块或解释文字。
            """;

    private String endpoint = DEFAULT_ENDPOINT;
    private String model = "";
    private String apiKey = "";
    private String promptPreset = "通用保守翻译";
    private String prompt = DEFAULT_PROMPT;
    private int batchSize = 10;
    private int maxConcurrency = 2;
    private int timeoutSeconds = 10;

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    @JsonSerialize(using = EncryptedSecret.Serializer.class)
    public String getApiKey() { return apiKey; }
    @JsonDeserialize(using = EncryptedSecret.Deserializer.class)
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getPromptPreset() { return promptPreset; }
    public void setPromptPreset(String promptPreset) { this.promptPreset = promptPreset; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getMaxConcurrency() { return maxConcurrency; }
    public void setMaxConcurrency(int maxConcurrency) { this.maxConcurrency = maxConcurrency; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
