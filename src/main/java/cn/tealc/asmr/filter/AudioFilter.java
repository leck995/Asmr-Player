package cn.tealc.asmr.filter;

import cn.tealc.asmr.model.SupportedAudioFormat;

import java.io.File;
import java.io.FilenameFilter;

/**
 * 文件名过滤器：筛选出扩展名属于 {@link SupportedAudioFormat} 的音频文件。
 * <p>
 * 仅接受真实存在的文件（目录会被排除），扩展名大小写不敏感。
 *
 * @author Leck
 */
public class AudioFilter implements FilenameFilter {
    /**
     * 判断候选项是否为受支持的音频文件。
     *
     * @param dir  所在目录
     * @param name 待判断的文件名（不含目录）
     * @return 是普通文件且扩展名在 {@link SupportedAudioFormat} 列表中时为 {@code true}
     */
    @Override
    public boolean accept(File dir, String name) {
        if (!new File(dir, name).isFile()) {
            return false;
        }
        int i = name.lastIndexOf(".");
        String ext = i == -1 ? "" : name.substring(i + 1).toUpperCase();
        return   SupportedAudioFormat.contains(ext);
    }
}
