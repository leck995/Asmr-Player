package cn.tealc.asmr.dao;

import cn.tealc.asmr.BaseDatabaseTest;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.Actor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link WorkVaDao} 全量测试。
 * <p>
 * 操作表 {@code asmr_vas}，返回 {@link Actor}。基类
 * {@link BaseDatabaseTest} 的 {@code @BeforeEach} 已自动清空该表，
 * 故本类无需额外清表。结构与 {@link WorkCategoryDaoTest} 对称。
 *
 * @author Leck
 */
@DisplayName("WorkVaDao 测试")
class WorkVaDaoTest extends BaseDatabaseTest {

    private WorkVaDao dao;

    @BeforeEach
    void setUp() {
        dao = new WorkVaDao();
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造测试作品对象，vas 字段使用指定声优名。
     * addWork 需要 22 个字段，此处填满常用字段。
     */
    private AsmrWork createWorkWithVa(String workId, String va) {
        AsmrWork work = new AsmrWork();
        work.setStorageSourceId(1);
        work.setWorkId(workId);
        work.setTitle(workId + "_title");
        work.setTitleCN(workId + "_cn");
        work.setCircle("社团A");
        work.setRelease("2024-01-01");
        work.setRate(4.5);
        work.setSubtitle(true);
        work.setNsfw(false);
        work.setVisits(100);
        work.setFavorites(false);
        work.setSources("dlsite");
        work.setFilepath("/audio/" + workId);
        work.setParentPath("/audio");
        work.setVas(va);
        work.setTags("标签1");
        work.setCategories("分类1");
        work.setThumbnailCoverUrl("http://thumb.url");
        work.setMainCoverUrl("http://main.url");
        work.setStorageType(0);
        work.setHasInfo(true);
        work.setRelatedWorkIds("RJ000001");
        work.setOriginWorkId("RJ000002");
        return work;
    }

    @Test
    @DisplayName("addVa 正常添加，getAllVas 包含")
    void addVa_normalInsert() {
        assertTrue(dao.addVa("声优A"));
        List<Actor> all = dao.getAllVas();
        assertTrue(all.stream().anyMatch(a -> "声优A".equals(a.getName())));
    }

    @Test
    @DisplayName("addVa 重复添加返回 false")
    void addVa_duplicate_returnsFalse() {
        assertTrue(dao.addVa("声优A"));
        assertFalse(dao.addVa("声优A"));
    }

    @Test
    @DisplayName("deleteVaByName 正常删除，getAll 不含")
    void deleteVaByName_normalDelete() {
        dao.addVa("声优A");
        assertTrue(dao.deleteVaByName("声优A"));
        List<Actor> all = dao.getAllVas();
        assertFalse(all.stream().anyMatch(a -> "声优A".equals(a.getName())));
    }

    @Test
    @DisplayName("deleteVaByName 不存在返回 false")
    void deleteVaByName_notExists_returnsFalse() {
        assertFalse(dao.deleteVaByName("不存在的声优"));
    }

    @Test
    @DisplayName("getAllVas 空表返回空列表")
    void getAllVas_emptyTable_returnsEmptyList() {
        List<Actor> all = dao.getAllVas();
        assertTrue(all.isEmpty());
    }

    @Test
    @DisplayName("getAllVas 有数据返回全部")
    void getAllVas_hasData_returnsAll() {
        dao.addVa("声优A");
        dao.addVa("声优B");
        List<Actor> all = dao.getAllVas();
        assertEquals(2, all.size());
    }

    @Test
    @DisplayName("getAllVas 验证关联作品数 num 大于 0")
    void getAllVas_verifyNum() {
        dao.addVa("声优A");
        // 插入带该声优的作品，建立关联
        AsmrWorkDao workDao = new AsmrWorkDao();
        workDao.addWork(createWorkWithVa("RJ123456", "声优A"));

        List<Actor> all = dao.getAllVas();
        Actor target = all.stream()
                .filter(a -> "声优A".equals(a.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(target, "应能查到刚添加的声优");
        assertTrue(target.getNum() > 0, "关联作品数 num 应大于 0");
    }
}
