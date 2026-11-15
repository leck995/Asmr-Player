package cn.tealc.asmr.dao;

import cn.tealc.asmr.model.GalgameSaveData;
import org.apache.commons.dbutils.BasicRowProcessor;
import org.apache.commons.dbutils.BeanProcessor;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.RowProcessor;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Galgame 存档数据访问，操作 {@code asmr_galgame_save} 表。
 * <p>
 * 每个方法自取连接、自动提交，单次调用即一个事务。
 * 异常统一包装为 {@link DataAccessException} 抛出。
 *
 * @author Leck
 */
public class SaveDataDao extends BaseDao {
    private static final Logger LOG = LoggerFactory.getLogger(BaseDao.class);

    /**
     * 列名到 {@link GalgameSaveData} 属性的映射，其中 {@code save_time} 列映射到 {@code time} 属性。
     */
    @Override
    public RowProcessor getRowProcessor() {
        Map<String,String> map=new HashMap<>();
        map.put("id","id");
        map.put("asmr_id","asmrId");
        map.put("title","title");
        map.put("chapter","chapter");
        map.put("filepath","filepath");
        map.put("thumb","thumb");
        map.put("background","background");
        map.put("save_time","time");
        map.put("text","text");
        map.put("save_date","saveDate");
        BeanProcessor beanProcessor=new BeanProcessor(map);
        return new BasicRowProcessor(beanProcessor);
    }

    /**
     * 插入一条存档记录（自动提交）。
     *
     * @param saveData 存档数据，其中 time 为播放进度，单位毫秒
     * @return 是否插入成功
     */
    public boolean addSave(GalgameSaveData saveData){
        String sql="INSERT INTO asmr_galgame_save (title,chapter,filepath,thumb,background,save_time,text,save_date,asmr_id)\n" +
                "        values  (?,?,?,?,?,?,?,?,?)";
        try {
            QueryRunner qr=new QueryRunner();
            int update = qr.update(getConn(), sql,
                    saveData.getTitle(), saveData.getChapter(), saveData.getFilepath(),
                    saveData.getThumb(), saveData.getBackground(), saveData.getTime(),
                    saveData.getText(), saveData.getSaveDate(), saveData.getAsmrId());
           return update > 0;
        } catch (SQLException e) {
            throw new DataAccessException("添加存档失败", e);
        }finally {
            close();
        }

    }
    /**
     * 按主键删除存档。
     *
     * @return 是否删除了记录，ID 不存在时返回 false
     */
    public boolean deleteSaveById(int id){
        String sql="DELETE FROM asmr_galgame_save WHERE id= ? ";
        try {
            QueryRunner qr=new QueryRunner();
            int update = qr.update(getConn(), sql, id);
            return update > 0;
        } catch (SQLException e) {
            throw new DataAccessException("删除存档失败: " + id, e);
        }finally {
            close();
        }
    }


    /**
     * 分页查询存档列表，无排序保证。
     *
     * @param index 页码，从 1 开始
     * @param size  每页条数
     * @return 当前页存档，无结果时为空列表
     */
    public List<GalgameSaveData> getAllSaves(int index,int size){
        String sql=" SELECT * FROM asmr_galgame_save  limit ?,?";
        try {
            QueryRunner qr=new QueryRunner();
            return qr.query(getConn(),sql,new BeanListHandler<>(GalgameSaveData.class,getRowProcessor()),(index-1)*size,size);
        } catch (SQLException e) {
            throw new DataAccessException("查询全部存档失败", e);
        }finally {
            close();
        }
    }

    /**
     * 统计存档总数。
     * <p>
     * 注意：本方法没有像其他方法那样在 finally 中调用 {@link #close()}，
     * 当前线程的连接会一直保留到同一 DAO 实例的下一个方法释放它为止。
     *
     * @return 存档总数（count 查询不会为 null）
     */
    public Integer getAllSavesCount(){
        String sql=" select count(*) from asmr_galgame_save";
        try {
            QueryRunner qr=new QueryRunner();
            Integer count = qr.query(getConn(), sql, new ScalarHandler<>());
            return count;
        } catch (SQLException e) {
            throw new DataAccessException("查询存档总数失败", e);
        }
    }
}
