package cn.tealc.asmr.dao;

import cn.tealc.asmr.model.Actor;
import org.apache.commons.dbutils.BasicRowProcessor;
import org.apache.commons.dbutils.BeanProcessor;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.RowProcessor;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 标签字典表访问，操作 {@code asmr_tags}。
 * <p>
 * 与分类/社团表相比多了 {@code description} 与创建、更新时间的维护；
 * 作品与标签的关联仍靠 {@code asmr_works.tags} 文本冗余字段，删除标签不会改动作品记录。
 *
 * @author Leck
 */
public class WorkTagDao extends BaseDao {

    private static final Logger LOG = LoggerFactory.getLogger(WorkTagDao.class);

    /**
     * 列名到 {@link Actor} 属性的映射，除 id/name/num 外还包含描述与时间戳字段。
     */
    @Override
    public RowProcessor getRowProcessor() {
        Map<String,String> map=new HashMap<>();
        map.put("id","id");
        map.put("name","name");
        map.put("num","num");
        map.put("description","description");
        map.put("create_date_time","createDateTime");
        map.put("update_date_time","updateDateTime");
        BeanProcessor beanProcessor=new BeanProcessor(map);
        return new BasicRowProcessor(beanProcessor);
    }

    /**
     * 按名称去重插入标签，描述为空串。
     */
    public boolean addTag(String name){
        return addTag(name, "");
    }

    /**
     * 按名称去重插入标签（名称已存在时不写入），创建与更新时间取本地当前时间。
     *
     * @return 是否新插入了一行，已存在时为 false
     */
    public boolean addTag(String name, String description){
        String sql = "INSERT INTO asmr_tags (name, description, create_date_time, update_date_time) " +
                "SELECT ?, ?, datetime('now','localtime'), datetime('now','localtime') " +
                "WHERE NOT EXISTS (SELECT 1 FROM asmr_tags WHERE name = ?)";
        try {
            QueryRunner queryRunner=new QueryRunner();
            int update = queryRunner.update(getConn(), sql, name, description, name);
            return update > 0;
        } catch (SQLException e) {
            throw new DataAccessException("添加标签失败: " + name, e);
        }finally {
            close();
        }
    }

    /**
     * 更新标签描述，并刷新 {@code update_date_time} 为本地当前时间。
     *
     * @return 是否更新了记录，ID 不存在时为 false
     */
    public boolean updateTagDescription(int id, String description) {
        String sql = "UPDATE asmr_tags SET description = ?, update_date_time = datetime('now','localtime') WHERE id = ?";
        try {
            QueryRunner queryRunner = new QueryRunner();
            return queryRunner.update(getConn(), sql, description, id) > 0;
        } catch (SQLException e) {
            throw new DataAccessException("更新标签描述失败: " + id, e);
        } finally {
            close();
        }
    }
    /**
     * 按名称精确删除标签，名称不存在时返回 false。
     */
    public boolean deleteTagByName(String name){
        String sql=" DELETE FROM asmr_tags WHERE name=?";
        try {
            QueryRunner queryRunner=new QueryRunner();
            int update = queryRunner.update(getConn(), sql, name);
            return update > 0;
        } catch (SQLException e) {
            throw new DataAccessException("删除标签失败: " + name, e);
        }finally {
            close();
        }
    }

    /**
     * 查询全部标签及其关联作品数，按数量倒序。
     * <p>
     * 作品数按 {@code asmr_works.tags LIKE '%名称%'} 模糊统计。
     *
     * @return 全部标签，无数据时为空列表
     */
    public List<Actor> getAllTags(){
        String sql="SELECT a.id, a.name, a.description, a.create_date_time, a.update_date_time, " +
                "(SELECT count(*) FROM asmr_works WHERE tags LIKE '%'||a.name||'%') AS num " +
                "FROM asmr_tags AS a ORDER BY num DESC";
        QueryRunner queryRunner=new QueryRunner();
        try {
            return queryRunner.query(getConn(),sql,new BeanListHandler<>(Actor.class,getRowProcessor()));
        } catch (SQLException e) {
            throw new DataAccessException("查询全部标签失败", e);
        }finally {
            close();
        }
    }

    /**
     * 按最近修改时间倒序查询标签，用于右键菜单的快捷入口。
     *
     * @param limit 最多返回多少条
     * @return 最近修改的标签
     */
    public List<Actor> getRecentTags(int limit) {
        String sql = "SELECT a.id, a.name, a.description, a.create_date_time, a.update_date_time, " +
                "(SELECT count(*) FROM asmr_works WHERE tags LIKE '%'||a.name||'%') AS num " +
                "FROM asmr_tags AS a ORDER BY a.update_date_time DESC, a.id DESC LIMIT ?";
        QueryRunner queryRunner = new QueryRunner();
        try {
            return queryRunner.query(getConn(), sql,
                    new BeanListHandler<>(Actor.class, getRowProcessor()), limit);
        } catch (SQLException e) {
            throw new DataAccessException("查询最近标签失败", e);
        } finally {
            close();
        }
    }
}