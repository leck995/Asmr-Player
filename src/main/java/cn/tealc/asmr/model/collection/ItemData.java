package cn.tealc.asmr.model.collection;

import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.model.AsmrWork;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 歌单里的一个收藏项，对应 SQLite 表 {@code asmr_collect_item} 的一行。
 * <p>
 * 由 commons-dbutils 的 {@code BeanHandler} 按列名映射读取（{@code item_name}→{@code name}、
 * {@code work_id}→{@code workId}、{@code sheet_id}→{@code sheetId}，{@code id} 与
 * {@code filepath} 同名），新增时只写入 {@code name}、{@code filepath}、{@code workId}、
 * {@code sheetId} 四列，主键由数据库生成。
 * <p>
 * {@code filepath} 存的是相对作品目录的路径，因此同一物理文件可以在多个歌单中各占一行；
 * 需要在歌单里直接展示或播放作品信息时，用带作品关联的 {@link ItemDataEx}。
 *
 * @author Leck
 */
public class ItemData {
    private int id;
    /** 显示名称，取音频文件名 */
    private String name;
    /** 相对所属作品目录的路径，播放时需与作品的绝对路径拼接还原 */
    private String filepath;
    /** 关联 asmr_works.id 的数据库主键，不是作品编号。 */
    private int workId;
    /** 所属歌单的主键，对应 {@code asmr_collect_sheet.id} */
    private int sheetId;
    /** 预留字段：没有访问器且行映射不会填充，读取作品信息请用 {@link ItemDataEx#getWork()} */
    private AsmrWork work;


    public ItemData() {
    }



    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFilepath() {
        return filepath;
    }

    public void setFilepath(String filepath) {
        this.filepath = filepath;
    }

    public int getWorkId() {
        return workId;
    }

    public void setWorkId(int workId) {
        this.workId = workId;
    }

    public int getSheetId() {
        return sheetId;
    }

    public void setSheetId(int sheetId) {
        this.sheetId = sheetId;
    }

    @Override
    public String toString() {
        return name;
    }
}
