package cn.tealc.asmr.source.openlist.model;

import java.util.List;

/**
 * /api/fs/list 响应中的 data 字段，包含目录内容列表和分页信息。
 *
 * @author Leck
 */
public class OpenListListResponse {

    /** 文件列表 */
    private List<OpenListFile> content;

    /** 总数 */
    private int total;

    public List<OpenListFile> getContent() {
        return content;
    }

    public void setContent(List<OpenListFile> content) {
        this.content = content;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }
}
