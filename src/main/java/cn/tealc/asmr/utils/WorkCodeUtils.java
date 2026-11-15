package cn.tealc.asmr.utils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 作品编号解析与规范化工具。
 *
 * <p>当前支持 DLsite 常见的 RJ、BJ 和 VJ 编号。</p>
 */
public final class WorkCodeUtils {

    private static final Pattern WORK_CODE_PATTERN = Pattern.compile(
            "(?<![A-Z0-9])((?:RJ|BJ|VJ)\\d+)(?![A-Z0-9])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EXACT_WORK_CODE_PATTERN = Pattern.compile(
            "^(RJ|BJ|VJ)(\\d+)$",
            Pattern.CASE_INSENSITIVE);

    private WorkCodeUtils() {
    }

    /**
     * 从路径、文件夹名或文本中提取作品编号。
     *
     * @param text 待解析文本
     * @return 规范化后的作品编号，无法识别时返回 null
     */
    public static String extract(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = WORK_CODE_PATTERN.matcher(text.toUpperCase(Locale.ROOT));
        return matcher.find() ? normalize(matcher.group(1)) : null;
    }

    /**
     * 规范化作品编号。仅对历史格式中的 RJ 七位数字编号补零，BJ/VJ 保留原数字部分。
     *
     * @param code 作品编号
     * @return 大写规范编号，无法识别时返回 null
     */
    public static String normalize(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        Matcher matcher = EXACT_WORK_CODE_PATTERN.matcher(code.trim().toUpperCase(Locale.ROOT));
        if (!matcher.matches()) {
            return null;
        }
        String prefix = matcher.group(1).toUpperCase(Locale.ROOT);
        String number = matcher.group(2);
        if ("RJ".equals(prefix) && number.length() == 7) {
            number = "0" + number;
        }
        return prefix + number;
    }

    /**
     * 判断文本是否为支持的作品编号。
     */
    public static boolean isSupported(String code) {
        return normalize(code) != null;
    }
}
