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
 * {@link WorkCircleDao} 全量测试。
 * <p>
 * 操作表 {@code asmr_circles}，返回 {@link Actor}。基类
 * {@link BaseDatabaseTest} 的 {@code @BeforeEach} 已自动清空该表，
 * 故本类无需额外清表。结构与 {@link WorkCategoryDaoTest} 对称。
 *
 * @author Leck
 */
@DisplayName("WorkCircleDao 测试")
class WorkCircleDaoTest extends BaseDatabaseTest {

    private WorkCircleDao dao;

    @BeforeEach
    void setUp() {
        dao = new WorkCircleDao();
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造测试作品对象，circle 字段使用指定社团名。
     * addWork 需要 22 个字段，此处填满常用字段。
     */
    private AsmrWork createWorkWithCircle(String workId, String circle) {
        AsmrWork work = new AsmrWork();
        work.setStorageSourceId(1);
        work.setWorkId(workId);
        work.setTitle(workId + "_title");
        work.setTitleCN(workId + "_cn");
        work.setCircle(circle);
        work.setRelease("2024-01-01");
        work.setRate(4.5);
        work.setSubtitle(true);
        work.setNsfw(false);
        work.setVisits(100);
        work.setFavorites(false);
        work.setSources("dlsite");
        work.setFilepath("/audio/" + workId);
        work.setParentPath("/audio");
        work.setVas("声优X");
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
    @DisplayName("addCircle 正常添加，getAllCircles 包含")
    void addCircle_normalInsert() {
        assertTrue(dao.addCircle("社团A"));
        List<Actor> all = dao.getAllCircles();
        assertTrue(all.stream().anyMatch(a -> "社团A".equals(a.getName())));
    }

    @Test
    @DisplayName("addCircle 重复添加返回 false")
    void addCircle_duplicate_returnsFalse() {
        assertTrue(dao.addCircle("社团A"));
        assertFalse(dao.addCircle("社团A"));
    }

    @Test
    @DisplayName("deleteCircleByName 正常删除，getAll 不含")
    void deleteCircleByName_normalDelete() {
        dao.addCircle("社团A");
        assertTrue(dao.deleteCircleByName("社团A"));
        List<Actor> all = dao.getAllCircles();
        assertFalse(all.stream().anyMatch(a -> "社团A".equals(a.getName())));
    }

    @Test
    @DisplayName("deleteCircleByName 不存在返回 false")
    void deleteCircleByName_notExists_returnsFalse() {
        assertFalse(dao.deleteCircleByName("不存在的社团"));
    }

    @Test
    @DisplayName("getAllCircles 空表返回空列表")
    void getAllCircles_emptyTable_returnsEmptyList() {
        List<Actor> all = dao.getAllCircles();
        assertTrue(all.isEmpty());
    }

    @Test
    @DisplayName("getAllCircles 有数据返回全部")
    void getAllCircles_hasData_returnsAll() {
        dao.addCircle("社团A");
        dao.addCircle("社团B");
        List<Actor> all = dao.getAllCircles();
        assertEquals(2, all.size());
    }

    @Test
    @DisplayName("getAllCircles 验证关联作品数 num 大于 0")
    void getAllCircles_verifyNum() {
        dao.addCircle("社团A");
        // 插入带该社团的作品，建立关联
        AsmrWorkDao workDao = new AsmrWorkDao();
        workDao.addWork(createWorkWithCircle("RJ123456", "社团A"));

        List<Actor> all = dao.getAllCircles();
        Actor target = all.stream()
                .filter(a -> "社团A".equals(a.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(target, "应能查到刚添加的社团");
        assertTrue(target.getNum() > 0, "关联作品数 num 应大于 0");
    }
}
