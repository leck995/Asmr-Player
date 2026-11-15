package cn.tealc.asmr.utils;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 密码摘要（MD5）计算与校验工具。
 * <p>
 * 默认以 UTF-8 编码计算摘要，并提供兼容旧版本 GBK 摘要的校验入口。
 *
 * @author Leck
 */
public class SecureUtils {
    /**
     * 以 UTF-8 编码计算字符串的 MD5 摘要。
     *
     * @param row 待计算原文，可为 null
     * @return 十六进制摘要（小写、可能省略前导零）；{@code row} 为 null 或空串时返回 null
     */
    public static String md5(String row){
        return md5(row, StandardCharsets.UTF_8);
    }

    /**
     * 以指定字符集编码原文并计算 MD5 摘要。
     *
     * @param row     待计算原文，可为 null
     * @param charset 原文编码，如 {@link StandardCharsets#UTF_8}
     * @return 十六进制摘要（小写）；由于 {@link BigInteger#toString(int)} 会丢弃前导零，
     *         长度可能少于标准 32 个十六进制字符；{@code row} 为 null 或空串时返回 null
     */
    public static String md5(String row, Charset charset){
        if (row == null || row.isEmpty())
            return null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");// 生成一个MD5加密计算摘要
            md.update(row.getBytes(charset));// 计算md5函数
            // md.digest() 返回 16 字节（128 位）摘要；BigInteger(1, ...) 将其按无符号数转成小写十六进制字符串。
            // 注意：前导零会被丢弃，故结果长度可能小于标准的 32 个十六进制字符。
            String hashedPwd = new BigInteger(1, md.digest()).toString(16);// 16是表示转换为16进制数
            return hashedPwd;
        }  catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 校验密码摘要，兼容旧版本以平台默认编码（中文 Windows 为 GBK）生成的摘要。
     */
    public static boolean matchesHash(String row, String storedHash) {
        if (row == null || storedHash == null || storedHash.isEmpty())
            return false;
        return storedHash.equalsIgnoreCase(md5(row, StandardCharsets.UTF_8))
                || storedHash.equalsIgnoreCase(md5(row, Charset.forName("GBK")));
    }
}