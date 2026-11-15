package cn.tealc.asmr.dao;

import cn.tealc.asmr.BaseDatabaseTest;
import cn.tealc.asmr.dao.collection.CollectionItemDao;
import cn.tealc.asmr.dao.collection.CollectionSheetDao;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.collection.ItemData;
import cn.tealc.asmr.service.WorkService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkIdentityTest extends BaseDatabaseTest {
    private final AsmrWorkDao dao = new AsmrWorkDao();

    private AsmrWork work(String workId, int sourceId, String suffix) {
        AsmrWork work = new AsmrWork();
        work.setWorkId(workId);
        work.setTitle("测试作品");
        work.setFilepath("/library/" + workId + suffix);
        work.setStorageSourceId(sourceId);
        work.setStorageType(0);
        work.setRelatedWorkIds("BJ123456#中文*VJ123456#日文");
        work.setOriginWorkId("VJ123456");
        return work;
    }

    @ParameterizedTest
    @ValueSource(strings = {"RJ123456", "BJ123456", "VJ123456"})
    void persistsQueriesAndUpdatesIdentity(String workId) {
        assertTrue(dao.addWork(work(workId, 1, "")));
        DI.bind(WorkDao.class, AsmrWorkDao::new);
        WorkService service = DI.get(WorkService.class);
        AsmrWork loaded = service.getByWorkId(workId, 0, 1);
        assertNotNull(loaded);
        assertEquals(workId, loaded.getWorkId());
        assertEquals(loaded.getId(), service.getByWorkId(workId, 0).getId());
        assertEquals(loaded.getId(), service.getById(loaded.getId()).getId());
        assertEquals("BJ123456#中文*VJ123456#日文", loaded.getRelatedWorkIds());
        assertEquals("VJ123456", loaded.getOriginWorkId());

        loaded.setWorkId(workId.substring(0, 2) + "654321");
        loaded.setRelatedWorkIds("BJ654321#中文");
        loaded.setOriginWorkId("VJ654321");
        assertTrue(dao.updateWorkById(loaded));
        assertNull(service.getByWorkId(workId, 0, 1));
        AsmrWork updated = service.getByWorkId(loaded.getWorkId(), 0, 1);
        assertEquals(loaded.getId(), updated.getId());
        assertEquals("BJ654321#中文", updated.getRelatedWorkIds());
        assertEquals("VJ654321", updated.getOriginWorkId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RJ123456", "BJ123456", "VJ123456"})
    void scopesUniquenessToStorageSource(String workId) {
        assertTrue(dao.addWork(work(workId, 1, "")));
        assertFalse(dao.addWork(work(workId, 1, "-duplicate")));
        assertTrue(dao.addWork(work(workId, 2, "")));
        assertNotEquals(dao.getWorkByWorkId(workId, 0, 1).getId(),
                dao.getWorkByWorkId(workId, 0, 2).getId());
    }

    @Test
    void separatesPrefixesInSearchAndSort() {
        for (String prefix : List.of("RJ", "BJ", "VJ")) {
            assertTrue(dao.addWork(work(prefix + "123456", 1, "")));
            List<AsmrWork> result = dao.searchWorks(prefix + "123456", 1, 10, 1,
                    null, null, null, null, null);
            assertEquals(1, result.size());
            assertEquals(prefix + "123456", result.getFirst().getWorkId());
            assertEquals(1, dao.searchWorksCount(prefix + "123456", null, null, null));
        }
        assertEquals(List.of("BJ123456", "RJ123456", "VJ123456"),
                dao.getAllWorks(1, 10, 2, null, null, false, null, null)
                        .stream().map(AsmrWork::getWorkId).toList());
    }

    @Test
    void permitsMultipleWorksWithoutIdentifier() {
        assertTrue(dao.addWork(work(null, 1, "-one")));
        assertTrue(dao.addWork(work(null, 1, "-two")));
        assertEquals(2, dao.getAllWorks().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RJ123456", "BJ123456", "VJ123456"})
    void mapsCollectionIdentitySeparatelyFromForeignKey(String workId) {
        assertTrue(dao.addWork(work(workId, 1, "")));
        AsmrWork saved = dao.getWorkByWorkId(workId, 0, 1);
        CollectionSheetDao sheets = new CollectionSheetDao();
        assertTrue(sheets.createSheet("歌单"));
        int sheetId = sheets.getAllSheets().getFirst().getId();
        ItemData item = new ItemData();
        item.setName("音轨");
        item.setFilepath(saved.getFilepath() + "/track.mp3");
        item.setWorkId(saved.getId());
        item.setSheetId(sheetId);
        CollectionItemDao items = new CollectionItemDao();
        assertTrue(items.addItem(item));

        var loaded = items.getItemsWithWorkInfoBySheet(sheetId).getFirst();
        assertEquals(saved.getId(), loaded.getWorkId());
        assertEquals(saved.getId(), loaded.getWork().getId());
        assertEquals(workId, loaded.getWork().getWorkId());
        assertEquals(saved.getRelatedWorkIds(), loaded.getWork().getRelatedWorkIds());
        assertEquals(saved.getOriginWorkId(), loaded.getWork().getOriginWorkId());

        var summary = sheets.getSheetSummaries().getFirst();
        assertEquals(1, summary.getItemCount());
        assertEquals(saved.getId(), summary.getLatestWork().getId());
        assertEquals(workId, summary.getLatestWork().getWorkId());
    }
}
