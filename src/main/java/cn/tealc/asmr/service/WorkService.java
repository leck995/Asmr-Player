package cn.tealc.asmr.service;

import cn.tealc.asmr.model.AsmrKeyType;
import cn.tealc.asmr.dao.AsmrWorkDao;
import cn.tealc.asmr.dao.WorkDao;
import cn.tealc.asmr.di.Inject;
import cn.tealc.asmr.eventbus.EventBus;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.config.Config;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 作品服务，负责单作品 CRUD 与分页查询。
 * <p>
 * 作品保存在 {@code asmr_works}，通过 {@code (work_id, storage_source_id)} 复合唯一约束区分来源。
 * 所有作品操作统一通过 {@code workDao} 执行。
 *
 * @author Leck
 */
public class WorkService {

    @Inject
    private WorkDao workDao;

    /**
     * 切换作品收藏状态并回写到作品对象。
     */
    public void toggleFavorite(AsmrWork work) {
        setFavorite(work, !work.getFavorites());
    }

    /**
     * 设置作品收藏状态为目标值并回写到作品对象。
     */
    public void setFavorite(AsmrWork work, boolean favorite) {
        workDao.updateFavorite(work.getId(), favorite);
        work.setFavorites(favorite);
    }

    /**
     * 更新作品评分。
     */
    public void updateRate(AsmrWork work, double rate) {
        workDao.updateWorkRateById(work.getId(), rate);
    }

    /**
     * 按作品主键删除记录，不影响其他存储源中的同路径作品。
     */
    public void deleteWork(AsmrWork work) {
        if (work == null || work.getId() <= 0) throw new IllegalArgumentException("作品记录无效");
        deleteByIds(List.of(work.getId()));
    }

    /** 批量删除作品数据库记录。 */
    public int deleteByIds(Collection<Integer> ids) {
        int deleted = workDao.deleteWorksByIds(ids);
        if (deleted > 0) EventBus.publish(NotificationKey.WORK_ALBUM_CHANGED);
        return deleted;
    }

    /**
     * 按 ID 获取作品。
     */
    public AsmrWork getById(int id) {
        return workDao.getWorkById(id);
    }

    /**
     * 按文件路径获取作品。
     */
    public AsmrWork getByFilepath(String filepath) {
        return workDao.getWorkByFilepath(filepath);
    }

    /**
     * 更新作品记录。
     */
    public boolean updateWork(AsmrWork work) {
        return workDao.updateWorkById(work);
    }

    /**
     * 仅更新作品中文标题，避免把界面中可能已过期的其他字段一并回写。
     */
    public boolean updateWorkTitleCN(AsmrWork work, String titleCN) {
        boolean updated = workDao.updateWorkTitleCN(work.getId(), titleCN);
        if (updated) {
            work.setTitleCN(titleCN);
        }
        return updated;
    }

    /**
     * 更新作品标签字段。
     */
    public boolean updateWorkTag(int id, String tag) {
        return workDao.updateWorkTag(id, tag);
    }

    /**
     * 分页查询作品，按 {@link AsmrKeyType} 分派到对应 DAO 方法。
     *
     * @param type          查询类型（ALL/CATEGORY/TAG/CIRCLE/VA/FOLDER/FAVORITE/SEARCH/ONLINE）
     * @param key           分类/标签/社团/声优/文件夹/搜索关键字，ALL 时忽略
     * @param pageIndex     当前页码，从 1 开始
     * @param sortType      排序：1=添加时间, 2=作品编号, 3=发行时间, 4=评分, 5=随机
     * @param hasSubtext    是否有字幕
     * @param nsfw          是否含 NSFW
     * @param order         true=倒序
     * @param noMate        无元数据来源
     * @param showLocal     显示本地作品（storage_type=0）
     * @param showNetwork   显示网络作品（storage_type!=0）
     * @return 当前页的作品列表，无结果时返回空列表
     */
    public List<AsmrWork> queryPage(AsmrKeyType type, String key, int pageIndex,
                                    int sortType, boolean hasSubtext, boolean nsfw,
                                    boolean order, boolean noMate,
                                    boolean showLocal, boolean showNetwork) {
        return queryPage(type, key, pageIndex, sortType, hasSubtext, nsfw, order, noMate,
                showLocal, showNetwork, null);
    }

    public List<AsmrWork> queryPage(AsmrKeyType type, String key, int pageIndex,
                                    int sortType, boolean hasSubtext, boolean nsfw,
                                    boolean order, boolean noMate,
                                    boolean showLocal, boolean showNetwork, Integer mediaLibraryId) {
        return queryPage(type, key, pageIndex, sortType, hasSubtext, nsfw, order, noMate,
                showLocal, showNetwork, mediaLibraryId, Config.setting.gridListCount.get());
    }

