package cn.tealc.asmr.dao;

import cn.tealc.asmr.model.AsmrWork;
import org.apache.commons.dbutils.BasicRowProcessor;
import org.apache.commons.dbutils.BeanProcessor;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.RowProcessor;
import org.apache.commons.dbutils.handlers.BeanHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/**
 * 作品数据表 DAO，操作 asmr_works 表。
 * <p>
 * 过滤值一律通过 ? 占位符绑定；唯一例外是 {@code storageTypeSql} 这类由 {@code WorkService}
 * 生成的 SQL 片段，它被直接拼进语句，因此只接受程序自己构造的常量片段，不能传入用户输入。
 * 异常统一包装为 {@link DataAccessException} 抛出，由上层处理。
 * 路径相关操作统一调用 {@link #normalizePath} 归一化。
 *
 * @author Leck
 */
public class AsmrWorkDao extends BaseDao implements WorkDao {
    private static final Logger LOG = LoggerFactory.getLogger(AsmrWorkDao.class);
    private final QueryRunner qr = new QueryRunner();

    @Override
    public RowProcessor getRowProcessor() {
        Map<String, String> map = new HashMap<>();
        map.put("id", "id");
        map.put("work_id", "workId");
        map.put("title", "title");
        map.put("title_cn", "titleCN");
        map.put("circle", "circle");
        map.put("release_date", "release");
        map.put("rate", "rate");
        map.put("subtitle", "subtitle");
        map.put("nsfw", "nsfw");
        map.put("vas", "vas");
        map.put("categories", "categories");
        map.put("tags", "tags");
        map.put("visits", "visits");
        map.put("favorites", "favorites");
        map.put("sources", "sources");
        map.put("filepath", "filepath");
        map.put("parent_path", "parentPath");
        map.put("thumbnail_cover_url", "thumbnailCoverUrl");
        map.put("main_cover_url", "mainCoverUrl");
        map.put("storage_type", "storageType");
        map.put("media_library_id", "mediaLibraryId");
        map.put("storage_source_id", "storageSourceId");
        map.put("has_info", "hasInfo");
        map.put("related_work_ids", "relatedWorkIds");
        map.put("origin_work_id", "originWorkId");
        BeanProcessor beanProcessor = new BeanProcessor(map);
        return new BasicRowProcessor(beanProcessor);
    }

    // ==================== 工具方法 ====================

    /**
     * 路径归一化：统一正斜杠、去除尾斜杠。
     */
    private String normalizePath(String path) {
        return path == null ? "" : path.replace('\\', '/').replaceAll("/+$", "");
    }

    /**
     * 构造 LIKE 参数：转义通配符后包裹 %。
     */
    private String likeParam(String keyword) {
        if (keyword == null) return "%";
        return "%" + keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") + "%";
    }

    /**
     * 构造目录前缀 LIKE 参数，避免相邻目录名称被误匹配。
     */
    private String pathPrefixLikeParam(String path) {
        return path
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") + "/%";
    }

    /**
     * 追加快捷入口目录筛选：匹配目录本身、直属作品及其子目录中的作品。
     */
    private void appendFolderCondition(StringBuilder sql, List<Object> params, String path) {
        String normalized = normalizePath(path);
        sql.append(" where (parent_path = ? or replace(filepath, char(92), '/') = ? or replace(filepath, char(92), '/') like ? escape '\\')");
        params.add(normalized);
        params.add(normalized);
        params.add(pathPrefixLikeParam(normalized));
    }

    /**
     * 判断 SQLException 是否为约束冲突（重复数据）。
     */
    private boolean isConstraintViolation(Throwable e) {
        if (!(e instanceof SQLException sqlEx)) return false;
        int code = sqlEx.getErrorCode();
        return code == 19 || code == 2067 || code == 1555;
    }

    /**
     * 动态拼接过滤条件到 SQL 骨架，参数收集到 params 列表。
     * storageTypeSql 是 WorkService 内部生成的 SQL 片段，无注入风险。
     */
    private void appendFilters(StringBuilder sql, List<Object> params,
                               Boolean hasSubtext, Boolean nsfw, Boolean noMate,
                               String storageTypeSql) {
        if (storageTypeSql != null && !storageTypeSql.isBlank()) {
            sql.append(" ").append(storageTypeSql);
        }
        if (nsfw != null && !nsfw) {
            sql.append(" and nsfw = ?");
            params.add(nsfw);
        }
        if (hasSubtext != null && hasSubtext) {
            sql.append(" and subtitle = ?");
            params.add(hasSubtext);
        }
        if (noMate != null && noMate) {
            sql.append(" and sources is null");
        }
    }

