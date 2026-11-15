package cn.tealc.asmr.model.collection;

import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.model.AsmrWork;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 附带完整作品信息的歌单收藏项，在 {@link ItemData} 的存储字段之上补充所属作品。
 * <p>
 * 歌单详情页要直接显示标题、声优并播放文件，而这些并未冗余在 {@code asmr_collect_item} 表中，
 * 因此 {@link cn.tealc.asmr.dao.collection.CollectionItemDao} 联表 {@code asmr_works} 查询后，
 * 用自定义的 {@code ResultSetHandler} 逐列手工填充本对象（基础列 + {@code w.} 前缀的作品列）。
 * <p>
 * {@link #getWork()} 是读取作品的唯一入口：基类虽然也声明了一个 {@code work} 字段，
 * 但没有访问器、也不参与列映射，两者的数据并不是同一份。
 *
 * @author Leck
 */
public class ItemDataEx extends ItemData {
    /** 收藏项所属的作品，来自联表查询的 {@code asmr_works} 行 */
    private AsmrWork work;


    public ItemDataEx() {
    }

    /**
     * 由作品及其目录下的文件构造一个收藏项，字段按入库约定填充。
     * <p>
     * 名称取文件名，路径取相对作品目录的相对路径，{@code workId} 取作品的数据库主键；
     * 所属歌单 {@code sheetId} 不在参数内，需由调用方另行设置。
     *
     * @param work 收藏项所属作品
     * @param file 作品目录下的音频文件
     */
    public ItemDataEx(AsmrWork work, AsmrFile file) {
        this.work = work;
        setName(file.getFilename());
        setFilepath(getRelativePath(work, file));
        setWorkId(work.getId());
    }

    /**
     * 以作品目录为基准求文件路径的相对形式，与收藏项入库时保存的 {@code filepath} 格式一致。
     */
    private String getRelativePath(AsmrWork work, AsmrFile file) {
        Path basePath = Paths.get(work.getFilepath());
        Path targetPath = Paths.get(file.getFilepath());
        Path relativePath = basePath.relativize(targetPath);
        return relativePath.toString();
    }


    public AsmrWork getWork() {
        return work;
    }

    public void setWork(AsmrWork work) {
        this.work = work;
    }
}