    /**
     * 按显式页大小分页查询作品，供通用作品列表的数据源使用。
     */
    public List<AsmrWork> queryPage(AsmrKeyType type, String key, int pageIndex,
                                    int sortType, boolean hasSubtext, boolean nsfw,
                                    boolean order, boolean noMate,
                                    boolean showLocal, boolean showNetwork, Integer mediaLibraryId, int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize 必须大于 0");
        }
        String storageTypeSql = storageTypeSql(showLocal, showNetwork);
        if (mediaLibraryId != null && (type == AsmrKeyType.FOLDER || type == AsmrKeyType.ONLINE)) {
            return workDao.getWorksByFolder(key, pageIndex, pageSize, sortType, hasSubtext, nsfw,
                    order, noMate, storageTypeSql + " and media_library_id = " + mediaLibraryId);
        }
        List<AsmrWork> result = null;

        switch (type) {
            case ALL -> result = workDao.getAllWorks(pageIndex, pageSize, sortType,
                    hasSubtext, nsfw, order, noMate, storageTypeSql);
            case CATEGORY -> result = workDao.getWorksByCategory(key, pageIndex, pageSize, sortType,
                    hasSubtext, nsfw, order, noMate, storageTypeSql);
            case TAG -> result = workDao.getWorksByTag(key, pageIndex, pageSize, sortType,
                    hasSubtext, nsfw, order, noMate, storageTypeSql);
            case CIRCLE -> result = workDao.getWorksByCircle(key, pageIndex, pageSize, sortType,
                    hasSubtext, nsfw, order, noMate, storageTypeSql);
            case VA -> result = workDao.getWorksByVa(key, pageIndex, pageSize, sortType,
                    hasSubtext, nsfw, order, noMate, storageTypeSql);
            case FOLDER -> result = workDao.getWorksByFolder(key, pageIndex, pageSize, sortType,
                    hasSubtext, nsfw, order, noMate, storageTypeSql);
            case FAVORITE -> result = workDao.getWorksByFavorites(true, pageIndex, pageSize, sortType,
                    hasSubtext, nsfw, order, noMate, storageTypeSql);
            case SEARCH -> result = workDao.searchWorks(key, pageIndex, pageSize, sortType,
                    hasSubtext, nsfw, order, noMate, storageTypeSql);
            case ONLINE -> result = workDao.getWorksByParentFolder(key, pageIndex, pageSize, sortType,
                    hasSubtext, nsfw, order, noMate, storageTypeSql);
        }

