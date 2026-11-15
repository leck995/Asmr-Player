package cn.tealc.asmr.source.openlist.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OpenList /api/fs/list 和 /api/fs/get 返回的文件对象。
 * <p>
 * 字段与 OpenList API 响应一一对应，通过 {@link JsonProperty} 映射 snake_case。
 *
 * @author Leck
 */
public class OpenListFile {

    /** 文件名 */
    private String name;

    /** 文件大小（字节） */
    private long size;

    /** 是否为目录 */
    @JsonProperty("is_dir")
    private boolean dir;

    /** 签名（用于构造签名直链） */
    private String sign;

    /** 缩略图 URL */
    private String thumb;

    /** 文件类型 */
    private int type;

    /** 原始直链（仅 /api/fs/get 返回，list 不返回此字段） */
    @JsonProperty("raw_url")
    private String rawUrl;

    /** 修改时间 */
    private String modified;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public boolean isDir() {
        return dir;
    }

    public void setDir(boolean dir) {
        this.dir = dir;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }

    public String getThumb() {
        return thumb;
    }

    public void setThumb(String thumb) {
        this.thumb = thumb;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getRawUrl() {
        return rawUrl;
    }

    public void setRawUrl(String rawUrl) {
        this.rawUrl = rawUrl;
    }

    public String getModified() {
        return modified;
    }

    public void setModified(String modified) {
        this.modified = modified;
    }
}
