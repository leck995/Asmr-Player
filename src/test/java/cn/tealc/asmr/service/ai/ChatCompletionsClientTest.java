package cn.tealc.asmr.service.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Chat Completions 兼容接口地址规范化测试。
 */
@DisplayName("Chat Completions 接口地址测试")
class ChatCompletionsClientTest {

    @Test
    @DisplayName("完整接口地址保持不变")
    void normalizeEndpoint_keepsFullEndpoint() {
        assertEquals("https://example.com/v1/chat/completions",
                ChatCompletionsClient.normalizeEndpoint("https://example.com/v1/chat/completions"));
    }

    @Test
    @DisplayName("v1 基础地址补全 chat completions 路径")
    void normalizeEndpoint_completesV1BaseUrl() {
        assertEquals("https://example.com/v1/chat/completions",
                ChatCompletionsClient.normalizeEndpoint("https://example.com/v1/"));
    }

    @Test
    @DisplayName("服务根地址补全 v1 chat completions 路径")
    void normalizeEndpoint_completesRootBaseUrl() {
        assertEquals("https://example.com/v1/chat/completions",
                ChatCompletionsClient.normalizeEndpoint("https://example.com"));
    }

    @Test
    @DisplayName("启用且配置有效时创建 HTTP 代理")
    void createProxySelector_usesConfiguredProxy() {
        ProxySelector selector = ChatCompletionsClient.createProxySelector(true, "127.0.0.1", "7890");

        assertNotNull(selector);
        Proxy proxy = selector.select(URI.create("https://api.example.com")).get(0);
        assertEquals("127.0.0.1", ((java.net.InetSocketAddress) proxy.address()).getHostString());
        assertEquals(7890, ((java.net.InetSocketAddress) proxy.address()).getPort());
    }

    @Test
    @DisplayName("代理配置无效时不创建代理")
    void createProxySelector_returnsNullForInvalidConfiguration() {
        assertNull(ChatCompletionsClient.createProxySelector(true, "127.0.0.1", "0"));
        assertNull(ChatCompletionsClient.createProxySelector(true, "127.0.0.1", "invalid"));
        assertNull(ChatCompletionsClient.createProxySelector(false, "127.0.0.1", "7890"));
    }
}
