package cn.tealc.asmr.service;

import cn.tealc.asmr.dao.DataAccessException;
import cn.tealc.asmr.dao.AnywhereDoorDao;
import cn.tealc.asmr.dao.JdbcUtils;
import cn.tealc.asmr.dao.MediaLibraryDao;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.eventbus.EventBus;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.model.MediaLibrary;
import cn.tealc.asmr.source.StorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 媒体库管理服务。
 * <p>
 * 无状态服务层，所有数据直接读写数据库，不在内存中持有列表。
 * UI/ViewModel 层按需调用，自行管理 ObservableList。
 *
 * @author Leck
 */
public class MediaLibraryService {
    private static final Logger LOG = LoggerFactory.getLogger(MediaLibraryService.class);

    private final MediaLibraryDao mediaLibraryDao;
    private final WorkService workService;

    public MediaLibraryService() {
        this.mediaLibraryDao = DI.get(MediaLibraryDao.class);
        this.workService = DI.get(WorkService.class);
    }

    /**
     * 查询全部媒体库。
     */
    public List<MediaLibrary> getAll() {
        return mediaLibraryDao.getAll();
    }

    /**
     * 查询本地媒体库（storageType=LOCAL）。
     */
    public List<MediaLibrary> getLocal() {
        return mediaLibraryDao.getByStorageType(StorageType.LOCAL);
    }

    /**
     * 查询 OpenList 媒体库（storageType=OPENLIST）。
     */
    public List<MediaLibrary> getOpenList() {
        return mediaLibraryDao.getByStorageType(StorageType.OPENLIST);
    }

    /**
     * 按存储类型查询媒体库。
     */
    public List<MediaLibrary> getByStorageType(StorageType type) {
        return mediaLibraryDao.getByStorageType(type);
    }

    /**
     * 按 ID 查询。
     */
    public MediaLibrary getById(int id) {
        return mediaLibraryDao.getById(id);
    }

    /**
     * 按路径查询，判断是否已存在同路径的媒体库。
     */
    public MediaLibrary getByPath(String path) {
        return mediaLibraryDao.getByPath(path);
    }

    /**
     * 新增媒体库。
     */
    public boolean add(MediaLibrary library) {
        return mediaLibraryDao.add(library);
    }

    /**
     * 更新媒体库。
     */
    public boolean update(MediaLibrary library) {
        return mediaLibraryDao.update(library);
    }

    /** 记录媒体库完成导入的时间。 */
    public boolean recordImportCompleted(int id, LocalDateTime time) {
        return mediaLibraryDao.updateLastImportedAt(id, time);
    }

    /** 记录媒体库完成刮削的时间。 */
    public boolean recordScrapeCompleted(int id, LocalDateTime time) {
        return mediaLibraryDao.updateLastScrapedAt(id, time);
    }

    /**
     * 按 ID 删除媒体库。
     */
    public boolean delete(int id) {
        return mediaLibraryDao.deleteById(id);
    }

    /**
     * 删除媒体库及其关联作品，使用手动事务保证原子性。
     * <p>
     * 删作品和删媒体库记录在同一个 Connection + 事务中执行，
     * 任一步骤失败则回滚，不留孤儿数据。
     *
     * @param library 要删除的媒体库
     * @return true=删除成功
     */
    public boolean deleteLibraryWithWorks(MediaLibrary library) {
        Connection conn = null;
        try {
            conn = JdbcUtils.getConnection();
            conn.setAutoCommit(false);
            // 两个 DAO 共享同一个 Connection
            workService.deleteByMediaLibrary(conn, library.getId());
            DI.get(AnywhereDoorDao.class).deleteByLibrary(conn, library.getId());
            mediaLibraryDao.deleteById(conn, library.getId());
            conn.commit();
            DI.get(AnywhereDoorService.class).refresh();
            EventBus.publish(NotificationKey.WORK_ALBUM_CHANGED);
            return true;
        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            throw new DataAccessException("删除媒体库及关联作品失败: " + library.getPath(), e);
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
                JdbcUtils.close(conn);
            }
        }
    }

    /**
     * 删除媒体库但保留作品记录，解除作品与媒体库的关联。
     */
    public boolean deleteLibraryKeepWorks(MediaLibrary library) {
        Connection conn = null;
        try {
            conn = JdbcUtils.getConnection();
            conn.setAutoCommit(false);
            workService.clearMediaLibraryAssociation(conn, library.getId());
            DI.get(AnywhereDoorDao.class).deleteByLibrary(conn, library.getId());
            mediaLibraryDao.deleteById(conn, library.getId());
            conn.commit();
            DI.get(AnywhereDoorService.class).refresh();
            return true;
        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            throw new DataAccessException("删除媒体库并保留作品失败: " + library.getPath(), e);
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
                JdbcUtils.close(conn);
            }
        }
    }

    /**
     * 按路径删除媒体库。
     */
    public boolean deleteByPath(String path) {
        MediaLibrary ml = mediaLibraryDao.getByPath(path);
        if (ml != null && ml.getId() != null) {
            return mediaLibraryDao.deleteById(ml.getId());
        }
        return false;
    }
}
