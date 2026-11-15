package cn.tealc.asmr.service.ai;

import cn.tealc.asmr.config.AiTranslationSettings;
import cn.tealc.asmr.config.Config;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 面向 OpenAI Chat Completions 兼容协议的最小 HTTP 客户端。
 */
public class ChatCompletionsClient {
    private static final Logger log = LoggerFactory.getLogger(ChatCompletionsClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String complete(AiTranslationSettings settings, String apiKey, String userContent) {
        validateSettings(settings, apiKey);
        try {
            Map<String, Object> payload = Map.of(
                    "model", settings.getModel().trim(),
                    "temperature", 0.1,
                    "messages", List.of(
                            Map.of("role", "system", "content", settings.getPrompt().trim()),
                            Map.of("role", "user", "content", userContent)
                    )
            );
            HttpRequest request = HttpRequest.newBuilder(URI.create(normalizeEndpoint(settings.getEndpoint())))
                    .timeout(Duration.ofSeconds(Math.max(5, settings.getTimeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = createHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AiTranslationException("AI 接口请求失败（HTTP " + response.statusCode() + "）：" + extractError(response.body()));
            }
            JsonNode responseJson = objectMapper.readTree(response.body());
            JsonNode choice = responseJson.path("choices").path(0);
            JsonNode message = choice.path("message");
            JsonNode content = message.path("content");
            if (!content.isTextual() || content.asText().isBlank()) {
                String finishReason = choice.path("finish_reason").asText("");
                String refusal = message.path("refusal").asText("");
                log.warn("AI 接口响应缺少 choices[0].message.content：finish_reason={}，refusal={}，原始响应：{}",
                        finishReason, refusal,
                        response.body());
                if ("prohibited_content".equalsIgnoreCase(finishReason)) {
                    throw new AiTranslationException("AI 服务的内容安全策略拒绝了当前翻译请求（prohibited_content）");
                }
                throw new AiTranslationException("AI 接口未返回 choices[0].message.content（finish_reason="
                        + finishReason + "，refusal=" + refusal + "）");
            }
            return content.asText();
        } catch (AiTranslationException e) {
            throw e;
        } catch (Exception e) {
            throw new AiTranslationException("调用 AI 接口失败：" + e.getMessage(), e);
        }
    }

    private static void validateSettings(AiTranslationSettings settings, String apiKey) {
        if (settings == null || settings.getEndpoint() == null || settings.getEndpoint().isBlank()) throw new AiTranslationException("请填写 Chat Completions 接口地址");
        if (settings.getModel() == null || settings.getModel().isBlank()) throw new AiTranslationException("请填写模型名称");
        if (apiKey == null || apiKey.isBlank()) throw new AiTranslationException("请先在设置中填写 API Key");
    }

    private HttpClient createHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder();
        ProxySelector proxySelector = createProxySelector(
                Config.setting.proxyModel.get(),
                Config.setting.proxyHost.get(),
                Config.setting.proxyPort.get());
        if (proxySelector != null) {
            builder.proxy(proxySelector);
        } else if (Config.setting.proxyModel.get()) {
            log.warn("AI 翻译代理已开启，但代理地址或端口无效，当前请求将直连");
        }
        return builder.build();
    }

    static ProxySelector createProxySelector(boolean enabled, String host, String port) {
        if (!enabled || host == null || host.isBlank() || port == null || port.isBlank()) {
            return null;
        }
        try {
            int proxyPort = Integer.parseInt(port.trim());
            if (proxyPort < 1 || proxyPort > 65535) {
                return null;
            }
            return ProxySelector.of(new InetSocketAddress(host.trim(), proxyPort));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String extractError(String body) {
        try {
            JsonNode error = objectMapper.readTree(body).path("error");
            if (error.hasNonNull("message")) return error.get("message").asText();
        } catch (Exception ignored) {
            // 服务端不是 JSON 时不展示完整响应正文，避免泄露敏感信息。
        }
        return "服务端未返回可识别错误信息";
    }

    static String normalizeEndpoint(String endpoint) {
        String value = endpoint.trim();
        if (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (value.endsWith("/v1/chat/completions")) return value;
        if (value.endsWith("/v1")) return value + "/chat/completions";
        return value + "/v1/chat/completions";
    }
}
