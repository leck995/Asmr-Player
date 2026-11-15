package cn.tealc.asmr.model;

/**
 * 应用可识别并加入播放列表的音频文件扩展名取值。
 * <p>
 * {@code AudioFilter}、{@code PlaylistService}、{@code OpenListMediaSource} 与作品
 * 详情页均以 {@code contains(...)} 按扩展名过滤音频文件；{@code STRM} 为指向真实
 * 音频的快捷文本文件，不在此读取内容。
 *
 * @author Leck
 */
public enum SupportedAudioFormat {
    OGG("ogg"),
    MP3("mp3"),
    FLAC("flac"),
    M4A("m4a"),
    WAV("wav"),
    STRM("strm"),
    M4B("m4b");

    private String filesuffix;

    private SupportedAudioFormat(String filesuffix) {
        this.filesuffix = filesuffix;
    }

    public String getFilesuffix() {
        return this.filesuffix;
    }


    public static boolean contains(String s)
    {
        s=s.toUpperCase();
        for(SupportedAudioFormat choice:values())
            if (choice.name().equals(s))
                return true;
        return false;
    }
}
