package cn.tealc.asmr.service;

import cn.tealc.asmr.BaseDatabaseTest;
import cn.tealc.asmr.dao.AsmrWorkDao;
import cn.tealc.asmr.dao.WorkDao;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AsmrWork;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link WorkService} 单元测试。
 * <p>
 * 聚焦本次重构补齐的 5 个方法（getWorkByTitle/getAllWorks/updateWorkFilePath/
 * updateWorkFilePathAndParent），以及 addWork/getById 基础方法。
 *
 * @author Leck
 */
class WorkServiceTest extends BaseDatabaseTest {

    private WorkService service() {
        DI.bind(WorkDao.class, AsmrWorkDao::new);
        return DI.get(WorkService.class);
    }

    /** 构造一个本地库作品（storageType=0） */
    private AsmrWork newWork(String title, String workId, String filepath) {
        AsmrWork work = new AsmrWork();
        work.setStorageSourceId(1);
        work.setTitle(title);
        work.setWorkId(workId);
        work.setFilepath(filepath);
        work.setParentPath("/audio/library1");
        work.setStorageType(0);
        return work;
    }

    /** 插入作品并从数据库查回真实 ID（addWork 不回填自增键） */
    private AsmrWork insertAndReload(WorkService svc, AsmrWork work) {
        svc.addWork(work);
        // 按 filepath 唯一键查回，拿到数据库分配的真实 ID
        return svc.getByFilepath(work.getFilepath(), 0);
    }

    @Test
    @DisplayName("addWork 插入后 getById 能查回并验证字段")
    void addWork_andGetById() {
        WorkService svc = service();
        AsmrWork work = newWork("测试作品", "RJ123456", "/audio/library1/RJ123456");
        svc.addWork(work);

        // addWork 不回填自增 ID，用 getByFilepath 拿真实记录
        AsmrWork loaded = svc.getByFilepath(work.getFilepath(), 0);
        assertNotNull(loaded);
        assertEquals("测试作品", loaded.getTitle());
        assertEquals("RJ123456", loaded.getWorkId());

        // 再用 getById 验证
        AsmrWork byId = svc.getById(loaded.getId());
        assertNotNull(byId);
        assertEquals("测试作品", byId.getTitle());
    }

    @Test
    @DisplayName("getWorkByTitle 按标题查回作品")
    void getWorkByTitle() {
        WorkService svc = service();
        svc.addWork(newWork("找到我", "RJ111", "/audio/lib/RJ111"));

        AsmrWork found = svc.getWorkByTitle("找到我");
        assertNotNull(found);
        assertEquals("RJ111", found.getWorkId());
    }

    @Test
    @DisplayName("getAllWorks 返回全部作品")
    void getAllWorks() {
        WorkService svc = service();
        svc.addWork(newWork("作品A", "RJ001", "/audio/lib/A"));
        svc.addWork(newWork("作品B", "RJ002", "/audio/lib/B"));
        svc.addWork(newWork("作品C", "RJ003", "/audio/lib/C"));

        List<AsmrWork> all = svc.getAllWorks();
        assertEquals(3, all.size());
    }

    @Test
    @DisplayName("updateWorkFilePath 更新文件路径后查回验证")
    void updateWorkFilePath() {
        WorkService svc = service();
        AsmrWork work = insertAndReload(svc, newWork("路径测试", "RJ222", "/audio/lib/old"));

        work.setFilepath("/audio/lib/new");
        boolean ok = svc.updateWorkFilePath(work);
        assertTrue(ok);

        AsmrWork loaded = svc.getById(work.getId());
        assertEquals("/audio/lib/new", loaded.getFilepath());
        // parentPath 不应被改动
        assertEquals("/audio/library1", loaded.getParentPath());
    }

    @Test
    @DisplayName("updateWorkFilePathAndParent 同时更新文件路径和父目录")
    void updateWorkFilePathAndParent() {
        WorkService svc = service();
        AsmrWork work = insertAndReload(svc, newWork("双路径测试", "RJ333", "/old/lib/path"));

        work.setFilepath("/new/lib/path");
        work.setParentPath("/new/lib");
        boolean ok = svc.updateWorkFilePathAndParent(work);
        assertTrue(ok);

        AsmrWork loaded = svc.getById(work.getId());
        assertEquals("/new/lib/path", loaded.getFilepath());
        assertEquals("/new/lib", loaded.getParentPath());
    }


}
