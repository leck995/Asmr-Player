package cn.tealc.asmr.model;

/**
 * 支持的视频格式。
 * <p>
 * 扩展名匹配统一大小写，覆盖常见本地视频与 strm 目标流格式
 * （{@code m3u8}、{@code ts} 常见于 OpenList / 网络串流）。
 *
 * @author Leck
 */
public enum SupportedVideoFormat {
    MP4("mp4"),
    MKV("mkv"),
    AVI("avi"),
    WEBM("webm"),
    MOV("mov"),
    FLV("flv"),
    WMV("wmv"),
    M4V("m4v"),
    TS("ts"),
    M3U8("m3u8");

    private String filesuffix;

    private SupportedVideoFormat(String filesuffix) {
        this.filesuffix = filesuffix;
    }

    public String getFilesuffix() {
        return this.filesuffix;
    }

    /**
     * 判断扩展名是否为受支持的视频格式，大小写不敏感。
     *
     * @param s 扩展名（不含点），如 {@code "mp4"}、{@code "MKV"}
     * @return 是受支持格式返回 true
     */
    public static boolean contains(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        String upper = s.toUpperCase();
        for (SupportedVideoFormat choice : values())
            if (choice.name().equals(upper))
                return true;
        return false;
    }
}
