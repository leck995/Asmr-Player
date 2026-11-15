package cn.tealc.asmr.config;

import cn.tealc.asmr.utils.CredentialCipher;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * setting.json 中敏感字段的静态加密编解码。
 * <p>
 * 密文带 {@link #PREFIX} 前缀，用于区分旧版本明文并支持一次性迁移；
 * 内存中始终保存明文，仅在 JSON 读写时加解密。
 */
public final class EncryptedSecret {
    private static final Logger log = LoggerFactory.getLogger(EncryptedSecret.class);
    private static final String PREFIX = "enc:v1:";

    private EncryptedSecret() {
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public static String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty() || isEncrypted(plaintext)) {
            return plaintext;
        }
        return PREFIX + CredentialCipher.encrypt(plaintext);
    }

    public static String decrypt(String value) {
        if (!isEncrypted(value)) {
            return value;
        }
        try {
            return CredentialCipher.decrypt(value.substring(PREFIX.length()));
        } catch (RuntimeException e) {
            log.warn("设置中的敏感字段无法解密，按空值处理", e);
            return "";
        }
    }

    public static final class Serializer extends JsonSerializer<String> {
        @Override
        public void serialize(String value, JsonGenerator generator, SerializerProvider serializers)
                throws IOException {
            generator.writeString(encrypt(value));
        }
    }

    public static final class Deserializer extends JsonDeserializer<String> {
        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return decrypt(parser.getValueAsString());
        }
    }
}
