package cn.tealc.asmr.service.ai;

/**
 * AI 翻译请求、响应或本地校验失败时抛出的异常。
 */
public class AiTranslationException extends RuntimeException {
    public AiTranslationException(String message) { super(message); }
    public AiTranslationException(String message, Throwable cause) { super(message, cause); }
}
