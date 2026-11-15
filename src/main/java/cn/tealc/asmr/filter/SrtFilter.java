package cn.tealc.asmr.filter;


import java.io.File;
import java.io.FilenameFilter;

/**
 * 文件名过滤器：匹配 {@code .srt} 字幕文件。
 * <p>
 * 仅按文件名后缀判断（大小写不敏感），不校验路径是否为文件。
 *
 * @author Leck
 */
public class SrtFilter implements FilenameFilter {
    /**
     * 判断候选文件名是否以 {@code .srt} 结尾。
     *
     * @param dir  所在目录（未使用）
     * @param name 待判断的文件名（不含目录）
     * @return 文件名以 {@code .srt} 结尾时为 {@code true}
     */
    @Override
    public boolean accept(File dir, String name) {
        return name.toLowerCase().endsWith(".srt");
    }
}