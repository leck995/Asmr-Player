package cn.tealc.asmr.model.dlsite;

/**
 * DLSite 作品封面的地址信息，对应 {@code product.json} 中 {@code image_main}、
 * {@code image_thum}、{@code image_thum_mini} 等图片对象。
 * <p>
 * 字段名与 JSON 键一致，由 Jackson 直接映射。接口返回的 {@code url} 不含协议，
 * 因此 {@link #getUrl()} 会补上 {@link #HEAD} 前缀后再交给下载逻辑使用；
 * {@code file_name}、{@code extension} 仅为接口原始信息，本项目未使用。
 *
 * @author Leck
 */
public class DLImage {
    /** 协议前缀：接口返回的地址不含协议，读取时需补全为完整 URL */
    public static final String HEAD="https:";
    private String file_name;
    private String extension;
    private String url;

    public String getFile_name() {
        return file_name;
    }

    public void setFile_name(String file_name) {
        this.file_name = file_name;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public String getUrl() {
        return HEAD+url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
