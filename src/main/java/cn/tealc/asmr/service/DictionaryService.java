package cn.tealc.asmr.service;

import cn.tealc.asmr.dao.WorkCategoryDao;
import cn.tealc.asmr.dao.WorkCircleDao;
import cn.tealc.asmr.dao.WorkTagDao;
import cn.tealc.asmr.dao.WorkVaDao;
import cn.tealc.asmr.di.Inject;
import cn.tealc.asmr.model.Actor;

import java.util.List;

/**
 * 字典表服务。
 * <p>
 * 统一封装分类、声优、社团、标签四张字典表的增删查操作，
 * 使 ViewModel/Controller/Task 不再直接实例化
 * {@link WorkCategoryDao} / {@link WorkVaDao} / {@link WorkCircleDao} / {@link WorkTagDao}。
 *
 * @author Leck
 */
public class DictionaryService {

    @Inject
    private WorkCategoryDao categoryDao;

    @Inject
    private WorkVaDao vaDao;

    @Inject
    private WorkCircleDao circleDao;

    @Inject
    private WorkTagDao tagDao;

    /*===============分类======================*/

    public boolean addCategory(String name) {
        return categoryDao.addCategory(name);
    }

    public boolean deleteCategoryByName(String name) {
        return categoryDao.deleteCategoryByName(name);
    }

    public List<Actor> getAllCategories() {
        return categoryDao.getAllCategories();
    }

    /*===============声优======================*/

    public boolean addVa(String name) {
        return vaDao.addVa(name);
    }

    public boolean deleteVaByName(String name) {
        return vaDao.deleteVaByName(name);
    }

    public List<Actor> getAllVas() {
        return vaDao.getAllVas();
    }

    /*===============社团======================*/

    public boolean addCircle(String name) {
        return circleDao.addCircle(name);
    }

    public boolean deleteCircleByName(String name) {
        return circleDao.deleteCircleByName(name);
    }

    public List<Actor> getAllCircles() {
        return circleDao.getAllCircles();
    }

    /*===============标签======================*/

    public boolean addTag(String name) {
        return tagDao.addTag(name);
    }

    public boolean addTag(String name, String description) {
        return tagDao.addTag(name, description);
    }

    public boolean updateTagDescription(int id, String description) {
        return tagDao.updateTagDescription(id, description);
    }

    public boolean deleteTagByName(String name) {
        return tagDao.deleteTagByName(name);
    }

    public List<Actor> getAllTags() {
        return tagDao.getAllTags();
    }

    /**
     * 最近修改的标签，用于右键菜单快捷入口。
     *
     * @param limit 最多返回多少条
     */
    public List<Actor> getRecentTags(int limit) {
        return tagDao.getRecentTags(limit);
    }
}
