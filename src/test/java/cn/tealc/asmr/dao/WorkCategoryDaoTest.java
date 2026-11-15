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
 * {@link WorkCategoryDao} 全量测试。
 * <p>
 * 操作表 {@code asmr_categories}，返回 {@link Actor}。基类
 * {@link BaseDatabaseTest} 的 {@code @BeforeEach} 已自动清空该表，
 * 故本类无需额外清表。
 *
 * @author Leck
 */
@DisplayName("WorkCategoryDao 测试")
class WorkCategoryDaoTest extends BaseDatabaseTest {

    private WorkCategoryDao dao;

    @BeforeEach
    void setUp() {
        dao = new WorkCategoryDao();
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造测试作品对象，categories 字段使用指定分类名。
     * addWork 需要 22 个字段，此处填满常用字段。
     */
    private AsmrWork createWorkWithCategory(String workId, String category) {
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
        work.setVas("声优X");
        work.setTags("标签1");
        work.setCategories(category);
        work.setThumbnailCoverUrl("http://thumb.url");
        work.setMainCoverUrl("http://main.url");
        work.setStorageType(0);
        work.setHasInfo(true);
        work.setRelatedWorkIds("RJ000001");
        work.setOriginWorkId("RJ000002");
        return work;
    }

    @Test
    @DisplayName("addCategory 正常添加，getAllCategories 包含")
    void addCategory_normalInsert() {
        assertTrue(dao.addCategory("ASMR"));
        List<Actor> all = dao.getAllCategories();
        assertTrue(all.stream().anyMatch(a -> "ASMR".equals(a.getName())));
    }

    @Test
    @DisplayName("addCategory 重复添加返回 false")
    void addCategory_duplicate_returnsFalse() {
        assertTrue(dao.addCategory("ASMR"));
        assertFalse(dao.addCategory("ASMR"));
    }

    @Test
    @DisplayName("deleteCategoryByName 正常删除，getAll 不含")
    void deleteCategoryByName_normalDelete() {
        dao.addCategory("ASMR");
        assertTrue(dao.deleteCategoryByName("ASMR"));
        List<Actor> all = dao.getAllCategories();
        assertFalse(all.stream().anyMatch(a -> "ASMR".equals(a.getName())));
    }

    @Test
    @DisplayName("deleteCategoryByName 不存在返回 false")
    void deleteCategoryByName_notExists_returnsFalse() {
        assertFalse(dao.deleteCategoryByName("不存在的分类"));
    }

    @Test
    @DisplayName("getAllCategories 空表返回空列表")
    void getAllCategories_emptyTable_returnsEmptyList() {
        List<Actor> all = dao.getAllCategories();
        assertTrue(all.isEmpty());
    }

    @Test
    @DisplayName("getAllCategories 有数据返回全部")
    void getAllCategories_hasData_returnsAll() {
        dao.addCategory("分类A");
        dao.addCategory("分类B");
        List<Actor> all = dao.getAllCategories();
        assertEquals(2, all.size());
    }

    @Test
    @DisplayName("getAllCategories 验证关联作品数 num 大于 0")
    void getAllCategories_verifyNum() {
        dao.addCategory("ASMR");
        // 插入带该分类的作品，建立关联
        AsmrWorkDao workDao = new AsmrWorkDao();
        workDao.addWork(createWorkWithCategory("RJ123456", "ASMR"));

        List<Actor> all = dao.getAllCategories();
        Actor target = all.stream()
                .filter(a -> "ASMR".equals(a.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(target, "应能查到刚添加的分类");
        assertTrue(target.getNum() > 0, "关联作品数 num 应大于 0");
    }
}
