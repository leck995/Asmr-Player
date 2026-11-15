package cn.tealc.asmr.source.openlist.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * OpenList 文件系统 API 通用请求体。
 * <p>
 * 不同接口使用不同字段子集，用 {@link JsonInclude}({@code NON_NULL}) 排除 null 字段，
 * 避免向 API 发送多余参数。
 *
 * @author Leck
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenListRequest {

    /** 路径（list/get/mkdir/put 用） */
    private String path;

    /** 密码（list/get 用） */
    private String password;

    /** 页码（list 用） */
    private Integer page;

    /** 每页条数（list 用） */
    @JsonProperty("per_page")
    private Integer perPage;

    /** 是否强制刷新缓存（list/get 用） */
    private Boolean refresh;

    /** 源目录（move/copy/remove 用） */
    @JsonProperty("src_dir")
    private String srcDir;

    /** 目标目录（move/copy 用） */
    @JsonProperty("dst_dir")
    private String dstDir;

    /** 文件名列表（move/copy/remove 用） */
    private List<String> names;

    /** 目录（remove 用） */
    private String dir;

    /** 新文件名（rename 用，不支持 '/'） */
    private String name;

    public OpenListRequest() {
    }

    public static OpenListRequest of(String path) {
        OpenListRequest req = new OpenListRequest();
        req.path = path;
        return req;
    }

    public static OpenListRequest of(String path, boolean refresh) {
        OpenListRequest req = new OpenListRequest();
        req.path = path;
        req.refresh = refresh;
        return req;
    }

    public static OpenListRequest rename(String path, String newName) {
        OpenListRequest req = new OpenListRequest();
        req.path = path;
        req.name = newName;
        return req;
    }

    public static OpenListRequest move(String srcDir, String dstDir, List<String> names) {
        OpenListRequest req = new OpenListRequest();
        req.srcDir = srcDir;
        req.dstDir = dstDir;
        req.names = names;
        return req;
    }

    public static OpenListRequest remove(String dir, List<String> names) {
        OpenListRequest req = new OpenListRequest();
        req.dir = dir;
        req.names = names;
        return req;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getPerPage() {
        return perPage;
    }

    public void setPerPage(Integer perPage) {
        this.perPage = perPage;
    }

    public Boolean getRefresh() {
        return refresh;
    }

    public void setRefresh(Boolean refresh) {
        this.refresh = refresh;
    }

    public String getSrcDir() {
        return srcDir;
    }

    public void setSrcDir(String srcDir) {
        this.srcDir = srcDir;
    }

    public String getDstDir() {
        return dstDir;
    }

    public void setDstDir(String dstDir) {
        this.dstDir = dstDir;
    }

    public List<String> getNames() {
        return names;
    }

    public void setNames(List<String> names) {
        this.names = names;
    }

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
