package cn.tealc.asmr.dao;

import cn.tealc.asmr.model.AsmrWork;

import java.sql.Connection;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 作品数据访问契约，对应 {@code asmr_works} 表。
 * <p>
 * 参考实现为 {@link AsmrWorkDao}，经 DI 绑定后由
 * {@link cn.tealc.asmr.service.WorkService}、{@link cn.tealc.asmr.service.PlayHistoryService}
 * 通过 {@code @Inject} 注入使用。实现类须保证以下语义：
 * <ul>
 *   <li>列表查询在无结果时返回空列表，不返回 {@code null}；单条查询未命中时返回 {@code null}</li>
 *   <li>计数方法返回非 {@code null} 的 {@link Integer}</li>
 *   <li>除显式接收 {@link Connection} 的方法外，每次调用自取连接、以自动提交模式执行并在返回前关闭，
 *       单次调用本身即一个事务，不保证多次调用之间的原子性</li>
 *   <li>显式接收 {@link Connection} 的方法必须在外部事务/连接中调用：实现不提交、不关闭传入连接，
 *       由调用方统一 commit/rollback，脱离外部事务单独使用时提交时机不可控</li>
 *   <li>写操作方法以"是否影响至少一行"返回布尔值；批量删除返回实际删除行数</li>
 *   <li>分类/标签/社团/声优/搜索条件按字符串 LIKE 模糊匹配，与其他名称互为子串的关键字可能多匹配</li>
 * </ul>
 * <p>
 * 查询参数的公共约定（按参考实现行为，各方法不再重复说明）：
 * <ul>
 *   <li>{@code index} 为页码，从 1 开始；{@code size} 为每页条数</li>
 *   <li>{@code type} 为排序方式：1=添加时间(id)、2=作品编号、3=发行时间、4=评分、5=随机；
 *       随机模式忽略 {@code index}，仅按 {@code size} 取一批</li>
 *   <li>{@code order} 为 true 时倒序</li>
 *   <li>{@code hasSubtext} 为 true 时只含有字幕作品，false/null 不过滤</li>
 *   <li>{@code nsfw} 为 false 时只含非 NSFW 作品，true/null 不过滤</li>
 *   <li>{@code noMate} 为 true 时只含无元数据来源（{@code sources} 为空）的作品</li>
 *   <li>{@code storageTypeSql} 是由调用方拼接好的 WHERE 条件片段（如 {@code " and storage_type=0"}），
 *       实现以字符串拼接并入 SQL、不走占位参数，调用方只能传入程序生成的片段、不得直接传用户输入；
 *       null/空白视为不限制。注意计数方法不接收 {@code noMate} 参数，{@code noMate=true} 时计数结果
 *       会大于对应分页查询的总条数</li>
 * </ul>
 *
 * @author Leck
 */
public interface WorkDao {
    /*=============AllWorks=============*/

    /**
     * 查询全部作品，不带任何过滤与排序，供全量扫描等场景使用（表可能很大）。
     */
    List<AsmrWork> getAllWorks();

    /**
     * 按公共过滤条件分页查询全部作品。
     *
     * @return 当前页作品，无结果时为空列表
     */
    List<AsmrWork> getAllWorks(int index, int size, int type, Boolean hasSubtext, Boolean nsfw, Boolean order, Boolean noMate, String storageTypeSql);

    /**
     * 统计全部作品数，过滤范围同 {@link #getAllWorks(int, int, int, Boolean, Boolean, Boolean, Boolean, String)} 但不含 noMate。
     */
    Integer getAllWorksCount(Boolean hasSubtext, Boolean nsfw, String storageTypeSql);    /*===============WorksByFavorites======================*/

    /**
     * 按收藏状态分页查询作品。
     *
     * @param star 期望的 favorites 值，true=收藏列表，false=未收藏列表
     */
    List<AsmrWork> getWorksByFavorites(boolean star, int index, int size, int type, Boolean hasSubtext, Boolean nsfw, Boolean order, Boolean noMate, String storageTypeSql);

    /**
     * 按收藏状态统计作品数（不含 noMate 过滤）。
     */
    Integer getWorksCountByFavorites(boolean star, Boolean hasSubtext, Boolean nsfw, String storageTypeSql);

    /**
     * 设置单个作品的收藏标记。
     */
    boolean updateFavorite(int id, boolean favorite);

    /*===============WorksByParentFolder======================*/

    /**
     * 按媒体库根路径精确匹配 {@code parent_path} 分页查询作品。
     *
     * @param path 媒体库路径，实现会归一化（反斜杠转正斜杠、去尾斜杠）后比较
     */
    List<AsmrWork> getWorksByParentFolder(String path, int index, int size, int type, Boolean hasSubtext, Boolean nsfw, Boolean order, Boolean noMate, String storageTypeSql);

