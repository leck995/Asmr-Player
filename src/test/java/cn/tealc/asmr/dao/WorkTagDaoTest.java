package cn.tealc.asmr.dao;

import cn.tealc.asmr.BaseDatabaseTest;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.Actor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link WorkTagDao} 全量测试。
 * <p>
 * 操作表 {@code asmr_tags}，返回 {@link Actor}。基类
 * {@link BaseDatabaseTest} 的 {@code @BeforeEach} 已自动清空该表，
 * 故本类无需额外清表。结构与 {@link WorkCategoryDaoTest} 对称。
 *
 * @author Leck
 */
@DisplayName("WorkTagDao 测试")
class WorkTagDaoTest extends BaseDatabaseTest {

    private WorkTagDao dao;

    @BeforeEach
    void setUp() {
        dao = new WorkTagDao();
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造测试作品对象，tags 字段使用指定标签名。
     * addWork 需要 22 个字段，此处填满常用字段。
     */
    private AsmrWork createWorkWithTag(String workId, String tag) {
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
        work.setTags(tag);
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
    @DisplayName("addTag 正常添加，getAllTags 包含")
    void addTag_normalInsert() {
        assertTrue(dao.addTag("治愈"));
        List<Actor> all = dao.getAllTags();
        assertTrue(all.stream().anyMatch(a -> "治愈".equals(a.getName())));
    }

    @Test
    @DisplayName("addTag 重复添加返回 false")
    void addTag_duplicate_returnsFalse() {
        assertTrue(dao.addTag("治愈"));
        assertFalse(dao.addTag("治愈"));
    }

    @Test
    @DisplayName("addTag 保存描述与创建/修改时间")
    void addTag_withDescription() {
        assertTrue(dao.addTag("治愈", "舒缓心情"));
        Actor tag = dao.getAllTags().get(0);
        assertEquals("治愈", tag.getName());
        assertEquals("舒缓心情", tag.getDescription());
        assertFalse(tag.getCreateDateTime().isBlank());
        assertFalse(tag.getUpdateDateTime().isBlank());
    }

    @Test
    @DisplayName("updateTagDescription 更新描述与修改时间")
    void updateTagDescription_updatesTime() throws Exception {
        assertTrue(dao.addTag("治愈", "旧描述"));
        Actor tag = dao.getAllTags().get(0);
        try (Connection connection = JdbcUtils.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("UPDATE asmr_tags SET update_date_time = '2000-01-01 00:00:00' WHERE id = " + tag.getId());
        }

        assertTrue(dao.updateTagDescription(tag.getId(), "新描述"));

        Actor updated = dao.getAllTags().get(0);
        assertEquals("新描述", updated.getDescription());
        assertEquals(tag.getCreateDateTime(), updated.getCreateDateTime());
        assertNotEquals("2000-01-01 00:00:00", updated.getUpdateDateTime());
    }

    @Test
    @DisplayName("deleteTagByName 正常删除，getAll 不含")
    void deleteTagByName_normalDelete() {
        dao.addTag("治愈");
        assertTrue(dao.deleteTagByName("治愈"));
        List<Actor> all = dao.getAllTags();
        assertFalse(all.stream().anyMatch(a -> "治愈".equals(a.getName())));
    }

    @Test
    @DisplayName("deleteTagByName 不存在返回 false")
    void deleteTagByName_notExists_returnsFalse() {
        assertFalse(dao.deleteTagByName("不存在的标签"));
    }

    @Test
    @DisplayName("getAllTags 空表返回空列表")
    void getAllTags_emptyTable_returnsEmptyList() {
        List<Actor> all = dao.getAllTags();
        assertTrue(all.isEmpty());
    }

    @Test
    @DisplayName("getAllTags 有数据返回全部")
    void getAllTags_hasData_returnsAll() {
        dao.addTag("标签A");
        dao.addTag("标签B");
        List<Actor> all = dao.getAllTags();
        assertEquals(2, all.size());
    }

    @Test
    @DisplayName("getAllTags 验证关联作品数 num 大于 0")
    void getAllTags_verifyNum() {
        dao.addTag("治愈");
        // 插入带该标签的作品，建立关联
        AsmrWorkDao workDao = new AsmrWorkDao();
        workDao.addWork(createWorkWithTag("RJ123456", "治愈"));

        List<Actor> all = dao.getAllTags();
        Actor target = all.stream()
                .filter(a -> "治愈".equals(a.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(target, "应能查到刚添加的标签");
        assertTrue(target.getNum() > 0, "关联作品数 num 应大于 0");
    }
}
