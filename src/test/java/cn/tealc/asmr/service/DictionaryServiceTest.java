package cn.tealc.asmr.service;

import cn.tealc.asmr.BaseDatabaseTest;
import cn.tealc.asmr.dao.WorkCategoryDao;
import cn.tealc.asmr.dao.WorkCircleDao;
import cn.tealc.asmr.dao.WorkTagDao;
import cn.tealc.asmr.dao.WorkVaDao;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.Actor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DictionaryService} 单元测试。
 * <p>
 * 覆盖分类、声优、社团、标签四张字典表的增删查。
 *
 * @author Leck
 */
class DictionaryServiceTest extends BaseDatabaseTest {

    private DictionaryService service() {
        DI.bind(WorkCategoryDao.class, WorkCategoryDao::new);
        DI.bind(WorkVaDao.class, WorkVaDao::new);
        DI.bind(WorkCircleDao.class, WorkCircleDao::new);
        DI.bind(WorkTagDao.class, WorkTagDao::new);
        return DI.get(DictionaryService.class);
    }

    // ==================== 分类 ====================

    @Test
    @DisplayName("addCategory + getAllCategories：添加后能查回")
    void category_addAndGetAll() {
        DictionaryService svc = service();
        assertTrue(svc.addCategory("催眠"));
        assertTrue(svc.addCategory("ASMR"));

        List<Actor> list = svc.getAllCategories();
        assertEquals(2, list.size());
        assertTrue(list.stream().anyMatch(a -> a.getName().equals("催眠")));
        assertTrue(list.stream().anyMatch(a -> a.getName().equals("ASMR")));
    }

    @Test
    @DisplayName("addCategory 去重：重复添加同名分类不报错")
    void category_addDuplicate() {
        DictionaryService svc = service();
        svc.addCategory("催眠");
        // 第二次添加同名，应返回 false（INSERT OR IGNORE）
        assertFalse(svc.addCategory("催眠"));
        assertEquals(1, svc.getAllCategories().size());
    }

    @Test
    @DisplayName("deleteCategoryByName：删除后查不到")
    void category_deleteByName() {
        DictionaryService svc = service();
        svc.addCategory("待删除分类");
        assertEquals(1, svc.getAllCategories().size());

        boolean ok = svc.deleteCategoryByName("待删除分类");
        assertTrue(ok);
        assertEquals(0, svc.getAllCategories().size());
    }

    // ==================== 声优 ====================

    @Test
    @DisplayName("addVa + getAllVas：添加后能查回")
    void va_addAndGetAll() {
        DictionaryService svc = service();
        assertTrue(svc.addVa("声优A"));
        assertTrue(svc.addVa("声优B"));

        List<Actor> list = svc.getAllVas();
        assertEquals(2, list.size());
    }

    @Test
    @DisplayName("deleteVaByName：删除后查不到")
    void va_deleteByName() {
        DictionaryService svc = service();
        svc.addVa("待删除声优");
        assertEquals(1, svc.getAllVas().size());

        assertTrue(svc.deleteVaByName("待删除声优"));
        assertEquals(0, svc.getAllVas().size());
    }

    // ==================== 社团 ====================

    @Test
    @DisplayName("addCircle + getAllCircles：添加后能查回")
    void circle_addAndGetAll() {
        DictionaryService svc = service();
        assertTrue(svc.addCircle("社团X"));
        assertTrue(svc.addCircle("社团Y"));

        List<Actor> list = svc.getAllCircles();
        assertEquals(2, list.size());
    }

    @Test
    @DisplayName("deleteCircleByName：删除后查不到")
    void circle_deleteByName() {
        DictionaryService svc = service();
        svc.addCircle("待删除社团");
        assertEquals(1, svc.getAllCircles().size());

        assertTrue(svc.deleteCircleByName("待删除社团"));
        assertEquals(0, svc.getAllCircles().size());
    }

    // ==================== 标签 ====================

    @Test
    @DisplayName("addTag + getAllTags：添加后能查回")
    void tag_addAndGetAll() {
        DictionaryService svc = service();
        assertTrue(svc.addTag("标签1"));
        assertTrue(svc.addTag("标签2"));

        List<Actor> list = svc.getAllTags();
        assertEquals(2, list.size());
    }

    @Test
    @DisplayName("addTag 保存描述，updateTagDescription 更新描述")
    void tag_descriptionAndUpdate() {
        DictionaryService svc = service();
        assertTrue(svc.addTag("标签1", "描述1"));

        Actor tag = svc.getAllTags().get(0);
        assertEquals("描述1", tag.getDescription());
        assertFalse(tag.getCreateDateTime().isBlank());

        assertTrue(svc.updateTagDescription(tag.getId(), "描述2"));
        assertEquals("描述2", svc.getAllTags().get(0).getDescription());
    }

    @Test
    @DisplayName("deleteTagByName：删除后查不到")
    void tag_deleteByName() {
        DictionaryService svc = service();
        svc.addTag("待删除标签");
        assertEquals(1, svc.getAllTags().size());

        assertTrue(svc.deleteTagByName("待删除标签"));
        assertEquals(0, svc.getAllTags().size());
    }
}