    /**
     * 动态拼接排序与分页到 SQL 骨架。
     * type: 1=添加时间, 2=作品编号, 3=发行时间, 4=评分, 5=随机
     */
    private void appendOrderAndPaging(StringBuilder sql, List<Object> params,
                                      int index, int size, int type, Boolean order) {
        switch (type) {
            case 1 -> sql.append(" order by id");
            case 2 -> sql.append(" order by work_id");
            case 3 -> sql.append(" order by release_date");
            case 4 -> sql.append(" order by rate");
            case 5 -> {
                sql.append(" and id in (select id from asmr_works order by RANDOM() limit ");
                sql.append(size).append(")");
                if (order != null && order) sql.append(" order by id");
            }
        }
        if (order != null && order) sql.append(" desc");
        if (type != 5) {
            sql.append(" limit ?,?");
            params.add((index - 1) * size);
            params.add(size);
        }
    }

    // ==================== AllWorks ====================

    @Override
    public List<AsmrWork> getAllWorks() {
        String sql = "select * from asmr_works";
        return execute("查询全部作品", conn ->
                qr.query(conn, sql, new BeanListHandler<>(AsmrWork.class, getRowProcessor())));
    }

    @Override
    public List<AsmrWork> getAllWorks(int index, int size, int type, Boolean hasSubtext,
                                       Boolean nsfw, Boolean order, Boolean noMate,
                                       String storageTypeSql) {
        StringBuilder sql = new StringBuilder("select * from asmr_works where 1=1");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, hasSubtext, nsfw, noMate, storageTypeSql);
        appendOrderAndPaging(sql, params, index, size, type, order);
        LOG.debug("{}", sql);
        return execute("分页查询全部作品",
                conn -> qr.query(conn, sql.toString(), new BeanListHandler<>(AsmrWork.class, getRowProcessor()), params.toArray()));
    }

    @Override
    public Integer getAllWorksCount(Boolean hasSubtext, Boolean nsfw, String storageTypeSql) {
        StringBuilder sql = new StringBuilder("select count(*) from asmr_works where 1=1");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, hasSubtext, nsfw, null, storageTypeSql);
        return execute("统计全部作品数量",
                conn -> qr.query(conn, sql.toString(), new ScalarHandler<Integer>(), params.toArray()));
    }

    // ==================== WorksByFavorites ====================

    @Override
    public List<AsmrWork> getWorksByFavorites(boolean star, int index, int size, int type,
                                               Boolean hasSubtext, Boolean nsfw, Boolean order,
                                               Boolean noMate, String storageTypeSql) {
        StringBuilder sql = new StringBuilder("select * from asmr_works where favorites = ?");
        List<Object> params = new ArrayList<>();
        params.add(star);
        appendFilters(sql, params, hasSubtext, nsfw, noMate, storageTypeSql);
        appendOrderAndPaging(sql, params, index, size, type, order);
        LOG.debug("{}", sql);
        return execute("分页查询收藏作品",
                conn -> qr.query(conn, sql.toString(), new BeanListHandler<>(AsmrWork.class, getRowProcessor()), params.toArray()));
    }

    @Override
    public Integer getWorksCountByFavorites(boolean star, Boolean hasSubtext, Boolean nsfw,
                                             String storageTypeSql) {
        StringBuilder sql = new StringBuilder("select count(*) from asmr_works where favorites = ?");
        List<Object> params = new ArrayList<>();
        params.add(star);
        appendFilters(sql, params, hasSubtext, nsfw, null, storageTypeSql);
        return execute("统计收藏作品数量",
                conn -> qr.query(conn, sql.toString(), new ScalarHandler<Integer>(), params.toArray()));
    }

    // ==================== WorksByParentFolder ====================

    @Override
    public List<AsmrWork> getWorksByParentFolder(String path, int index, int size, int type,
                                                  Boolean hasSubtext, Boolean nsfw, Boolean order,
                                                  Boolean noMate, String storageTypeSql) {
        String normalized = normalizePath(path);
        StringBuilder sql = new StringBuilder("select * from asmr_works where parent_path = ?");
        List<Object> params = new ArrayList<>();
        params.add(normalized);
        appendFilters(sql, params, hasSubtext, nsfw, noMate, storageTypeSql);
        appendOrderAndPaging(sql, params, index, size, type, order);
        LOG.debug("{}", sql);
        return execute("按媒体库路径分页查询作品",
                conn -> qr.query(conn, sql.toString(), new BeanListHandler<>(AsmrWork.class, getRowProcessor()), params.toArray()));
    }

    @Override
    public List<AsmrWork> getWorksByFolder(String path, int index, int size, int type,
                                            Boolean hasSubtext, Boolean nsfw, Boolean order,
                                            Boolean noMate, String storageTypeSql) {
        StringBuilder sql = new StringBuilder("select * from asmr_works");
        List<Object> params = new ArrayList<>();
        appendFolderCondition(sql, params, path);
        appendFilters(sql, params, hasSubtext, nsfw, noMate, storageTypeSql);
        appendOrderAndPaging(sql, params, index, size, type, order);
        LOG.debug("{}", sql);
        return execute("按快捷入口目录分页查询作品",
                conn -> qr.query(conn, sql.toString(), new BeanListHandler<>(AsmrWork.class, getRowProcessor()), params.toArray()));
    }

    @Override
    public List<AsmrWork> getAllWorksByParentFolder(String path) {
        String normalized = normalizePath(path);
        String sql = "select * from asmr_works where parent_path = ?";
        return execute("按媒体库路径查询全部作品",
                conn -> qr.query(conn, sql, new BeanListHandler<>(AsmrWork.class, getRowProcessor()), normalized));
    }

    @Override
    public List<AsmrWork> getAllWorksByMediaLibrary(int mediaLibraryId) {
        String sql = "select * from asmr_works where media_library_id = ?";
        return execute("按媒体库 ID 查询全部作品",
                conn -> qr.query(conn, sql, new BeanListHandler<>(AsmrWork.class, getRowProcessor()), mediaLibraryId));
    }

    @Override
    public Map<Integer, Integer> getWorkCountsByMediaLibrary() {
        String sql = "select media_library_id, count(*) as work_count from asmr_works "
                + "where media_library_id is not null group by media_library_id";
        return execute("按媒体库统计作品数量", conn -> qr.query(conn, sql, resultSet -> {
            Map<Integer, Integer> counts = new HashMap<>();
            while (resultSet.next()) {
                counts.put(resultSet.getInt("media_library_id"), resultSet.getInt("work_count"));
            }
            return counts;
        }));
    }

    @Override
    public AsmrWork getWorkByFilepath(String path, Integer storageType) {
        String sql = "select * from asmr_works where filepath = ? and storage_type = ?";
        return execute("按路径和存储类型查询作品",
                conn -> qr.query(conn, sql, new BeanHandler<>(AsmrWork.class, getRowProcessor()), path, storageType));
    }

    @Override
    public AsmrWork getWorkByFilepath(String path, Integer storageType, Integer storageSourceId) {
        String sql = "select * from asmr_works where filepath = ? and storage_type = ? and storage_source_id = ?";
        return execute("按路径和存储源查询作品",
                conn -> qr.query(conn, sql, new BeanHandler<>(AsmrWork.class, getRowProcessor()), path, storageType, storageSourceId));
    }

    @Override
    public AsmrWork getWorkByWorkId(String workId, Integer storageType) {
        String sql = "select * from asmr_works where work_id = ? and storage_type = ?";
        return execute("按作品编号和存储类型查询作品",
                conn -> qr.query(conn, sql, new BeanHandler<>(AsmrWork.class, getRowProcessor()), workId, storageType));
    }

    @Override
    public AsmrWork getWorkByWorkId(String workId, Integer storageType, Integer storageSourceId) {
        String sql = "select * from asmr_works where work_id = ? and storage_type = ? and storage_source_id = ?";
        return execute("按作品编号和存储源查询作品",
                conn -> qr.query(conn, sql, new BeanHandler<>(AsmrWork.class, getRowProcessor()), workId, storageType, storageSourceId));
    }

    @Override
    public boolean deleteWorkByByParentFolder(String path) {
        String normalized = normalizePath(path);
        String sql = "delete from asmr_works where parent_path = ?";
        return execute("按媒体库路径删除作品",
                conn -> qr.update(conn, sql, normalized) > 0);
    }

    /**
     * 带外部 Connection 的重载，用于事务场景。
     */
    public boolean deleteByParentFolder(Connection conn, String path) throws SQLException {
        String normalized = normalizePath(path);
        String sql = "delete from asmr_works where parent_path = ?";
        return qr.update(conn, sql, normalized) > 0;
    }

    @Override
    public boolean deleteWorksByMediaLibrary(Connection connection, int mediaLibraryId) throws SQLException {
        return qr.update(connection, "delete from asmr_works where media_library_id = ?", mediaLibraryId) > 0;
    }

    @Override
    public boolean clearMediaLibraryAssociation(Connection connection, int mediaLibraryId) throws SQLException {
        String sql = "update asmr_works set media_library_id = null where media_library_id = ?";
        return qr.update(connection, sql, mediaLibraryId) > 0;
    }

    @Override
    public int deleteWorksByIds(Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = "delete from asmr_works where id in (" + placeholders + ")";
        return execute("批量删除作品记录", conn -> qr.update(conn, sql, ids.toArray()));
    }

    @Override
    public Integer getWorksCountByParentFolder(String path, Boolean hasSubtext, Boolean nsfw,
                                                String storageTypeSql) {
        String normalized = normalizePath(path);
        StringBuilder sql = new StringBuilder("select count(*) from asmr_works where parent_path = ?");
        List<Object> params = new ArrayList<>();
        params.add(normalized);
        appendFilters(sql, params, hasSubtext, nsfw, null, storageTypeSql);
        return execute("按媒体库路径统计作品数量",
                conn -> qr.query(conn, sql.toString(), new ScalarHandler<Integer>(), params.toArray()));
    }

    @Override
    public Integer getWorksCountByFolder(String path, Boolean hasSubtext, Boolean nsfw,
                                         String storageTypeSql) {
        StringBuilder sql = new StringBuilder("select count(*) from asmr_works");
        List<Object> params = new ArrayList<>();
        appendFolderCondition(sql, params, path);
        appendFilters(sql, params, hasSubtext, nsfw, null, storageTypeSql);
        return execute("按快捷入口目录统计作品数量",
                conn -> qr.query(conn, sql.toString(), new ScalarHandler<Integer>(), params.toArray()));
    }

    // ==================== WorksByCategory ====================

    @Override
    public List<AsmrWork> getWorksByCategory(String key, int index, int size, int type,
                                              Boolean hasSubtext, Boolean nsfw, Boolean order,
                                              Boolean noMate, String storageTypeSql) {
        StringBuilder sql = new StringBuilder("select * from asmr_works where categories like ? escape '\\'");
        List<Object> params = new ArrayList<>();
        params.add(likeParam(key));
        appendFilters(sql, params, hasSubtext, nsfw, noMate, storageTypeSql);
        appendOrderAndPaging(sql, params, index, size, type, order);
        LOG.debug("{}", sql);
        return execute("按分类分页查询作品",
                conn -> qr.query(conn, sql.toString(), new BeanListHandler<>(AsmrWork.class, getRowProcessor()), params.toArray()));
    }

    @Override
    public Integer getWorksCountByCategory(String key, Boolean hasSubtext, Boolean nsfw,
                                            String storageTypeSql) {
        StringBuilder sql = new StringBuilder("select count(*) from asmr_works where categories like ? escape '\\'");
        List<Object> params = new ArrayList<>();
        params.add(likeParam(key));
        appendFilters(sql, params, hasSubtext, nsfw, null, storageTypeSql);
        return execute("按分类统计作品数量",
                conn -> qr.query(conn, sql.toString(), new ScalarHandler<Integer>(), params.toArray()));
    }

    // ==================== WorksByTag ====================

    @Override
    public List<AsmrWork> getWorksByTag(String key, int index, int size, int type,
                                         Boolean hasSubtext, Boolean nsfw, Boolean order,
                                         Boolean noMate, String storageTypeSql) {
        StringBuilder sql = new StringBuilder("select * from asmr_works where tags like ? escape '\\'");
        List<Object> params = new ArrayList<>();
        params.add(likeParam(key));
        appendFilters(sql, params, hasSubtext, nsfw, noMate, storageTypeSql);
        appendOrderAndPaging(sql, params, index, size, type, order);
        LOG.debug("{}", sql);
        return execute("按标签分页查询作品",
                conn -> qr.query(conn, sql.toString(), new BeanListHandler<>(AsmrWork.class, getRowProcessor()), params.toArray()));
    }

    @Override
    public Integer getWorksCountByTag(String key, Boolean hasSubtext, Boolean nsfw,
                                       String storageTypeSql) {
        StringBuilder sql = new StringBuilder("select count(*) from asmr_works where tags like ? escape '\\'");
        List<Object> params = new ArrayList<>();
        params.add(likeParam(key));
        appendFilters(sql, params, hasSubtext, nsfw, null, storageTypeSql);
        return execute("按标签统计作品数量",
                conn -> qr.query(conn, sql.toString(), new ScalarHandler<Integer>(), params.toArray()));
    }

    // ==================== WorksByCircle ====================

    @Override
    public List<AsmrWork> getWorksByCircle(String key, int index, int size, int type,
                                            Boolean hasSubtext, Boolean nsfw, Boolean order,
                                            Boolean noMate, String storageTypeSql) {
        StringBuilder sql = new StringBuilder("select * from asmr_works where circle like ? escape '\\'");
        List<Object> params = new ArrayList<>();
        params.add(likeParam(key));
        appendFilters(sql, params, hasSubtext, nsfw, noMate, storageTypeSql);
        appendOrderAndPaging(sql, params, index, size, type, order);
        LOG.debug("{}", sql);
        return execute("按社团分页查询作品",
                conn -> qr.query(conn, sql.toString(), new BeanListHandler<>(AsmrWork.class, getRowProcessor()), params.toArray()));
    }

    @Override
    public Integer getWorksCountByCircle(String key, Boolean hasSubtext, Boolean nsfw,
                                          String storageTypeSql) {
        StringBuilder sql = new StringBuilder("select count(*) from asmr_works where circle like ? escape '\\'");
        List<Object> params = new ArrayList<>();
        params.add(likeParam(key));
        appendFilters(sql, params, hasSubtext, nsfw, null, storageTypeSql);
        return execute("按社团统计作品数量",
                conn -> qr.query(conn, sql.toString(), new ScalarHandler<Integer>(), params.toArray()));
    }

    // ==================== WorksByVa ====================

    @Override
    public List<AsmrWork> getWorksByVa(String key, int index, int size, int type,
                                        Boolean hasSubtext, Boolean nsfw, Boolean order,
                                        Boolean noMate, String storageTypeSql) {
        StringBuilder sql = new StringBuilder("select * from asmr_works where vas like ? escape '\\'");
        List<Object> params = new ArrayList<>();
        params.add(likeParam(key));
        appendFilters(sql, params, hasSubtext, nsfw, noMate, storageTypeSql);
        appendOrderAndPaging(sql, params, index, size, type, order);
        LOG.debug("{}", sql);
        return execute("按声优分页查询作品",
                conn -> qr.query(conn, sql.toString(), new BeanListHandler<>(AsmrWork.class, getRowProcessor()), params.toArray()));
    }

    @Override
    public Integer getWorksCountByVa(String key, Boolean hasSubtext, Boolean nsfw,
                                      String storageTypeSql) {
        StringBuilder sql = new StringBuilder("select count(*) from asmr_works where vas like ? escape '\\'");
        List<Object> params = new ArrayList<>();
        params.add(likeParam(key));
        appendFilters(sql, params, hasSubtext, nsfw, null, storageTypeSql);
        return execute("按声优统计作品数量",
                conn -> qr.query(conn, sql.toString(), new ScalarHandler<Integer>(), params.toArray()));
    }

    // ==================== WorksSearch ====================

    @Override
    public List<AsmrWork> searchWorks(String key, int index, int size, int type,
                                       Boolean hasSubtext, Boolean nsfw, Boolean order,
                                       Boolean noMate, String storageTypeSql) {
        String like = likeParam(key);
        StringBuilder sql = new StringBuilder("select * from asmr_works where (1=1 and work_id like ? escape '\\' or title like ? escape '\\' or tags like ? escape '\\' or categories like ? escape '\\' or circle like ? escape '\\')");
        List<Object> params = new ArrayList<>();
        params.add(like);
        params.add(like);
        params.add(like);
        params.add(like);
        params.add(like);
        appendFilters(sql, params, hasSubtext, nsfw, noMate, storageTypeSql);
        appendOrderAndPaging(sql, params, index, size, type, order);
        LOG.debug("{}", sql);
        return execute("搜索作品",
                conn -> qr.query(conn, sql.toString(), new BeanListHandler<>(AsmrWork.class, getRowProcessor()), params.toArray()));
    }

    @Override
    public Integer searchWorksCount(String key, Boolean hasSubtext, Boolean nsfw,
                                     String storageTypeSql) {
        String like = likeParam(key);
        StringBuilder sql = new StringBuilder("select count(*) from asmr_works where (1=1 and work_id like ? escape '\\' or title like ? escape '\\' or tags like ? escape '\\' or categories like ? escape '\\' or circle like ? escape '\\')");
        List<Object> params = new ArrayList<>();
        params.add(like);
        params.add(like);
        params.add(like);
        params.add(like);
        params.add(like);
        appendFilters(sql, params, hasSubtext, nsfw, null, storageTypeSql);
        return execute("统计搜索结果数量",
                conn -> qr.query(conn, sql.toString(), new ScalarHandler<Integer>(), params.toArray()));
    }

    // ==================== WorksBySubtitle ====================

    @Override
    public List<AsmrWork> getWorksBySubtitle(boolean subtitle, int index, int size) {
        String sql = "select * from asmr_works where subtitle = ? limit ?,?";
        return execute("按字幕分页查询作品",
                conn -> qr.query(conn, sql, new BeanListHandler<>(AsmrWork.class, getRowProcessor()), subtitle, (index - 1) * size, size));
    }

    @Override
    public Integer getWorksBySubtitleCount(boolean subtitle) {
        String sql = "select count(*) from asmr_works where subtitle = ?";
        return execute("按字幕统计作品数量",
                conn -> qr.query(conn, sql, new ScalarHandler<Integer>(), subtitle));
    }

    // ==================== 单条查询 ====================

    @Override
    public AsmrWork getWorkByFilepath(String path) {
        String sql = "select * from asmr_works where filepath = ?";
        return execute("按路径查询作品",
                conn -> qr.query(conn, sql, new BeanHandler<>(AsmrWork.class, getRowProcessor()), path));
    }

    @Override
    public AsmrWork getWorkById(int id) {
        String sql = "select * from asmr_works where id = ?";
        return execute("按 ID 查询作品",
                conn -> qr.query(conn, sql, new BeanHandler<>(AsmrWork.class, getRowProcessor()), id));
    }

    @Override
    public boolean updateWorkRateById(int id, double rate) {
        String sql = "update asmr_works set rate = ? where id = ?";
        return execute("更新作品评分",
                conn -> qr.update(conn, sql, rate, id) > 0);
    }

    public AsmrWork getWorkByTitle(String title) {
        String sql = "select * from asmr_works where title = ?";
        return execute("按标题查询作品",
                conn -> qr.query(conn, sql, new BeanHandler<>(AsmrWork.class, getRowProcessor()), title));
    }

    // ==================== 写操作 ====================

    @Override
    public boolean updateFavorite(int id, boolean favorite) {
        String sql = "update asmr_works set favorites = ? where id = ?";
        return execute("更新收藏状态",
                conn -> qr.update(conn, sql, favorite, id) > 0);
    }

    public boolean addWork(AsmrWork work) {
        String sql = "insert or ignore into asmr_works " +
                "(work_id,title,title_cn,circle,release_date,rate,subtitle,nsfw,visits,favorites," +
                "sources,filepath,parent_path,vas,tags,categories,thumbnail_cover_url,main_cover_url," +
                "storage_type,media_library_id,storage_source_id,has_info,related_work_ids,origin_work_id) " +
                "values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        return executeWithConstraintCheck("添加作品: " + work.getWorkId(),
                conn -> qr.update(conn, sql, buildInsertParams(work)));
    }

    public boolean addAndReplaceWork(AsmrWork work) {
        String sql = "insert into asmr_works " +
                "(work_id,title,title_cn,circle,release_date,rate,subtitle,nsfw,visits,favorites," +
                "sources,filepath,parent_path,vas,tags,categories,thumbnail_cover_url,main_cover_url," +
                "storage_type,media_library_id,storage_source_id,has_info,related_work_ids,origin_work_id) " +
                "values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                "ON CONFLICT DO UPDATE SET work_id=excluded.work_id, title=excluded.title, title_cn=excluded.title_cn, " +
                "circle=excluded.circle, release_date=excluded.release_date, rate=excluded.rate, subtitle=excluded.subtitle, " +
                "nsfw=excluded.nsfw, visits=excluded.visits, favorites=excluded.favorites, sources=excluded.sources, " +
                "filepath=excluded.filepath, parent_path=excluded.parent_path, vas=excluded.vas, tags=excluded.tags, " +
                "categories=excluded.categories, thumbnail_cover_url=excluded.thumbnail_cover_url, main_cover_url=excluded.main_cover_url, " +
                "storage_type=excluded.storage_type, media_library_id=excluded.media_library_id, storage_source_id=excluded.storage_source_id, " +
                "has_info=excluded.has_info, related_work_ids=excluded.related_work_ids, origin_work_id=excluded.origin_work_id";
        return executeWithConstraintCheck("替换作品: " + work.getWorkId(),
                conn -> qr.update(conn, sql, buildInsertParams(work)));
    }

    /**
     * 构建 INSERT 语句的参数数组（22 个字段）。
     */
    private Object[] buildInsertParams(AsmrWork work) {
        return new Object[]{
                work.getWorkId(), work.getTitle(), work.getTitleCN(),
                work.getCircle(), work.getRelease(),
                work.getRate() == null ? 0 : work.getRate(),
                work.getSubtitle() != null && work.getSubtitle(),
                work.getNsfw() != null && work.getNsfw(),
                work.getVisits() == null ? 0 : work.getVisits(),
                work.getFavorites() != null && work.getFavorites(),
                work.getSources(),
                work.getFilepath(), work.getParentPath(), work.getVas(),
                work.getTags(), work.getCategories(), work.getThumbnailCoverUrl(),
                work.getMainCoverUrl(), work.getStorageType(), work.getMediaLibraryId(), work.getStorageSourceId(),
                work.getHasInfo() != null && work.getHasInfo(),
                work.getRelatedWorkIds(), work.getOriginWorkId()
        };
    }

    @Override
    public boolean updateWorkById(AsmrWork work) {
        StringBuilder sql = new StringBuilder("update asmr_works set ");
        List<Object> params = new ArrayList<>();
        if (work.getWorkId() != null)          { sql.append("work_id=?, ");              params.add(work.getWorkId()); }
        if (work.getTitle() != null)         { sql.append("title=?, ");              params.add(work.getTitle()); }
        if (work.getTitleCN() != null)       { sql.append("title_cn=?, ");           params.add(work.getTitleCN()); }
        if (work.getCircle() != null)        { sql.append("circle=?, ");             params.add(work.getCircle()); }
        if (work.getRate() != null)          { sql.append("rate=?, ");               params.add(work.getRate()); }
        if (work.getRelease() != null)       { sql.append("release_date=?, ");       params.add(work.getRelease()); }
        if (work.getSubtitle() != null)      { sql.append("subtitle=?, ");           params.add(work.getSubtitle()); }
        if (work.getNsfw() != null)          { sql.append("nsfw=?, ");               params.add(work.getNsfw()); }
        if (work.getVisits() != null)        { sql.append("visits=?, ");             params.add(work.getVisits()); }
        if (work.getFavorites() != null)     { sql.append("favorites=?, ");          params.add(work.getFavorites()); }
        if (work.getFilepath() != null)      { sql.append("filepath=?, ");           params.add(work.getFilepath()); }
        if (work.getParentPath() != null)    { sql.append("parent_path=?, ");        params.add(work.getParentPath()); }
        if (work.getSources() != null)       { sql.append("sources=?, ");            params.add(work.getSources()); }
        if (work.getVas() != null)           { sql.append("vas=?, ");                params.add(work.getVas()); }
        if (work.getTags() != null)          { sql.append("tags=?, ");               params.add(work.getTags()); }
        if (work.getCategories() != null)    { sql.append("categories=?, ");         params.add(work.getCategories()); }
        if (work.getThumbnailCoverUrl() != null) { sql.append("thumbnail_cover_url=?, "); params.add(work.getThumbnailCoverUrl()); }
        if (work.getMainCoverUrl() != null)  { sql.append("main_cover_url=?, ");     params.add(work.getMainCoverUrl()); }
        if (work.getStorageType() != null)   { sql.append("storage_type=?, ");       params.add(work.getStorageType()); }
        if (work.getMediaLibraryId() != null) { sql.append("media_library_id=?, ");   params.add(work.getMediaLibraryId()); }
        if (work.getStorageSourceId() != null) { sql.append("storage_source_id=?, "); params.add(work.getStorageSourceId()); }
        if (work.getHasInfo() != null)       { sql.append("has_info=?, ");           params.add(work.getHasInfo()); }
        if (work.getRelatedWorkIds() != null)     { sql.append("related_work_ids=?, ");         params.add(work.getRelatedWorkIds()); }
        if (work.getOriginWorkId() != null)      { sql.append("origin_work_id=?, ");          params.add(work.getOriginWorkId()); }
        if (params.isEmpty()) return false;
        sql.setLength(sql.length() - 2);  // 去掉末尾 ", "
        sql.append(" where id = ?");
        params.add(work.getId());
        LOG.debug("{}", sql);
        return execute("按 ID 更新作品: " + work.getId(),
                conn -> qr.update(conn, sql.toString(), params.toArray()) > 0);
    }

    @Override
    public boolean updateWorkTitleCN(int id, String titleCN) {
        String sql = "update asmr_works set title_cn = ? where id = ?";
        return execute("更新作品中文标题: id=" + id,
                conn -> qr.update(conn, sql, titleCN, id) > 0);
    }

    @Override
    public boolean updateWorkTag(int id, String tag) {
        String sql = "update asmr_works set tags = ? where id = ?";
        return execute("更新作品标签: id=" + id,
                conn -> qr.update(conn, sql, tag, id) > 0);
    }

    public boolean updateWorkFilePathById(AsmrWork work) {
        if (work.getFilepath() == null) return false;
        String sql = "update asmr_works set filepath = ? where id = ?";
        return execute("更新作品路径: id=" + work.getId(),
                conn -> qr.update(conn, sql, work.getFilepath(), work.getId()) > 0);
    }

    public boolean updateWorkFilePathAndParentById(AsmrWork work) {
        if (work.getFilepath() == null) return false;
        String sql = "update asmr_works set filepath = ?, parent_path = ? where id = ?";
        return execute("更新作品路径与父路径: id=" + work.getId(),
                conn -> qr.update(conn, sql, work.getFilepath(), work.getParentPath(), work.getId()) > 0);
    }

    @Override
    public boolean updateWorkMediaLibrary(int workId, Integer mediaLibraryId) {
        String sql = "update asmr_works set media_library_id = ? where id = ?";
        return execute("更新作品媒体库归属: id=" + workId,
                conn -> qr.update(conn, sql, mediaLibraryId, workId) > 0);
    }

    @Override
    public boolean updateWorkLocationAndLibrary(AsmrWork work) {
        if (work.getFilepath() == null) return false;
        String sql = "update asmr_works set filepath = ?, parent_path = ?, media_library_id = ?, storage_source_id = ? where id = ?";
        return execute("更新作品路径和媒体库归属: id=" + work.getId(),
                conn -> qr.update(conn, sql, work.getFilepath(), work.getParentPath(),
                        work.getMediaLibraryId(), work.getStorageSourceId(), work.getId()) > 0);
    }

    // ==================== 内部辅助 ====================

    /**
     * 执行写操作，约束冲突返回 false，其他异常抛 DataAccessException。
     */
    private boolean executeWithConstraintCheck(String operation, SqlAction<Integer> action) {
        try {
            return execute(operation, conn -> action.run(conn) > 0);
        } catch (DataAccessException e) {
            if (isConstraintViolation(e.getCause())) return false;
            throw e;
        }
    }
}
