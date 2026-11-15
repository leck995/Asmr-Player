package cn.tealc.asmr.service;

import cn.tealc.asmr.BaseDatabaseTest;
import cn.tealc.asmr.dao.AsmrWorkDao;
import cn.tealc.asmr.dao.DataAccessException;
import cn.tealc.asmr.dao.JdbcUtils;
import cn.tealc.asmr.dao.WorkDao;
import cn.tealc.asmr.model.MediaLibrary;
import cn.tealc.asmr.source.StorageType;
import cn.tealc.asmr.dao.album.WorkAlbumDao;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.album.WorkAlbum;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkAlbumServiceTest extends BaseDatabaseTest {
    private WorkAlbumService service() { return DI.get(WorkAlbumService.class); }

    private int work(String code, String path, int source) throws Exception {
        try (Connection connection = JdbcUtils.getConnection();
             var statement = connection.prepareStatement(
                     "INSERT INTO asmr_works(work_id,title,filepath,storage_source_id) VALUES (?,?,?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, code);
            statement.setString(2, path);
            statement.setString(3, path);
            statement.setInt(4, source);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) { return keys.getInt(1); }
        }
    }

    @Test
    void createsEditsAndValidatesAlbumWithoutChangingCreationTime() {
        var service = service();
        WorkAlbum album = service.create(" 集册 ", " 描述\n第二行 ");
        assertEquals("集册", album.name());
        assertEquals("描述\n第二行", album.description());
        assertNull(album.lastAddedAt());
        assertTrue(service.update(album.id(), "新名称", "新描述"));
        var summary = service.summaries().getFirst();
        assertEquals("新名称", summary.album().name());
        assertEquals(album.createdAt(), summary.album().createdAt());
        assertEquals(0, summary.workCount());
        assertNull(summary.coverWork());
        assertThrows(IllegalArgumentException.class, () -> service.create(" ", ""));
        assertThrows(IllegalArgumentException.class, () -> service.update(album.id(), null, ""));
        assertNotEquals(album.id(), service.create("新名称", null).id());
    }

    @Test
    void supportsManyToManyAndDeduplicatesBatch() throws Exception {
        var service = service();
        int first = work("RJ123", "/same", 1);
        int second = work("RJ123", "/same", 2);
        int noCode = work(null, "/no-code", 1);
        int a = service.create("A", "").id();
        int b = service.create("B", "").id();
        assertEquals(6, service.addWorks(List.of(a, b, a), List.of(first, second, noCode, first)));
        assertEquals(0, service.addWorks(List.of(a, b), List.of(first, noCode)));
        assertEquals(2, service.albumsForWork(first).size());
        assertTrue(service.summaries().stream().allMatch(summary -> summary.workCount() == 3));
        assertEquals(3, service.works(a, "", 1, 20).total());
    }

    @Test
    void removesOnlyMembershipAndPreservesHistoricalTime() throws Exception {
        var service = service();
        int id = work(null, "/work", 1);
        int a = service.create("A", "保留描述").id();
        int b = service.create("B", "").id();
        service.addWorks(List.of(a, b), List.of(id));
        var time = service.summaries().stream().filter(s -> s.album().id() == a).findFirst().orElseThrow().album().lastAddedAt();
        assertTrue(service.removeWork(a, id));
        assertFalse(service.removeWork(a, id));
        var summary = service.summaries().stream().filter(s -> s.album().id() == a).findFirst().orElseThrow();
        assertEquals(0, summary.workCount());
        assertEquals("保留描述", summary.album().description());
        assertEquals(time, summary.album().lastAddedAt());
        assertEquals(1, service.albumsForWork(id).size());
        assertNotNull(new AsmrWorkDao().getWorkById(id));
    }

    @Test
    void repeatedAddDoesNotUpdateTimeAndCoverFallsBackOnDeletion() throws Exception {
        var service = service();
        int first = work("BJ1", "/first", 1);
        int second = work("VJ2", "/second", 1);
        int album = service.create("A", "").id();
        WorkAlbumDao dao = new WorkAlbumDao();
        LocalDateTime firstTime = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime lastTime = firstTime.plusDays(1);
        dao.addWorks(List.of(album), List.of(first), firstTime);
        dao.addWorks(List.of(album), List.of(second), lastTime);
        assertEquals(0, dao.addWorks(List.of(album), List.of(first), lastTime.plusDays(1)));
        assertEquals(second, service.summaries().getFirst().coverWork().getId());
        assertEquals(lastTime, service.summaries().getFirst().album().lastAddedAt());
        new AsmrWorkDao().deleteWorksByIds(List.of(second));
        assertEquals(first, service.summaries().getFirst().coverWork().getId());
        assertEquals(lastTime, service.summaries().getFirst().album().lastAddedAt());
    }

    @Test
    void deletingWorksCascadesButKeepsEmptyAlbumsAndReimportDoesNotRestore() throws Exception {
        var service = service();
        int id = work("RJ1", "/one", 1);
        int a = service.create("A", "描述").id();
        int b = service.create("B", "").id();
        service.addWorks(List.of(a, b), List.of(id));
        new AsmrWorkDao().deleteWorksByIds(List.of(id));
        assertEquals(2, service.summaries().size());
        assertTrue(service.summaries().stream().allMatch(s -> s.workCount() == 0));
        int reimport = work("RJ1", "/one", 1);
        assertNotEquals(id, reimport);
        assertTrue(service.albumsForWork(reimport).isEmpty());
    }

    @Test
    void deletingAlbumDoesNotDeleteWorksOrOtherMemberships() throws Exception {
        var service = service();
        int id = work(null, "/work", 1);
        int a = service.create("A", "").id();
        int b = service.create("B", "").id();
        service.addWorks(List.of(a, b), List.of(id));
        assertTrue(service.delete(a));
        assertFalse(service.delete(a));
        assertNotNull(new AsmrWorkDao().getWorkById(id));
        assertEquals(java.util.Set.of(b), service.albumsForWork(id));
    }

    @Test
    void failedBatchRollsBackMembershipsAndTimestamps() throws Exception {
        var service = service();
        int id = work(null, "/work", 1);
        int album = service.create("A", "").id();
        assertThrows(DataAccessException.class, () -> service.addWorks(List.of(album), List.of(id, Integer.MAX_VALUE)));
        assertEquals(0, service.summaries().getFirst().workCount());
        assertNull(service.summaries().getFirst().album().lastAddedAt());
        assertThrows(DataAccessException.class, () -> service.addWorks(List.of(album, Integer.MAX_VALUE), List.of(id)));
        assertEquals(0, service.summaries().getFirst().workCount());
    }

    @Test
    void sortsAndFiltersAlbumWorks() throws Exception {
        var service = service();
        int local = work("RJ1", "/local", 1);
        int network = work("RJ2", "/network", 1);
        try (Connection connection = JdbcUtils.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE asmr_works SET rate=1, subtitle=0, nsfw=1, storage_type=0 WHERE id=" + local);
            statement.executeUpdate("UPDATE asmr_works SET rate=5, subtitle=1, nsfw=0, storage_type=1 WHERE id=" + network);
        }
        int album = service.create("A", "").id();
        service.addWorks(List.of(album), List.of(local, network));

        var byRate = service.works(album, "", 1, 10, 4, true, false, true, false, true, true);
        assertEquals(network, byRate.works().getFirst().getId());

        var allAges = service.works(album, "", 1, 10, 1, true, false, false, false, true, true);
        assertEquals(1, allAges.total());
        assertEquals(network, allAges.works().getFirst().getId());

        var subtitled = service.works(album, "", 1, 10, 1, true, true, true, false, true, true);
        assertEquals(1, subtitled.total());
        assertEquals(network, subtitled.works().getFirst().getId());

        var localOnly = service.works(album, "", 1, 10, 1, true, false, true, false, true, false);
        assertEquals(1, localOnly.total());
        assertEquals(local, localOnly.works().getFirst().getId());

        var networkOnly = service.works(album, "", 1, 10, 1, true, false, true, false, false, true);
        assertEquals(1, networkOnly.total());
        assertEquals(network, networkOnly.works().getFirst().getId());
    }

    @Test
    void paginatesSearchesAndExcludesExistingMembers() throws Exception {
        var service = service();
        int album = service.create("A", "").id();
        int first = work("RJ1", "/100%_a", 1);
        int second = work("BJ2", "/other", 1);
        int third = work("VJ3", "/10000", 2);
        assertEquals(3, service.candidates(album, null, "", 1, 2).total());
        assertEquals(2, service.candidates(album, null, "", 1, 2).works().size());
        assertEquals(1, service.candidates(album, null, "", 2, 2).works().size());
        assertEquals(first, service.candidates(album, null, "%_", 1, 20).works().getFirst().getId());
        service.addWorks(List.of(album), List.of(first, second));
        assertEquals(1, service.candidates(album, null, "", 1, 20).total());
        assertEquals(third, service.candidates(album, null, "", 1, 20).works().getFirst().getId());
        assertEquals(first, service.works(album, "RJ1", 1, 20).works().getFirst().getId());
        assertTrue(service.works(album, "不存在", 1, 20).works().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.works(album, "", 0, 10));
    }

    @Test
    void rejectsInvalidIdsAndAcceptsEmptyBatches() {
        assertEquals(0, service().addWorks(List.of(), List.of(1)));
        assertThrows(IllegalArgumentException.class, () -> service().addWorks(List.of(1), List.of(0)));
        assertThrows(IllegalArgumentException.class, () -> service().addWorks(null, List.of(1)));
    }

    @Test
    void workDeletionTargetsRecordIdAcrossSources() throws Exception {
        DI.bind(WorkDao.class, AsmrWorkDao::new);
        int first = work("RJ1", "/same", 1);
        int second = work("RJ1", "/same", 2);
        var service = service();
        int album = service.create("A", "").id();
        service.addWorks(List.of(album), List.of(first, second));
        assertTrue(DI.get(FileOperationService.class).deleteWork(new AsmrWorkDao().getWorkById(first), false).success());
        assertNull(new AsmrWorkDao().getWorkById(first));
        assertNotNull(new AsmrWorkDao().getWorkById(second));
        assertEquals(1, service.summaries().getFirst().workCount());
        assertEquals(second, service.works(album, "", 1, 20).works().getFirst().getId());
    }

    @Test
    void updatesAndUpsertsPreserveMembershipsAndRecordIds() throws Exception {
        int id = work("RJ1", "/one", 1);
        int album = service().create("A", "").id();
        service().addWorks(List.of(album), List.of(id));
        AsmrWorkDao works = new AsmrWorkDao();
        var original = works.getWorkById(id);
        original.setTitle("更新作品");
        original.setFilepath("/moved");
        assertTrue(works.updateWorkById(original));
        assertEquals(id, service().works(album, "", 1, 20).works().getFirst().getId());
        assertTrue(works.addAndReplaceWork(original));
        assertEquals(id, works.getWorkByFilepath("/moved", 0, 1).getId());
        assertEquals(1, service().summaries().getFirst().workCount());
        original.setWorkId("BJ123");
        assertTrue(works.addAndReplaceWork(original));
        assertEquals(id, works.getWorkByFilepath("/moved", 0, 1).getId());
        assertEquals(1, service().summaries().getFirst().workCount());
    }

    @Test
    void libraryDeletionRespectsKeepWorksChoice() throws Exception {
        DI.bind(WorkDao.class, AsmrWorkDao::new);
        var libraries = DI.get(MediaLibraryService.class);
        MediaLibrary keep = new MediaLibrary("保留作品", "/keep", 1, StorageType.LOCAL, 1);
        MediaLibrary remove = new MediaLibrary("删除作品", "/remove", 1, StorageType.LOCAL, 1);
        libraries.add(keep);
        libraries.add(remove);
        int keepId = libraries.getByPath("/keep").getId();
        int removeId = libraries.getByPath("/remove").getId();
        int first = work(null, "/keep/one", 1);
        int second = work(null, "/remove/two", 1);
        try (var connection = JdbcUtils.getConnection(); var statement = connection.createStatement()) {
            statement.execute("UPDATE asmr_works SET media_library_id = " + keepId + " WHERE id = " + first);
            statement.execute("UPDATE asmr_works SET media_library_id = " + removeId + " WHERE id = " + second);
        }
        int album = service().create("A", "").id();
        assertEquals(1, service().candidates(album, keepId, "", 1, 20).total());
        assertEquals(1, service().candidates(album, removeId, "", 1, 20).total());
        service().addWorks(List.of(album), List.of(first, second));
        assertEquals(0, service().candidates(album, keepId, "", 1, 20).total());
        assertTrue(libraries.deleteLibraryKeepWorks(libraries.getById(keepId)));
        assertEquals(2, service().summaries().getFirst().workCount());
        assertTrue(libraries.deleteLibraryWithWorks(libraries.getById(removeId)));
        assertEquals(1, service().summaries().getFirst().workCount());
        assertEquals(first, service().works(album, "", 1, 20).works().getFirst().getId());
    }
}
