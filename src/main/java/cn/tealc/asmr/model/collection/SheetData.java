package cn.tealc.asmr.model.collection;

/**
 * 一个歌单（收藏夹）的主记录，对应 SQLite 表 {@code asmr_collect_sheet} 的一行。
 * <p>
 * 由 commons-dbutils 的 {@code BeanHandler} 按列名映射：{@code sheet_name}→{@code sheetName}、
 * {@code create_date_time}→{@code createDateTime}、{@code update_date_time}→{@code updateDateTime}，
 * {@code id}、{@code description}、{@code top} 同名。歌单内的具体收藏条目见 {@link ItemData}，
 * 删除歌单时其条目由外键 {@code ON DELETE CASCADE} 或先行的清理逻辑一并删除。
 * <p>
 * 时间列存的是 SQLite {@code datetime('now','localtime')} 生成的本地时间文本，不是时间戳。
 * {@code top} 在建表语句中默认值为 1，而创建歌单的 INSERT 未显式写该列，
 * 因此新建歌单在按 {@code top DESC} 排序的列表中默认就排在前面。
 * <p>
 * {@link #toString()} 返回歌单名，供 ComboBox 等控件直接展示。
 *
 * @author Leck
 */
public class SheetData {
    private int id;
    private String sheetName;
    /** 歌单描述，数据库列为 {@code NOT NULL DEFAULT ''} */
    private String description = "";
    /** 创建时间文本，写入后不再变化 */
    private String createDateTime;
    /** 最后修改时间文本，更新名称与描述时刷新，仅切换置顶不刷新 */
    private String updateDateTime;
    private boolean top; //置顶


    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getSheetName() {
        return sheetName;
    }

    public void setSheetName(String sheetName) {
        this.sheetName = sheetName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCreateDateTime() {
        return createDateTime;
    }

    public void setCreateDateTime(String createDateTime) {
        this.createDateTime = createDateTime;
    }

    public String getUpdateDateTime() {
        return updateDateTime;
    }

    public void setUpdateDateTime(String updateDateTime) {
        this.updateDateTime = updateDateTime;
    }

    public boolean isTop() {
        return top;
    }

    public void setTop(boolean top) {
        this.top = top;
    }

    @Override
    public String toString() {
        return sheetName;
    }
}