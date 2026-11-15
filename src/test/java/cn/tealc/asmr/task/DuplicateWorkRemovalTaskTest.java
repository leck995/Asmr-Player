package cn.tealc.asmr.task;

import cn.tealc.asmr.BaseDatabaseTest;
import cn.tealc.asmr.dao.AsmrWorkDao;
import cn.tealc.asmr.dao.WorkDao;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.service.WorkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DuplicateWorkRemovalTaskTest extends BaseDatabaseTest {
    private WorkService service;

    @BeforeEach
    void setup() {
        DI.bind(WorkDao.class, AsmrWorkDao::new);
        service = DI.get(WorkService.class);
    }

    @Test
    void databaseOnlyRemovalDoesNotTouchStorageOrOtherSourceWithSamePath() {
        AsmrWork selected = insert(1);
        AsmrWork other = insert(2);
        assertTrue(DuplicateWorkRemovalTask.removeOne(selected, false, service, ignored -> {
            fail("仅移除数据库时不应访问存储");
            return false;
        }));
        assertNull(service.getById(selected.getId()));
        assertNotNull(service.getById(other.getId()));
    }

    @Test
    void failedStorageDeletionKeepsDatabaseRecord() {
        AsmrWork work = insert(1);
        assertFalse(DuplicateWorkRemovalTask.removeOne(work, true, service, ignored -> false));
        assertNotNull(service.getById(work.getId()));
    }

    @Test
    void successfulStorageDeletionOnlyRemovesSelectedRecord() {
        AsmrWork work = insert(1);
        AsmrWork other = insert(2);
        assertTrue(DuplicateWorkRemovalTask.removeOne(work, true, service, ignored -> true));
        assertNull(service.getById(work.getId()));
        assertNotNull(service.getById(other.getId()));
    }

    @Test
    void invalidIdDoesNotDeleteStorage() {
        assertFalse(DuplicateWorkRemovalTask.removeOne(new AsmrWork(), true, service, ignored -> {
            fail("无效记录不能删除存储");
            return true;
        }));
    }

    @Test
    void storageExceptionLeavesRecordIntact() {
        AsmrWork work = insert(1);
        assertThrows(IllegalStateException.class, () -> DuplicateWorkRemovalTask.removeOne(work, true, service,
                ignored -> { throw new IllegalStateException("远程连接失败"); }));
        assertNotNull(service.getById(work.getId()));
    }

    private AsmrWork insert(int source) {
        AsmrWork work = new AsmrWork();
        work.setWorkId("RJ123456");
        work.setTitle("测试作品");
        work.setFilepath("/library/RJ123456");
        work.setStorageSourceId(source);
        work.setStorageType(0);
        assertTrue(service.addWork(work));
        return service.getByFilepath(work.getFilepath(), 0, source);
    }
}