    /**
     * 按快捷入口目录分页查询作品：含该目录本身的作品、直属文件及其全部子目录。
     *
     * @param path 目录路径，可含反斜杠，实现归一化后按前缀匹配
     */
    List<AsmrWork> getWorksByFolder(String path, int index, int size, int type, Boolean hasSubtext, Boolean nsfw, Boolean order, Boolean noMate, String storageTypeSql);

    /**
     * 查询指定 {@code parent_path} 下的全部作品，不分页。
     */
    List<AsmrWork> getAllWorksByParentFolder(String path);

    /**
     * 查询指定媒体库（{@code media_library_id}）下的全部作品，不分页。
     */
    List<AsmrWork> getAllWorksByMediaLibrary(int mediaLibraryId);

    /**
     * 按媒体库聚合统计作品数。
     *
     * @return 媒体库 ID → 作品数；只包含有作品的媒体库，无数据时为空 Map
     */
    Map<Integer, Integer> getWorkCountsByMediaLibrary();

    /**
     * 按文件路径与存储类型查询作品，多条同路径记录时返回其一。
     */
    AsmrWork getWorkByFilepath(String path, Integer storageType);

    /**
     * 按文件路径、存储类型与存储源查询作品，对应 {@code (filepath, storage_source_id)} 唯一约束。
     */
    AsmrWork getWorkByFilepath(String path, Integer storageType, Integer storageSourceId);

    /**
     * 按作品编号与存储类型查询作品。
     */
    AsmrWork getWorkByWorkId(String workId, Integer storageType);

    /**
     * 按作品编号、存储类型与存储源查询作品，对应 {@code (work_id, storage_source_id)} 唯一约束。
     */
    AsmrWork getWorkByWorkId(String workId, Integer storageType, Integer storageSourceId);

    /**
     * 删除指定媒体库根路径下的全部作品（自动提交，不参与外部事务）。
     *
     * @return 是否删除了至少一行
     */
    boolean deleteWorkByByParentFolder(String path);

    /**
     * 在外部连接上删除媒体库下全部作品，供事务场景使用。
     * <p>
     * 不提交、不关闭 {@code connection}，由调用方管理事务边界。
     */
    boolean deleteWorksByMediaLibrary(Connection connection, int mediaLibraryId) throws java.sql.SQLException;

    /**
     * 在外部连接上解除媒体库与作品的关联（{@code media_library_id} 置 null，不删除作品本身）。
     * <p>
     * 不提交、不关闭 {@code connection}，由调用方管理事务边界。
     */
    boolean clearMediaLibraryAssociation(Connection connection, int mediaLibraryId) throws java.sql.SQLException;

    /**
     * 按主键批量删除作品记录。
     *
     * @param ids 作品主键集合，null 或空集合时不执行 SQL 并直接返回 0
     * @return 实际删除的行数
     */
    int deleteWorksByIds(Collection<Integer> ids);

    /**
     * 按媒体库根路径统计作品数（{@code parent_path} 精确匹配，不含 noMate 过滤）。
     */
    Integer getWorksCountByParentFolder(String path, Boolean hasSubtext, Boolean nsfw, String storageTypeSql);

    /**
     * 按快捷入口目录统计作品数，匹配范围同 {@link #getWorksByFolder}（不含 noMate 过滤）。
     */
    Integer getWorksCountByFolder(String path, Boolean hasSubtext, Boolean nsfw, String storageTypeSql);

    /*===============WorksByCategory======================*/

    /**
     * 按分类名 LIKE 匹配 {@code categories} 冗余字段分页查询作品。
     */
    List<AsmrWork> getWorksByCategory(String key, int index, int size, int type, Boolean hasSubtext, Boolean nsfw, Boolean order, Boolean noMate, String storageTypeSql);

    /**
     * 按分类名统计作品数（不含 noMate 过滤）。
     */
    Integer getWorksCountByCategory(String key, Boolean hasSubtext, Boolean nsfw, String storageTypeSql);

    /*===============WorksByTag======================*/

    /**
     * 按标签名 LIKE 匹配 {@code tags} 冗余字段分页查询作品。
     */
    List<AsmrWork> getWorksByTag(String key, int index, int size, int type, Boolean hasSubtext, Boolean nsfw, Boolean order, Boolean noMate, String storageTypeSql);

    /**
     * 按标签名统计作品数（不含 noMate 过滤）。
     */
    Integer getWorksCountByTag(String key, Boolean hasSubtext, Boolean nsfw, String storageTypeSql);

