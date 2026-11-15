package cn.tealc.asmr.utils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 为 SQLite 中的凭据字段提供固定密钥加密：数据库可随 data.db 一起迁移，仅作静态混淆。
 */
public final class CredentialCipher {
    private static final SecretKey KEY = new SecretKeySpec(
            Base64.getDecoder().decode("1fWHLcQq+qcOFUM0Wq7BOGcBWX65HhLMUBEkUVvkE/A="), "AES");
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CredentialCipher() {
    }

    public static String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, KEY, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new IllegalStateException("加密存储源凭据失败", e);
        }
    }

    public static String decrypt(String ciphertext) {
        try {
            byte[] value = Base64.getDecoder().decode(ciphertext);
            if (value.length <= IV_LENGTH) {
                throw new IllegalArgumentException("凭据密文格式无效");
            }
            byte[] iv = java.util.Arrays.copyOfRange(value, 0, IV_LENGTH);
            byte[] encrypted = java.util.Arrays.copyOfRange(value, IV_LENGTH, value.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, KEY, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("读取存储源凭据失败", e);
        }
    }
}
