package cn.tealc.asmr.dao;

import cn.tealc.asmr.model.Actor;
import org.apache.commons.dbutils.*;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 声优字典表访问，操作 {@code asmr_vas}（仅 id 与唯一 name 两列）。
 * <p>
 * 作品与声优的关联靠 {@code asmr_works.vas} 文本冗余字段，删除声优不会改动作品记录。
 *
 * @author Leck
 */
public class WorkVaDao extends BaseDao {
    private static final Logger LOG = LoggerFactory.getLogger(WorkVaDao.class);

    /**
     * 列名到 {@link Actor} 属性的映射（id/name/num）。
     */
    public RowProcessor getRowProcessor(){
        Map<String,String> map=new HashMap<>();
        map.put("id","id");
        map.put("name","name");
        map.put("num","num");
        BeanProcessor beanProcessor=new BeanProcessor(map);
        return new BasicRowProcessor(beanProcessor);
    }


    /**
     * 按名称去重插入声优（名称已存在时不写入）。
     * <p>
     * 与分类/社团不同，本方法经 {@code QueryRunner.insert} 以生成键判断结果，
     * 名称已存在时的返回值取决于驱动行为，调用方不应将其当作可靠的"是否新插入"信号。
     *
     * @return 大致表示是否插入成功，重名场景下不严格
     */
    public boolean addVa(String name){
        String sql="INSERT INTO asmr_vas (name) SELECT ? WHERE NOT EXISTS (SELECT * FROM asmr_vas where name=?)";
        QueryRunner qr=new QueryRunner();
        Connection conn = getConn();
        try {
            ResultSetHandler<Integer> rsh = new ScalarHandler<Integer>();
            Integer insert = qr.insert(conn, sql, rsh, name, name);
            return insert > 0;
        } catch (SQLException e) {
            throw new DataAccessException("添加声优失败: " + name, e);
        }finally{
            close();
        }
    }
    /**
     * 按名称精确删除声优，名称不存在时返回 false。
     */
    public boolean deleteVaByName(String name){
        String sql=" DELETE FROM asmr_vas WHERE name=?";
        QueryRunner qr=new QueryRunner();
        Connection conn = getConn();
        try {
            int update = qr.update(conn, sql, name);
            return update > 0;
        } catch (SQLException e) {
            throw new DataAccessException("删除声优失败: " + name, e);
        }finally {
            close();
        }
    }


    /**
     * 查询全部声优及其关联作品数，按数量倒序。
     * <p>
     * 作品数按 {@code asmr_works.vas LIKE '%名称%'} 模糊统计。
     *
     * @return 全部声优，无数据时为空列表
     */
    public List<Actor> getAllVas(){
        QueryRunner qr=new QueryRunner();
        String sql="SELECT a.id, a.name, COUNT(b.id) AS num FROM asmr_vas AS a LEFT JOIN asmr_works AS b ON b.vas LIKE '%' || a.name || '%' GROUP BY a.id, a.name ORDER BY num DESC";
        try {
            return qr.query(getConn(),sql,new BeanListHandler<>(Actor.class,getRowProcessor()));
        } catch (SQLException e) {
            throw new DataAccessException("查询全部声优失败", e);
        }finally {
            close();
        }
    }

}