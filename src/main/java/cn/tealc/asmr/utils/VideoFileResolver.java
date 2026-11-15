package cn.tealc.asmr.utils;

import cn.tealc.asmr.model.SupportedVideoFormat;
import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.source.MediaSource;

/**
 * 视频文件判定与播放地址解析。
 * <p>
 * 除了按扩展名直接判断视频，还处理 {@code .strm} 文件：strm 内容只是一行 URL，
 * 既可能指向视频也可能指向音频，因此需要读出内部地址后再按其后缀判断。
 * <p>
 * 与来源无关：本地与 OpenList 都通过 {@link MediaSource#resolvePlayUrl} 得到真实地址。
 *
 * @author Leck
 */
public final class VideoFileResolver {

    /** 判断 strm 目标时需要读取文件内容，避免重复读，由调用方一次性拿到地址 */
    private VideoFileResolver() {
    }

    /**
     * 判断文件是否为视频。
     * <p>
     * 普通文件按扩展名判断；{@code .strm} 读取内部 URL 后按 URL 后缀判断，
     * 读取失败或非视频时返回 false。
     *
     * @param file   文件
     * @param source 文件所属来源，strm 需要它读取内容
     * @return 是视频返回 true
     */
    public static boolean isVideoFile(AsmrFile file, MediaSource source) {
        if (file == null || file.isDir()) {
            return false;
        }
        String type = file.getType();
        if ("strm".equalsIgnoreCase(type)) {
            return isVideoUrl(resolveUrl(file, source));
        }
        return SupportedVideoFormat.contains(type);
    }

    /**
     * 解析可交给外部播放器的地址。
     *
     * @param file   文件
     * @param source 文件所属来源
     * @return 播放地址，解析失败返回 null
     */
    public static String resolveUrl(AsmrFile file, MediaSource source) {
        if (file == null || source == null) {
            return null;
        }
        try {
            String url = source.resolvePlayUrl(file.getFilepath());
            return url == null || url.isBlank() ? null : url;
        } catch (RuntimeException exception) {
            // OpenList 直链获取失败等情况：交由上层提示，此处不中断流程
            return null;
        }
    }

    /**
     * 按 URL 判断是否为视频地址。
     * <p>
     * 取路径部分的后缀，忽略查询参数（签名串、token 等），m3u8 等流格式一并识别。
     *
     * @param url 播放地址
     * @return 是视频地址返回 true
     */
    public static boolean isVideoUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String path = url;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        if (dot <= slash || dot == path.length() - 1) {
            return false;
        }
        return SupportedVideoFormat.contains(path.substring(dot + 1));
    }
}
