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
 * 分类字典表访问，操作 {@code asmr_categories}（仅 id 与唯一 name 两列）。
 * <p>
 * 作品与分类的关联靠 {@code asmr_works.categories} 文本冗余字段，删除分类不会改动作品记录。
 *
 * @author Leck
 */
public class WorkCategoryDao extends BaseDao {
    private static final Logger LOG = LoggerFactory.getLogger(WorkCategoryDao.class);

    /**
     * 列名到 {@link Actor} 属性的映射（id/name/num）。
     */
    @Override
    public RowProcessor getRowProcessor() {
        Map<String,String> map=new HashMap<>();
        map.put("id","id");
        map.put("name","name");
        map.put("num","num");
        BeanProcessor beanProcessor=new BeanProcessor(map);
        return new BasicRowProcessor(beanProcessor);
    }


    /**
     * 按名称去重插入分类（名称已存在时不写入）。
     *
     * @return 是否新插入了一行，已存在时为 false
     */
    public boolean addCategory(String name){
        String sql="INSERT INTO asmr_categories (name) SELECT ? WHERE NOT EXISTS (SELECT * FROM asmr_categories where name=?)";
        try {
            QueryRunner queryRunner=new QueryRunner();
            int update = queryRunner.update(getConn(), sql, name, name);
            return update > 0;
        } catch (SQLException e) {
            throw new DataAccessException("添加分类失败: " + name, e);
        }finally {
            close();
        }
    }
    /**
     * 按名称精确删除分类，名称不存在时返回 false。
     */
    public boolean deleteCategoryByName(String name){
        String sql=" DELETE FROM asmr_categories WHERE name=?";
        try {
            QueryRunner queryRunner=new QueryRunner();
            int update = queryRunner.update(getConn(), sql, name);
            return update > 0;
        } catch (SQLException e) {
            throw new DataAccessException("删除分类失败: " + name, e);
        }finally {
            close();
        }
    }
    /**
     * 查询全部分类及其关联作品数，按数量倒序。
     * <p>
     * 作品数按 {@code asmr_works.categories LIKE '%名称%'} 模糊统计，
     * 名称互为子串的分类会重复计数。
     *
     * @return 全部分类，无数据时为空列表
     */
    public List<Actor> getAllCategories(){
        String sql="SELECT a.id, a.name, COUNT(b.id) AS num FROM asmr_categories AS a LEFT JOIN asmr_works AS b ON b.categories LIKE '%' || a.name || '%' GROUP BY a.id, a.name ORDER BY num DESC";
        QueryRunner queryRunner=new QueryRunner();
        try {
            return queryRunner.query(getConn(),sql,new BeanListHandler<>(Actor.class,getRowProcessor()));
        } catch (SQLException e) {
            throw new DataAccessException("查询全部分类失败", e);
        }finally {
            close();
        }
    }



}