package cn.tealc.asmr.service;

import cn.tealc.asmr.dao.album.WorkAlbumDao;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.eventbus.EventBus;
import cn.tealc.asmr.model.album.AlbumWorkPage;
import cn.tealc.asmr.model.album.WorkAlbum;
import cn.tealc.asmr.model.album.WorkAlbumSummary;
import cn.tealc.asmr.config.NotificationKey;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** 作品级集册，不操作作品文件或媒体文件歌单。 */
public class WorkAlbumService {
    private final WorkAlbumDao dao;

    public WorkAlbumService() {
        dao = DI.get(WorkAlbumDao.class);
    }

    public List<WorkAlbumSummary> summaries() {
        return dao.summaries();
    }

    /**
     * 最近有变动的作品集，用于右键菜单快捷入口。
     *
     * @param limit 最多返回多少条
     */
    public List<WorkAlbumSummary> recentSummaries(int limit) {
        return dao.recentSummaries(limit);
    }

    public WorkAlbum create(String name, String description) {
        WorkAlbum album = dao.create(name(name), description == null ? "" : description.trim(), LocalDateTime.now());
        changed();
        return album;
    }

    public boolean update(int id, String name, String description) {
        boolean updated = dao.update(id, name(name), description == null ? "" : description.trim());
        if (updated) changed();
        return updated;
    }

    public boolean delete(int id) {
        boolean deleted = dao.delete(id);
        if (deleted) changed();
        return deleted;
    }

    public int addWorks(Collection<Integer> albumIds, Collection<Integer> workIds) {
        if (albumIds == null || workIds == null) throw new IllegalArgumentException("请选择作品集和作品");
        if (albumIds.stream().anyMatch(id -> id == null || id <= 0)
                || workIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("作品集与作品必须是有效的数据库记录");
        }
        if (albumIds.isEmpty() || workIds.isEmpty()) return 0;
        int added = dao.addWorks(albumIds, workIds, LocalDateTime.now());
        if (added > 0) changed();
        return added;
    }

    public boolean removeWork(int albumId, int workId) {
        boolean removed = dao.removeWork(albumId, workId);
        if (removed) changed();
        return removed;
    }

    public Set<Integer> albumsForWork(int workId) {
        return dao.albumsForWork(workId);
    }

    public AlbumWorkPage works(int albumId, String search, int page, int pageSize) {
        validatePage(page, pageSize);
        return dao.works(albumId, search, page, pageSize);
    }

    public AlbumWorkPage works(int albumId, String search, int page, int pageSize,
                               int sortType, boolean descending, boolean subtitle, boolean nsfw,
                               boolean noMate, boolean showLocal, boolean showNetwork) {
        validatePage(page, pageSize);
        return dao.works(albumId, search, page, pageSize, sortType, descending, subtitle, nsfw,
                noMate, showLocal, showNetwork);
    }

    public AlbumWorkPage candidates(int albumId, Integer libraryId, String search, int page, int pageSize) {
        validatePage(page, pageSize);
        return dao.candidates(albumId, libraryId, search, page, pageSize);
    }

    private static String name(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("作品集名称不能为空");
        return value.trim();
    }

    private static void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 200) throw new IllegalArgumentException("无效的分页参数");
    }

    private static void changed() {
        EventBus.publish(NotificationKey.WORK_ALBUM_CHANGED);
    }
}
