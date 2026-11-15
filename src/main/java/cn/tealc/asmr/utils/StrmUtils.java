package cn.tealc.asmr.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * STRM 文件解析工具。
 * <p>
 * strm 文件是文本文件，内容为单行流媒体 URL（HTTP/HTTPS）。
 * 播放时调用 {@link #readUrl(File)} 读取文件内容获取真实播放地址。
 *
 * @author Leck
 */
public class StrmUtils {

    private static final Logger LOG = LoggerFactory.getLogger(StrmUtils.class);

    /**
     * 读取 strm 文件内容，返回第一行非空 URL（去首尾空白）。
     * <p>
     * strm 文件格式为单行 HTTP/HTTPS URL。仅当读取到的内容以
     * {@code http://} 或 {@code https://} 开头时才返回，否则返回 null。
     *
     * @param strmFile strm 文件
     * @return 流媒体 URL，文件为空或读取失败返回 null
     */
    public static String readUrl(File strmFile) {
        if (strmFile == null || !strmFile.exists()) {
            return null;
        }
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(strmFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                        return trimmed;
                    }
                    // 第一行非空但不是 URL，返回 null
                    return null;
                }
            }
        } catch (Exception e) {
            LOG.error("读取 strm 文件失败: {}", strmFile.getPath(), e);
        }
        return null;
    }
}
