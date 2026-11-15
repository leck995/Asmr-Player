package cn.tealc.asmr.dao;

import cn.tealc.asmr.BaseDatabaseTest;
import cn.tealc.asmr.model.AsmrWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AsmrWorkDao} 全量测试，覆盖全部 40 个 public 方法。
 * <p>
 * 每个测试方法前由 {@link BaseDatabaseTest#clearTables()} 清空 asmr_works 表，
 * 确保测试间数据隔离。测试数据由 {@link #createWork} 工厂方法构造。
 *
 * @author Leck
 */
@DisplayName("AsmrWorkDao 测试")
class AsmrWorkDaoTest extends BaseDatabaseTest {

    private AsmrWorkDao dao;

    @BeforeEach
    void setUp() {
        dao = new AsmrWorkDao();
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造测试作品对象，填满常用字段。
     */
    private AsmrWork createWork(String workId, String title, String filepath, String parentPath) {
        AsmrWork work = new AsmrWork();
        work.setStorageSourceId(1);
        work.setWorkId(workId);
        work.setTitle(title);
        work.setTitleCN(title + "_CN");
        work.setCircle("社团A");
        work.setRelease("2024-01-01");
        work.setRate(4.5);
        work.setSubtitle(true);
        work.setNsfw(false);
        work.setVisits(100);
        work.setFavorites(false);
        work.setSources("dlsite");
        work.setFilepath(filepath);
        work.setParentPath(parentPath);
        work.setVas("声优X,声优Y");
        work.setTags("标签1,标签2");
        work.setCategories("分类1,分类2");
        work.setThumbnailCoverUrl("http://thumb.url");
        work.setMainCoverUrl("http://main.url");
        work.setStorageType(0);
        work.setHasInfo(true);
        work.setRelatedWorkIds("RJ000001");
        work.setOriginWorkId("RJ000002");
        return work;
    }

    /**
     * 插入作品并返回带有数据库自增 id 的对象。
     * addWork 不回填 id，需要重新查询获取。
     */
    private AsmrWork insertAndGet(AsmrWork work) {
        dao.addWork(work);
        return dao.getWorkByFilepath(work.getFilepath());
    }

    // ==================== 写入操作 ====================

    @Nested
    @DisplayName("写入操作")
    class WriteTests {

        @Test
        @DisplayName("addWork 正常插入返回 true")
        void addWork_normalInsert_returnsTrue() {
            AsmrWork work = createWork("RJ123456", "测试作品", "/audio/RJ123456", "/audio");
            assertTrue(dao.addWork(work));
        }

        @Test
        @DisplayName("addWork 重复插入返回 false（约束冲突）")
        void addWork_duplicate_returnsFalse() {
            AsmrWork work = createWork("RJ123456", "测试作品", "/audio/RJ123456", "/audio");
            dao.addWork(work);
            assertFalse(dao.addWork(work));
        }

        @Test
        @DisplayName("addWork 不同 storage_type 同 work_id 可共存")
        void addWork_differentStorageType_sameWorkId() {
            AsmrWork w1 = createWork("RJ123456", "作品A", "/local/RJ123456", "/local");
            w1.setStorageType(0);
            AsmrWork w2 = createWork("RJ123456", "作品A", "/remote/RJ123456", "/remote");
            w2.setStorageType(1);
            w2.setStorageSourceId(2);
            assertTrue(dao.addWork(w1));
            assertTrue(dao.addWork(w2));
        }

        @Test
        @DisplayName("addWork null 字段使用默认值")
        void addWork_nullFields_usesDefaults() {
            AsmrWork work = new AsmrWork();
        work.setStorageSourceId(1);
            work.setWorkId("RJ000001");
            work.setTitle("空字段作品");
            work.setFilepath("/audio/RJ000001");
            work.setParentPath("/audio");
            work.setStorageType(0);
            assertTrue(dao.addWork(work));
            AsmrWork result = dao.getWorkByFilepath("/audio/RJ000001");
            assertNotNull(result);
            assertEquals(0, result.getRate());
            assertFalse(result.getSubtitle());
            assertFalse(result.getNsfw());      // addWork 对 null nsfw 传 false
            assertEquals(0, result.getVisits());
            assertFalse(result.getFavorites());
            assertFalse(result.getHasInfo());
        }

        @Test
        @DisplayName("addAndReplaceWork 正常插入返回 true")
        void addAndReplaceWork_normalInsert_returnsTrue() {
            AsmrWork work = createWork("RJ123456", "测试作品", "/audio/RJ123456", "/audio");
            assertTrue(dao.addAndReplaceWork(work));
        }

        @Test
        @DisplayName("addAndReplaceWork 已存在则替换更新")
        void addAndReplaceWork_exists_replaces() {
            AsmrWork work = createWork("RJ123456", "原标题", "/audio/RJ123456", "/audio");
            dao.addWork(work);
            work.setTitle("替换后标题");
            work.setRate(5.0);
            assertTrue(dao.addAndReplaceWork(work));
            AsmrWork result = dao.getWorkByFilepath("/audio/RJ123456");
            assertEquals("替换后标题", result.getTitle());
            assertEquals(5.0, result.getRate());
        }

        @Test
        @DisplayName("addAndReplaceWork 包含 storage_type 列")
        void addAndReplaceWork_includesStorageType() {
            AsmrWork work = createWork("RJ123456", "测试", "/audio/RJ123456", "/audio");
            work.setStorageType(1);
            dao.addAndReplaceWork(work);
            AsmrWork result = dao.getWorkByFilepath("/audio/RJ123456");
            assertEquals(1, result.getStorageType());
        }

    }

    // ==================== 查询操作 ====================

    @Nested
    @DisplayName("查询操作")
    class QueryTests {

        @Test
        @DisplayName("getAllWorks 空表返回空列表")
        void getAllWorks_emptyTable_returnsEmptyList() {
            List<AsmrWork> result = dao.getAllWorks();
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("getAllWorks 有数据返回全部")
        void getAllWorks_hasData_returnsAll() {
            for (int i = 1; i <= 3; i++) {
                dao.addWork(createWork("RJ" + i, "作品" + i, "/audio/RJ" + i, "/audio"));
            }
            assertEquals(3, dao.getAllWorks().size());
        }

        @Test
        @DisplayName("getAllWorks 分页查询第一页")
        void getAllWorks_paging_firstPage() {
            for (int i = 1; i <= 3; i++) {
                dao.addWork(createWork("RJ" + i, "作品" + i, "/audio/RJ" + i, "/audio"));
            }
            List<AsmrWork> result = dao.getAllWorks(1, 2, 1, null, null, null, null, null);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("getAllWorks 分页查询第二页")
        void getAllWorks_paging_secondPage() {
            for (int i = 1; i <= 3; i++) {
                dao.addWork(createWork("RJ" + i, "作品" + i, "/audio/RJ" + i, "/audio"));
            }
            List<AsmrWork> result = dao.getAllWorks(2, 2, 1, null, null, null, null, null);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("getAllWorks 过滤 nsfw=false")
        void getAllWorks_filterNsfw() {
            AsmrWork w1 = createWork("RJ1", "作品1", "/a/RJ1", "/a");
            w1.setNsfw(false);
            AsmrWork w2 = createWork("RJ2", "作品2", "/a/RJ2", "/a");
            w2.setNsfw(true);
            dao.addWork(w1);
            dao.addWork(w2);
            List<AsmrWork> result = dao.getAllWorks(1, 10, 1, null, false, null, null, null);
            assertEquals(1, result.size());
            assertEquals("RJ1", result.get(0).getWorkId());
        }

        @Test
        @DisplayName("getAllWorks 过滤 subtitle=true")
        void getAllWorks_filterSubtitle() {
            AsmrWork w1 = createWork("RJ1", "作品1", "/a/RJ1", "/a");
            w1.setSubtitle(true);
            AsmrWork w2 = createWork("RJ2", "作品2", "/a/RJ2", "/a");
            w2.setSubtitle(false);
            dao.addWork(w1);
            dao.addWork(w2);
            List<AsmrWork> result = dao.getAllWorks(1, 10, 1, true, null, null, null, null);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("getAllWorks 过滤 noMate (sources is null)")
        void getAllWorks_filterNoMate() {
            AsmrWork w1 = createWork("RJ1", "作品1", "/a/RJ1", "/a");
            w1.setSources(null);
            AsmrWork w2 = createWork("RJ2", "作品2", "/a/RJ2", "/a");
            w2.setSources("dlsite");
            dao.addWork(w1);
            dao.addWork(w2);
            List<AsmrWork> result = dao.getAllWorks(1, 10, 1, null, null, null, true, null);
            assertEquals(1, result.size());
            assertEquals("RJ1", result.get(0).getWorkId());
        }

        @Test
        @DisplayName("getAllWorks 排序 type=1 按 id")
        void getAllWorks_sortById() {
            dao.addWork(createWork("RJ3", "C", "/a/RJ3", "/a"));
            dao.addWork(createWork("RJ1", "A", "/a/RJ1", "/a"));
            dao.addWork(createWork("RJ2", "B", "/a/RJ2", "/a"));
            List<AsmrWork> result = dao.getAllWorks(1, 10, 1, null, null, false, null, null);
            // type=1 order by id ASC, id 递增插入顺序
            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("getAllWorks 排序 type=2 按 work_id")
        void getAllWorks_sortByWorkId() {
            dao.addWork(createWork("RJ3", "C", "/a/RJ3", "/a"));
            dao.addWork(createWork("RJ1", "A", "/a/RJ1", "/a"));
            dao.addWork(createWork("RJ2", "B", "/a/RJ2", "/a"));
            List<AsmrWork> result = dao.getAllWorks(1, 10, 2, null, null, false, null, null);
            assertEquals("RJ1", result.get(0).getWorkId());
            assertEquals("RJ2", result.get(1).getWorkId());
            assertEquals("RJ3", result.get(2).getWorkId());
        }

        @Test
        @DisplayName("getAllWorks 排序 type=3 按 release_date")
        void getAllWorks_sortByReleaseDate() {
            AsmrWork w1 = createWork("RJ1", "A", "/a/RJ1", "/a");
            w1.setRelease("2023-01-01");
            AsmrWork w2 = createWork("RJ2", "B", "/a/RJ2", "/a");
            w2.setRelease("2024-01-01");
            dao.addWork(w1);
            dao.addWork(w2);
            List<AsmrWork> result = dao.getAllWorks(1, 10, 3, null, null, false, null, null);
            assertEquals("RJ1", result.get(0).getWorkId());
        }

        @Test
        @DisplayName("getAllWorks 排序 type=4 按 rate")
        void getAllWorks_sortByRate() {
            AsmrWork w1 = createWork("RJ1", "A", "/a/RJ1", "/a");
            w1.setRate(3.0);
            AsmrWork w2 = createWork("RJ2", "B", "/a/RJ2", "/a");
            w2.setRate(5.0);
            dao.addWork(w1);
            dao.addWork(w2);
            List<AsmrWork> result = dao.getAllWorks(1, 10, 4, null, null, false, null, null);
            assertEquals("RJ1", result.get(0).getWorkId());
        }

        @Test
        @DisplayName("getAllWorks 随机 type=5 返回正确数量")
        void getAllWorks_randomType5() {
            for (int i = 1; i <= 5; i++) {
                dao.addWork(createWork("RJ" + i, "作品" + i, "/a/RJ" + i, "/a"));
            }
            List<AsmrWork> result = dao.getAllWorks(1, 3, 5, null, null, null, null, null);
            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("getAllWorks 倒序 order=true")
        void getAllWorks_descOrder() {
            dao.addWork(createWork("RJ1", "A", "/a/RJ1", "/a"));
            dao.addWork(createWork("RJ2", "B", "/a/RJ2", "/a"));
            List<AsmrWork> result = dao.getAllWorks(1, 10, 1, null, null, true, null, null);
            // order by id DESC
            assertTrue(result.get(0).getId() > result.get(1).getId());
        }

        @Test
        @DisplayName("getAllWorksCount 全部计数")
        void getAllWorksCount_all() {
            for (int i = 1; i <= 3; i++) {
                dao.addWork(createWork("RJ" + i, "作品" + i, "/a/RJ" + i, "/a"));
            }
            assertEquals(3, dao.getAllWorksCount(null, null, null));
        }

        @Test
        @DisplayName("getAllWorksCount 带过滤计数")
        void getAllWorksCount_withFilter() {
            AsmrWork w1 = createWork("RJ1", "A", "/a/RJ1", "/a");
            w1.setNsfw(false);
            AsmrWork w2 = createWork("RJ2", "B", "/a/RJ2", "/a");
            w2.setNsfw(true);
            dao.addWork(w1);
            dao.addWork(w2);
            assertEquals(1, dao.getAllWorksCount(null, false, null));
        }

        @Test
        @DisplayName("getWorksByFavorites 返回收藏作品")
        void getWorksByFavorites_returnsFavorites() {
            AsmrWork w1 = createWork("RJ1", "A", "/a/RJ1", "/a");
            w1.setFavorites(true);
            AsmrWork w2 = createWork("RJ2", "B", "/a/RJ2", "/a");
            w2.setFavorites(false);
            dao.addWork(w1);
            dao.addWork(w2);
            List<AsmrWork> result = dao.getWorksByFavorites(true, 1, 10, 1, null, null, null, null, null);
            assertEquals(1, result.size());
            assertEquals("RJ1", result.get(0).getWorkId());
        }

        @Test
        @DisplayName("getWorksCountByFavorites 计数")
        void getWorksCountByFavorites() {
            AsmrWork w1 = createWork("RJ1", "A", "/a/RJ1", "/a");
            w1.setFavorites(true);
            dao.addWork(w1);
            assertEquals(1, dao.getWorksCountByFavorites(true, null, null, null));
        }

        @Test
        @DisplayName("getWorksByParentFolder 按路径查询")
        void getWorksByParentFolder_byPath() {
            dao.addWork(createWork("RJ1", "A", "/audio/RJ1", "/audio"));
            dao.addWork(createWork("RJ2", "B", "/audio/RJ2", "/audio"));
            dao.addWork(createWork("RJ3", "C", "/video/RJ3", "/video"));
            List<AsmrWork> result = dao.getWorksByParentFolder("/audio", 1, 10, 1, null, null, null, null, null);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("getWorksByParentFolder 路径归一化 - 反斜杠匹配")
        void getWorksByParentFolder_normalizeBackslash() {
            dao.addWork(createWork("RJ1", "A", "/audio/RJ1", "/audio"));
            List<AsmrWork> result = dao.getWorksByParentFolder("\\audio", 1, 10, 1, null, null, null, null, null);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("getWorksByParentFolder 路径归一化 - 尾斜杠匹配")
        void getWorksByParentFolder_normalizeTrailingSlash() {
            dao.addWork(createWork("RJ1", "A", "/audio/RJ1", "/audio"));
            List<AsmrWork> result = dao.getWorksByParentFolder("/audio/", 1, 10, 1, null, null, null, null, null);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("getWorksByFolder 匹配快捷入口目录及其子目录")
        void getWorksByFolder_matchesFolderAndDescendants() {
            dao.addWork(createWork("RJ1", "A", "Q:\\audio\\RJ1", "Q:/audio"));
            dao.addWork(createWork("RJ2", "B", "Q:\\audio\\series\\RJ2", "Q:/audio/series"));
            dao.addWork(createWork("RJ3", "C", "Q:\\audiobook\\RJ3", "Q:/audiobook"));

            List<AsmrWork> result = dao.getWorksByFolder("Q:\\audio", 1, 10, 1, null, null, null, null, null);

            assertEquals(2, result.size());
            assertEquals(2, dao.getWorksCountByFolder("Q:/audio/", null, null, null));
        }

        @Test
        @DisplayName("getAllWorksByParentFolder 不分页查询")
        void getAllWorksByParentFolder_noPaging() {
            dao.addWork(createWork("RJ1", "A", "/audio/RJ1", "/audio"));
            dao.addWork(createWork("RJ2", "B", "/audio/RJ2", "/audio"));
            List<AsmrWork> result = dao.getAllWorksByParentFolder("/audio");
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("getWorkCountsByMediaLibrary 按媒体库聚合作品数量")
        void getWorkCountsByMediaLibrary_groupsByMediaLibrary() {
            AsmrWork first = createWork("RJ1", "A", "/audio/RJ1", "/audio");
            first.setMediaLibraryId(7);
            AsmrWork second = createWork("RJ2", "B", "/audio/RJ2", "/audio");
            second.setMediaLibraryId(7);
            AsmrWork third = createWork("RJ3", "C", "/audio/RJ3", "/audio");
            third.setMediaLibraryId(9);
            dao.addWork(first);
            dao.addWork(second);
            dao.addWork(third);
            dao.addWork(createWork("RJ4", "D", "/audio/RJ4", "/audio"));

            Map<Integer, Integer> counts = dao.getWorkCountsByMediaLibrary();

            assertEquals(Map.of(7, 2, 9, 1), counts);
        }

        @Test
        @DisplayName("getWorksCountByParentFolder 计数")
        void getWorksCountByParentFolder() {
            dao.addWork(createWork("RJ1", "A", "/audio/RJ1", "/audio"));
            dao.addWork(createWork("RJ2", "B", "/audio/RJ2", "/audio"));
            assertEquals(2, dao.getWorksCountByParentFolder("/audio", null, null, null));
        }

        @Test
        @DisplayName("getWorksByCategory LIKE 查询")
        void getWorksByCategory_likeQuery() {
            AsmrWork w1 = createWork("RJ1", "A", "/a/RJ1", "/a");
            w1.setCategories("治愈,ASMR");
            AsmrWork w2 = createWork("RJ2", "B", "/a/RJ2", "/a");
            w2.setCategories("恐怖");
            dao.addWork(w1);
            dao.addWork(w2);
            List<AsmrWork> result = dao.getWorksByCategory("治愈", 1, 10, 1, null, null, null, null, null);
            assertEquals(1, result.size());
            assertEquals("RJ1", result.get(0).getWorkId());
        }

        @Test
        @DisplayName("getWorksByCategory 含 % 通配符正确转义")
        void getWorksByCategory_escapePercent() {
            AsmrWork w1 = createWork("RJ1", "A", "/a/RJ1", "/a");
            w1.setCategories("100%");
            AsmrWork w2 = createWork("RJ2", "B", "/a/RJ2", "/a");
            w2.setCategories("治愈");
            dao.addWork(w1);
            dao.addWork(w2);
            List<AsmrWork> result = dao.getWorksByCategory("100%", 1, 10, 1, null, null, null, null, null);
            assertEquals(1, result.size());
            assertEquals("RJ1", result.get(0).getWorkId());
        }

        @Test
        @DisplayName("getWorksCountByCategory 计数")
        void getWorksCountByCategory() {
            AsmrWork w = createWork("RJ1", "A", "/a/RJ1", "/a");
            w.setCategories("治愈,ASMR");
            dao.addWork(w);
            assertEquals(1, dao.getWorksCountByCategory("治愈", null, null, null));
        }

        @Test
        @DisplayName("getWorksByTag LIKE 查询")
        void getWorksByTag_likeQuery() {
            AsmrWork w1 = createWork("RJ1", "A", "/a/RJ1", "/a");
            w1.setTags("催眠,ASMR");
            AsmrWork w2 = createWork("RJ2", "B", "/a/RJ2", "/a");
            w2.setTags("恐怖");
            dao.addWork(w1);
            dao.addWork(w2);
            List<AsmrWork> result = dao.getWorksByTag("催眠", 1, 10, 1, null, null, null, null, null);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("getWorksCountByTag 计数")
        void getWorksCountByTag() {
            AsmrWork w = createWork("RJ1", "A", "/a/RJ1", "/a");
            w.setTags("催眠,ASMR");
            dao.addWork(w);
            assertEquals(1, dao.getWorksCountByTag("催眠", null, null, null));
        }

        @Test
        @DisplayName("getWorksByCircle LIKE 查询")
        void getWorksByCircle_likeQuery() {
            AsmrWork w1 = createWork("RJ1", "A", "/a/RJ1", "/a");
            w1.setCircle("社团X");
            AsmrWork w2 = createWork("RJ2", "B", "/a/RJ2", "/a");
            w2.setCircle("社团Y");
            dao.addWork(w1);
            dao.addWork(w2);
            List<AsmrWork> result = dao.getWorksByCircle("社团X", 1, 10, 1, null, null, null, null, null);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("getWorksCountByCircle 计数")
        void getWorksCountByCircle() {
            AsmrWork w = createWork("RJ1", "A", "/a/RJ1", "/a");
            w.setCircle("社团X");
            dao.addWork(w);
            assertEquals(1, dao.getWorksCountByCircle("社团X", null, null, null));
        }

        @Test
        @DisplayName("getWorksByVa LIKE 查询")
        void getWorksByVa_likeQuery() {
            AsmrWork w1 = createWork("RJ1", "A", "/a/RJ1", "/a");
            w1.setVas("声优A,声优B");
            AsmrWork w2 = createWork("RJ2", "B", "/a/RJ2", "/a");
            w2.setVas("声优C");
            dao.addWork(w1);
            dao.addWork(w2);
            List<AsmrWork> result = dao.getWorksByVa("声优A", 1, 10, 1, null, null, null, null, null);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("getWorksCountByVa 计数")
        void getWorksCountByVa() {
            AsmrWork w = createWork("RJ1", "A", "/a/RJ1", "/a");
            w.setVas("声优A,声优B");
            dao.addWork(w);
            assertEquals(1, dao.getWorksCountByVa("声优A", null, null, null));
        }

        @Test
        @DisplayName("searchWorks 按标题匹配")
        void searchWorks_byTitle() {
            dao.addWork(createWork("RJ1", "催眠ASMR", "/a/RJ1", "/a"));
            dao.addWork(createWork("RJ2", "恐怖故事", "/a/RJ2", "/a"));
            List<AsmrWork> result = dao.searchWorks("催眠", 1, 10, 1, null, null, null, null, null);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("searchWorks 按 RJ 号匹配")
        void searchWorks_byWorkId() {
            dao.addWork(createWork("RJ123456", "作品", "/a/RJ123456", "/a"));
            List<AsmrWork> result = dao.searchWorks("123456", 1, 10, 1, null, null, null, null, null);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("searchWorks 按标签匹配")
        void searchWorks_byTag() {
            AsmrWork w = createWork("RJ1", "作品A", "/a/RJ1", "/a");
            w.setTags("催眠");
            dao.addWork(w);
            List<AsmrWork> result = dao.searchWorks("催眠", 1, 10, 1, null, null, null, null, null);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("searchWorks 按社团匹配")
        void searchWorks_byCircle() {
            AsmrWork w = createWork("RJ1", "作品A", "/a/RJ1", "/a");
            w.setCircle("星空社团");
            dao.addWork(w);
            List<AsmrWork> result = dao.searchWorks("星空", 1, 10, 1, null, null, null, null, null);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("searchWorks 含 % 通配符正确转义")
        void searchWorks_escapePercent() {
            AsmrWork w1 = createWork("RJ1", "100%还原", "/a/RJ1", "/a");
            dao.addWork(w1);
            dao.addWork(createWork("RJ2", "普通作品", "/a/RJ2", "/a"));
            List<AsmrWork> result = dao.searchWorks("100%", 1, 10, 1, null, null, null, null, null);
            assertEquals(1, result.size());
            assertEquals("RJ1", result.get(0).getWorkId());
        }

        @Test
        @DisplayName("searchWorksCount 计数")
        void searchWorksCount() {
            dao.addWork(createWork("RJ1", "催眠ASMR", "/a/RJ1", "/a"));
            dao.addWork(createWork("RJ2", "恐怖故事", "/a/RJ2", "/a"));
            assertEquals(1, dao.searchWorksCount("催眠", null, null, null));
        }

        @Test
        @DisplayName("getWorksBySubtitle 有字幕")
        void getWorksBySubtitle_hasSubtitle() {
            AsmrWork w1 = createWork("RJ1", "A", "/a/RJ1", "/a");
            w1.setSubtitle(true);
            AsmrWork w2 = createWork("RJ2", "B", "/a/RJ2", "/a");
            w2.setSubtitle(false);
            dao.addWork(w1);
            dao.addWork(w2);
            List<AsmrWork> result = dao.getWorksBySubtitle(true, 1, 10);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("getWorksBySubtitleCount 计数")
        void getWorksBySubtitleCount() {
            AsmrWork w = createWork("RJ1", "A", "/a/RJ1", "/a");
            w.setSubtitle(true);
            dao.addWork(w);
            assertEquals(1, dao.getWorksBySubtitleCount(true));
        }

        @Test
        @DisplayName("getWorkByFilepath 存在返回作品")
        void getWorkByFilepath_exists() {
            dao.addWork(createWork("RJ1", "测试", "/audio/RJ1", "/audio"));
            AsmrWork result = dao.getWorkByFilepath("/audio/RJ1");
            assertNotNull(result);
            assertEquals("RJ1", result.getWorkId());
        }

        @Test
        @DisplayName("getWorkByFilepath 不存在返回 null")
        void getWorkByFilepath_notExists() {
            assertNull(dao.getWorkByFilepath("/nonexistent"));
        }

        @Test
        @DisplayName("getWorkById 存在返回作品")
        void getWorkById_exists() {
            AsmrWork work = insertAndGet(createWork("RJ1", "测试", "/a/RJ1", "/a"));
            AsmrWork result = dao.getWorkById(work.getId());
            assertNotNull(result);
            assertEquals("RJ1", result.getWorkId());
        }

        @Test
        @DisplayName("getWorkById 不存在返回 null")
        void getWorkById_notExists() {
            assertNull(dao.getWorkById(99999));
        }

        @Test
        @DisplayName("getWorkByTitle 存在返回作品")
        void getWorkByTitle_exists() {
            dao.addWork(createWork("RJ1", "测试标题", "/a/RJ1", "/a"));
            AsmrWork result = dao.getWorkByTitle("测试标题");
            assertNotNull(result);
            assertEquals("RJ1", result.getWorkId());
        }

        @Test
        @DisplayName("getWorkByTitle 不存在返回 null")
        void getWorkByTitle_notExists() {
            assertNull(dao.getWorkByTitle("不存在的标题"));
        }
    }

    // ==================== 更新操作 ====================

    @Nested
    @DisplayName("更新操作")
    class UpdateTests {

        @Test
        @DisplayName("updateFavorite 切换收藏")
        void updateFavorite_toggle() {
            AsmrWork work = insertAndGet(createWork("RJ1", "A", "/a/RJ1", "/a"));
            assertTrue(dao.updateFavorite(work.getId(), true));
            AsmrWork result = dao.getWorkById(work.getId());
            assertTrue(result.getFavorites());
        }

        @Test
        @DisplayName("updateWorkRateById 更新评分")
        void updateWorkRateById() {
            AsmrWork work = insertAndGet(createWork("RJ1", "A", "/a/RJ1", "/a"));
            assertTrue(dao.updateWorkRateById(work.getId(), 5.0));
            AsmrWork result = dao.getWorkById(work.getId());
            assertEquals(5.0, result.getRate());
        }

        @Test
        @DisplayName("updateWorkById 更新所有非空字段")
        void updateWorkById_allFields() {
            AsmrWork work = insertAndGet(createWork("RJ1", "原标题", "/a/RJ1", "/a"));
            work.setTitle("更新标题");
            work.setCircle("新社团");
            work.setRate(3.5);
            work.setTags("新标签");
            assertTrue(dao.updateWorkById(work));
            AsmrWork result = dao.getWorkById(work.getId());
            assertEquals("更新标题", result.getTitle());
            assertEquals("新社团", result.getCircle());
            assertEquals(3.5, result.getRate());
            assertEquals("新标签", result.getTags());
        }

        @Test
        @DisplayName("updateWorkById 只更新部分字段，其余不变")
        void updateWorkById_partialFields() {
            AsmrWork work = insertAndGet(createWork("RJ1", "原标题", "/a/RJ1", "/a"));
            work.setTitle("新标题");
            work.setCircle(null); // null 字段不更新
            assertTrue(dao.updateWorkById(work));
            AsmrWork result = dao.getWorkById(work.getId());
            assertEquals("新标题", result.getTitle());
            assertEquals("社团A", result.getCircle()); // 保持原值
        }

        @Test
        @DisplayName("updateWorkById 全部 null 返回 false")
        void updateWorkById_allNull_returnsFalse() {
            AsmrWork work = insertAndGet(createWork("RJ1", "A", "/a/RJ1", "/a"));
            AsmrWork empty = new AsmrWork();
            empty.setId(work.getId());
            assertFalse(dao.updateWorkById(empty));
        }

        @Test
        @DisplayName("updateWorkTag 更新标签")
        void updateWorkTag() {
            AsmrWork work = insertAndGet(createWork("RJ1", "A", "/a/RJ1", "/a"));
            assertTrue(dao.updateWorkTag(work.getId(), "新标签"));
            AsmrWork result = dao.getWorkById(work.getId());
            assertEquals("新标签", result.getTags());
        }

        @Test
        @DisplayName("updateWorkTitleCN 只更新中文标题")
        void updateWorkTitleCN_updatesOnlyChineseTitle() {
            AsmrWork work = insertAndGet(createWork("RJ1", "原标题", "/a/RJ1", "/a"));

            assertTrue(dao.updateWorkTitleCN(work.getId(), "AI 翻译标题"));

            AsmrWork result = dao.getWorkById(work.getId());
            assertEquals("原标题", result.getTitle());
            assertEquals("AI 翻译标题", result.getTitleCN());
            assertEquals("/a/RJ1", result.getFilepath());
        }

        @Test
        @DisplayName("updateWorkFilePathById 更新路径")
        void updateWorkFilePathById() {
            AsmrWork work = insertAndGet(createWork("RJ1", "A", "/a/RJ1", "/a"));
            work.setFilepath("/new/path/RJ1");
            assertTrue(dao.updateWorkFilePathById(work));
            assertNotNull(dao.getWorkByFilepath("/new/path/RJ1"));
        }

        @Test
        @DisplayName("updateWorkFilePathAndParentById 更新路径和父路径")
        void updateWorkFilePathAndParentById() {
            AsmrWork work = insertAndGet(createWork("RJ1", "A", "/a/RJ1", "/a"));
            work.setFilepath("/new/path/RJ1");
            work.setParentPath("/new/path");
            assertTrue(dao.updateWorkFilePathAndParentById(work));
            AsmrWork result = dao.getWorkByFilepath("/new/path/RJ1");
            assertEquals("/new/path", result.getParentPath());
        }

    }

    // ==================== 删除操作 ====================

    @Nested
    @DisplayName("删除操作")
    class DeleteTests {

        @Test
        @DisplayName("deleteWorkByByParentFolder 删除关联作品")
        void deleteByParentFolder_deletesWorks() {
            dao.addWork(createWork("RJ1", "A", "/audio/RJ1", "/audio"));
            dao.addWork(createWork("RJ2", "B", "/audio/RJ2", "/audio"));
            dao.addWork(createWork("RJ3", "C", "/video/RJ3", "/video"));
            assertTrue(dao.deleteWorkByByParentFolder("/audio"));
            assertEquals(1, dao.getAllWorks().size());
        }

        @Test
        @DisplayName("deleteWorkByByParentFolder 路径归一化 - 反斜杠")
        void deleteByParentFolder_normalizeBackslash() {
            dao.addWork(createWork("RJ1", "A", "/audio/RJ1", "/audio"));
            assertTrue(dao.deleteWorkByByParentFolder("\\audio"));
            assertEquals(0, dao.getAllWorks().size());
        }

        @Test
        @DisplayName("deleteWorkByByParentFolder 路径归一化 - 尾斜杠")
        void deleteByParentFolder_normalizeTrailingSlash() {
            dao.addWork(createWork("RJ1", "A", "/audio/RJ1", "/audio"));
            assertTrue(dao.deleteWorkByByParentFolder("/audio/"));
            assertEquals(0, dao.getAllWorks().size());
        }

        @Test
        @DisplayName("按主键删除作品")
        void deleteByRecordId() {
            dao.addWork(createWork("RJ1", "A", "/audio/RJ1", "/audio"));
            int id = dao.getWorkByFilepath("/audio/RJ1").getId();
            assertEquals(1, dao.deleteWorksByIds(List.of(id)));
            assertNull(dao.getWorkByFilepath("/audio/RJ1"));
        }

        @Test
        @DisplayName("按主键删除不存在的作品返回零")
        void deleteByRecordId_notExists() {
            assertEquals(0, dao.deleteWorksByIds(List.of(Integer.MAX_VALUE)));
        }
    }
}