    /*===============WorksByCircle======================*/

    /**
     * 按社团名 LIKE 匹配 {@code circle} 冗余字段分页查询作品。
     */
    List<AsmrWork> getWorksByCircle(String key, int index, int size, int type, Boolean hasSubtext, Boolean nsfw, Boolean order, Boolean noMate, String storageTypeSql);

    /**
     * 按社团名统计作品数（不含 noMate 过滤）。
     */
    Integer getWorksCountByCircle(String key, Boolean hasSubtext, Boolean nsfw, String storageTypeSql);

    /*===============WorksByVa======================*/

    /**
     * 按声优名 LIKE 匹配 {@code vas} 冗余字段分页查询作品。
     */
    List<AsmrWork> getWorksByVa(String key, int index, int size, int type, Boolean hasSubtext, Boolean nsfw, Boolean order, Boolean noMate, String storageTypeSql);

    /**
     * 按声优名统计作品数（不含 noMate 过滤）。
     */
    Integer getWorksCountByVa(String key, Boolean hasSubtext, Boolean nsfw, String storageTypeSql);

    /*===============WorksSearch======================*/

    /**
     * 关键字搜索：LIKE 匹配作品编号、标题、标签、分类、社团任一字段。
     */
    List<AsmrWork> searchWorks(String key, int index, int size, int type, Boolean hasSubtext, Boolean nsfw, Boolean order, Boolean noMate, String storageTypeSql);

    /**
     * 统计搜索结果数，匹配范围同 {@link #searchWorks}（不含 noMate 过滤）。
     */
    Integer searchWorksCount(String key, Boolean hasSubtext, Boolean nsfw, String storageTypeSql);

    /*===============WorksBySubtitle======================*/

    /**
     * 按是否有字幕精确分页查询作品，不参与公共过滤条件。
     */
    List<AsmrWork> getWorksBySubtitle(boolean subtitle, int index, int size);

    /**
     * 按是否有字幕统计作品数。
     */
    Integer getWorksBySubtitleCount(boolean subtitle);

    /*===============WorksByFilepath======================*/

    /**
     * 按文件路径查询作品，不考虑存储类型与存储源，同路径存在多条记录时返回其一。
     */
    AsmrWork getWorkByFilepath(String path);

    /*===============AddWork======================*/

    /**
     * 插入作品记录。
     * <p>
     * 违反 {@code (work_id, storage_source_id)} 或 {@code (filepath, storage_source_id)}
     * 唯一约束时静默跳过并返回 false，不抛异常。
     */
    boolean addWork(AsmrWork work);

    /*===============WorksBy id======================*/

    /**
     * 按主键查询作品，未命中返回 null。
     */
    AsmrWork getWorkById(int id);

    /**
     * 更新作品评分。
     *
     * @param rate 评分值，写入 {@code rate} 列
     */
    boolean updateWorkRateById(int id, double rate);

    /*===============WorksByTitle======================*/

    /**
     * 按原始标题精确匹配查询作品，未命中返回 null。
     */
    AsmrWork getWorkByTitle(String title);

    /**
     * 按主键部分更新作品：只写回 {@code work} 中非 null 的属性，null 属性保持库中原值。
     * <p>
     * 所有可更新属性均为 null 时不执行 SQL，直接返回 false。
     */
    boolean updateWorkById(AsmrWork work);

    /**
     * 只更新作品中文标题。
     */
    boolean updateWorkTitleCN(int id, String titleCN);

    /**
     * 用整串文本覆盖作品的 {@code tags} 冗余字段。
     */
    boolean updateWorkTag(int id, String tag);

    /*===============WorksFilePathUpdate======================*/

    /**
     * 只更新 {@code filepath}；{@code work.getFilepath()} 为 null 时不执行 SQL 并返回 false。
     */
    boolean updateWorkFilePathById(AsmrWork work);

    /**
     * 同时更新 {@code filepath} 与 {@code parent_path}；filepath 为 null 时返回 false，
     * parent_path 为 null 时照常写入（置空字段）。
     */
    boolean updateWorkFilePathAndParentById(AsmrWork work);

    /**
     * 更新作品的媒体库归属。
     *
     * @param mediaLibraryId 目标媒体库 ID，传 null 表示解除关联
     */
    boolean updateWorkMediaLibrary(int workId, Integer mediaLibraryId);

    /**
     * 一次性写回作品的位置与归属字段（filepath、parent_path、media_library_id、storage_source_id）。
     * <p>
     * filepath 为 null 时不执行 SQL 并返回 false；其余字段为 null 时照常写入。
     */
    boolean updateWorkLocationAndLibrary(AsmrWork work);

}
