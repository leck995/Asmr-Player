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
 * 社团字典表访问，操作 {@code asmr_circles}（仅 id 与唯一 name 两列）。
 * <p>
 * 作品与社团的关联靠 {@code asmr_works.circle} 文本冗余字段，删除社团不会改动作品记录。
 *
 * @author Leck
 */
public class WorkCircleDao extends BaseDao {
    private static final Logger LOG = LoggerFactory.getLogger(WorkCircleDao.class);

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
     * 按名称去重插入社团（名称已存在时不写入）。
     *
     * @return 是否新插入了一行，已存在时为 false
     */
    public boolean addCircle(String name){
        String sql="INSERT INTO asmr_circles (name) SELECT  ? WHERE NOT EXISTS (SELECT * FROM asmr_circles where name=?)";
        try {
            QueryRunner queryRunner=new QueryRunner();
            int update = queryRunner.update(getConn(), sql, name ,name);
            return update > 0;
        } catch (SQLException e) {
            throw new DataAccessException("添加社团失败: " + name, e);
        }finally {
            close();
        }
    }

    /**
     * 按名称精确删除社团，名称不存在时返回 false。
     */
    public boolean deleteCircleByName(String name){
        String sql=" DELETE FROM asmr_circles WHERE name=?";
        try {
            QueryRunner queryRunner=new QueryRunner();
            int update = queryRunner.update(getConn(), sql, name);
            return update > 0;
        } catch (SQLException e) {
            throw new DataAccessException("删除社团失败: " + name, e);
        }finally {
            close();
        }
    }
    /**
     * 查询全部社团及其关联作品数，按数量倒序。
     * <p>
     * 作品数按 {@code asmr_works.circle LIKE '%名称%'} 模糊统计。
     *
     * @return 全部社团，无数据时为空列表
     */
    public List<Actor> getAllCircles(){
        String sql="SELECT a.id, a.name, COUNT(b.circle) AS num FROM asmr_circles AS a LEFT JOIN asmr_works AS b ON b.circle LIKE '%' || a.name || '%' GROUP BY a.id, a.name ORDER BY num DESC";

        QueryRunner queryRunner=new QueryRunner();
        try {
            return queryRunner.query(getConn(),sql,new BeanListHandler<>(Actor.class,getRowProcessor()));
        } catch (SQLException e) {
            throw new DataAccessException("查询全部社团失败", e);
        }finally {
            close();
        }
    }



}