        return result != null ? result : List.of();
    }

    /**
     * 查询符合条件的作品总数。
     *
     * @param type        查询类型，须与 {@link #queryPage} 一致
     * @param key         分类/标签/社团/声优/文件夹/搜索关键字，ALL 时忽略
     * @param hasSubtext  是否有字幕
     * @param nsfw        是否含 NSFW
     * @param showLocal   显示本地作品（storage_type=0）
     * @param showNetwork 显示网络作品（storage_type!=0）
     * @return 符合条件的作品总数，查询失败时返回 0
     */
    public int queryCount(AsmrKeyType type, String key,
                          boolean hasSubtext, boolean nsfw,
                          boolean showLocal, boolean showNetwork) {
        return queryCount(type, key, hasSubtext, nsfw, showLocal, showNetwork, null);
    }

    public int queryCount(AsmrKeyType type, String key,
                          boolean hasSubtext, boolean nsfw,
                          boolean showLocal, boolean showNetwork, Integer mediaLibraryId) {
        String storageTypeSql = storageTypeSql(showLocal, showNetwork);
        if (mediaLibraryId != null && (type == AsmrKeyType.FOLDER || type == AsmrKeyType.ONLINE)) {
            return workDao.getWorksCountByFolder(key, hasSubtext, nsfw,
                    storageTypeSql + " and media_library_id = " + mediaLibraryId);
        }
        Integer count = switch (type) {
            case ALL -> workDao.getAllWorksCount(hasSubtext, nsfw, storageTypeSql);
            case CATEGORY -> workDao.getWorksCountByCategory(key, hasSubtext, nsfw, storageTypeSql);
            case TAG -> workDao.getWorksCountByTag(key, hasSubtext, nsfw, storageTypeSql);
            case CIRCLE -> workDao.getWorksCountByCircle(key, hasSubtext, nsfw, storageTypeSql);
            case VA -> workDao.getWorksCountByVa(key, hasSubtext, nsfw, storageTypeSql);
            case FOLDER -> workDao.getWorksCountByFolder(key, hasSubtext, nsfw, storageTypeSql);
            case ONLINE -> workDao.getWorksCountByParentFolder(key, hasSubtext, nsfw, storageTypeSql);
            case FAVORITE -> workDao.getWorksCountByFavorites(true, hasSubtext, nsfw, storageTypeSql);
            case SEARCH -> workDao.searchWorksCount(key, hasSubtext, nsfw, storageTypeSql);
        };
        return count != null ? count : 0;
    }

    /**
     * 根据 showLocal/showNetwork 生成 storage_type 的 SQL 条件片段。
     *
     * @param showLocal   显示本地作品（storage_type=0）
     * @param showNetwork 显示网络作品（storage_type!=0）
     * @return SQL 片段，如 {@code " and storage_type=0"}；两者都显示时返回空串；都不选时返回 {@code " and 1=0"}
     */
    private static String storageTypeSql(boolean showLocal, boolean showNetwork) {
        if (showLocal && showNetwork) return "";
        if (showLocal) return " and storage_type=0";
        if (showNetwork) return " and storage_type!=0";
        return " and 1=0";
    }

    /**
     * 获取指定父文件夹下的全部作品。
     */
    public List<AsmrWork> getAllByParentFolder(String path) {
        return workDao.getAllWorksByParentFolder(path);
    }

    /** 查询指定媒体库下的全部作品。 */
    public List<AsmrWork> getAllByMediaLibrary(int mediaLibraryId) {
        return workDao.getAllWorksByMediaLibrary(mediaLibraryId);
    }

    /** 按媒体库聚合查询作品数量。 */
    public Map<Integer, Integer> getWorkCountsByMediaLibrary() {
        return workDao.getWorkCountsByMediaLibrary();
    }

    /** 按路径和存储类型查询作品。 */
    public AsmrWork getByFilepath(String filepath, Integer storageType) {
        return workDao.getWorkByFilepath(filepath, storageType);
    }

    public AsmrWork getByFilepath(String filepath, Integer storageType, Integer storageSourceId) {
        return workDao.getWorkByFilepath(filepath, storageType, storageSourceId);
    }

    /** 按作品编号和存储类型查询作品。 */
    public AsmrWork getByWorkId(String workId, Integer storageType) {
        return workDao.getWorkByWorkId(workId, storageType);
    }

    public AsmrWork getByWorkId(String workId, Integer storageType, Integer storageSourceId) {
        return workDao.getWorkByWorkId(workId, storageType, storageSourceId);
    }

    /**
     * 按父文件夹删除其下所有作品。
     */
    public void deleteByParentFolder(String path) {
        workDao.deleteWorkByByParentFolder(path);
    }

    /**
     * 按父文件夹删除其下所有作品（带外部 Connection，用于事务场景）。
     */
    public void deleteByParentFolder(Connection conn, String path) throws SQLException {
        if (workDao instanceof AsmrWorkDao dao) {
            dao.deleteByParentFolder(conn, path);
        } else {
            throw new UnsupportedOperationException("WorkDao 实现不支持带 Connection 的删除");
        }
    }

    /** 在事务中删除媒体库下的全部作品。 */
    public void deleteByMediaLibrary(Connection conn, int mediaLibraryId) throws SQLException {
        if (workDao instanceof AsmrWorkDao dao) {
            dao.deleteWorksByMediaLibrary(conn, mediaLibraryId);
        } else {
            throw new UnsupportedOperationException("WorkDao 实现不支持带 Connection 的媒体库删除");
        }
    }

    /** 在事务中解除作品与媒体库的关联。 */
    public void clearMediaLibraryAssociation(Connection conn, int mediaLibraryId) throws SQLException {
        if (workDao instanceof AsmrWorkDao dao) {
            dao.clearMediaLibraryAssociation(conn, mediaLibraryId);
        } else {
            throw new UnsupportedOperationException("WorkDao 实现不支持解除媒体库关联");
        }
    }

    /**
     * 新增作品记录。
     */
    public boolean addWork(AsmrWork work) {
        return workDao.addWork(work);
    }

    /**
     * 按标题获取作品。
     */
    public AsmrWork getWorkByTitle(String title) {
        return workDao.getWorkByTitle(title);
    }

    /**
     * 获取全部作品。
     */
    public List<AsmrWork> getAllWorks() {
        return workDao.getAllWorks();
    }

    /**
     * 更新作品文件路径（仅 filepath）。
     */
    public boolean updateWorkFilePath(AsmrWork work) {
        return workDao.updateWorkFilePathById(work);
    }

    /**
     * 更新作品文件路径与父目录（filepath + parentPath）。
     */
    public boolean updateWorkFilePathAndParent(AsmrWork work) {
        return workDao.updateWorkFilePathAndParentById(work);
    }

    /** 更新作品路径并归属媒体库。 */
    public boolean updateWorkLocationAndLibrary(AsmrWork work) {
        return workDao.updateWorkLocationAndLibrary(work);
    }

    /** 更新作品的媒体库归属。 */
    public boolean updateWorkMediaLibrary(int workId, Integer mediaLibraryId) {
        return workDao.updateWorkMediaLibrary(workId, mediaLibraryId);
    }


}
