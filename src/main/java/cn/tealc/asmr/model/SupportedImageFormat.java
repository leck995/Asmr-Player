package cn.tealc.asmr.model;

/**
 * 可作为作品背景图收集的图片文件扩展名取值。
 * <p>
 * {@code BackgroundService} 扫描作品目录、{@code ImageViewerView} 展示图片时，
 * 用 {@code contains(...)} 按扩展名判断是否为受支持的图片。
 *
 * @author Leck
 */
public enum SupportedImageFormat {
    JPG("jpg"),
    PNG("png"),
    JPEG("jpeg");


    private String filesuffix;

    private SupportedImageFormat(String filesuffix) {
        this.filesuffix = filesuffix;
    }

    public String getFilesuffix() {
        return this.filesuffix;
    }

    public static boolean contains(String s)
    {
        s=s.toUpperCase();
        for(SupportedImageFormat choice:values())
            if (choice.name().equals(s))
                return true;
        return false;
    }
}