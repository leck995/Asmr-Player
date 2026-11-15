package cn.tealc.asmr.dao.collection;

import cn.tealc.asmr.dao.BaseDao;
import cn.tealc.asmr.dao.DataAccessException;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.collection.ItemData;
import cn.tealc.asmr.model.collection.ItemDataEx;
import org.apache.commons.dbutils.*;
import org.apache.commons.dbutils.handlers.BeanHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 收藏条目数据访问，操作 {@code asmr_collect_item} 表。
 * <p>
 * 条目以 {@code work_id} 关联 {@code asmr_works}、以 {@code sheet_id} 关联
 * {@code asmr_collect_sheet}，两个外键均为 ON DELETE CASCADE（依赖连接上开启的
 * {@code PRAGMA foreign_keys}），因此无需手动清理悬挂条目。
 * 每个方法自取连接、自动提交；列表查询无结果时返回空列表。
 *
 * @author Leck
 */
public class CollectionItemDao extends BaseDao {
    private static final Logger LOG = LoggerFactory.getLogger(CollectionItemDao.class);

    /**
     * {@code asmr_collect_item} 列名到 {@link ItemData} 属性的映射。
     */
    @Override
    public RowProcessor getRowProcessor() {
        Map<String, String> columnToProperty = new HashMap<>();
        columnToProperty.put("id", "id");
        columnToProperty.put("item_name", "name");
        columnToProperty.put("filepath", "filepath");
        columnToProperty.put("work_id", "workId");
        columnToProperty.put("sheet_id", "sheetId");

        return new BasicRowProcessor(new BeanProcessor(columnToProperty));
    }

    /**
     * 联表查询用的列映射：{@code asmr_collect_item} 基础列映射到 {@link ItemDataEx}，
     * {@code w.} 前缀的 {@code asmr_works} 列映射到其内嵌 work 属性。
     * <p>
     * 当前代码未调用此映射器，{@link #getItemsWithWorkInfoBySheet} 走的是
     * 手动取列的 {@link ItemDataExListHandler}。
     */
    public RowProcessor getRowProcessorEX() {
        Map<String, String> columnToProperty = new HashMap<>();
        // ItemData 基础字段映射 (表: asmr_collect_item)
        columnToProperty.put("id", "id");
        columnToProperty.put("item_name", "name");
        columnToProperty.put("filepath", "filepath");
        columnToProperty.put("work_id", "workId");
        columnToProperty.put("sheet_id", "sheetId");


        // WorkData 完整字段映射 (表: asmr_works)
        columnToProperty.put("w.id", "work.id");
        columnToProperty.put("w.work_id", "work.workId");
        columnToProperty.put("w.title", "work.title");
        columnToProperty.put("w.title_cn", "work.titleCn");
        columnToProperty.put("w.circle", "work.circle");
        columnToProperty.put("w.release_date", "work.releaseDate");
        columnToProperty.put("w.rate", "work.rate");
        columnToProperty.put("w.subtitle", "work.subtitle");
        columnToProperty.put("w.nsfw", "work.nsfw");
        columnToProperty.put("w.vas", "work.vas");
        columnToProperty.put("w.categories", "work.categories");
        columnToProperty.put("w.tags", "work.tags");
        columnToProperty.put("w.visits", "work.visits");
        columnToProperty.put("w.favorites", "work.favorites");
        columnToProperty.put("w.sources", "work.sources");
        columnToProperty.put("w.filepath", "work.filepath");
        columnToProperty.put("w.parent_path", "work.parentPath");
        columnToProperty.put("w.thumbnail_cover_url", "work.thumbnailCoverUrl");
        columnToProperty.put("w.main_cover_url", "work.mainCoverUrl");
        columnToProperty.put("w.storage_type", "work.storageType");
        columnToProperty.put("w.has_info", "work.hasInfo");
        columnToProperty.put("w.related_work_ids", "work.relatedWorkIds");
        columnToProperty.put("w.origin_work_id", "work.originWorkId");

        return new BasicRowProcessor(new BeanProcessor(columnToProperty));
    }

    /**
     * 添加条目到歌单（自动提交，单行插入）。
     *
     * @return 是否插入成功
     */
    public boolean addItem(ItemData item) {
        String sql = "INSERT INTO asmr_collect_item (item_name, filepath, work_id, sheet_id) VALUES (?, ?, ?, ?)";
        try {
            QueryRunner runner = new QueryRunner();
            return runner.update(getConn(), sql,
                    item.getName(),
                    item.getFilepath(),
                    item.getWorkId(),
                    item.getSheetId()) > 0;
        } catch (SQLException e) {
            throw new DataAccessException("添加歌单项目失败", e);
        } finally {
            close();
        }
    }



    /**
     * 从歌单移除单个条目。
     *
     * @return 是否删除了记录，ID 不存在时返回 false
     */
    public boolean deleteItem(int itemId) {
        String sql = "DELETE FROM asmr_collect_item WHERE id = ?";
        try {
            QueryRunner runner = new QueryRunner();
            return runner.update(getConn(), sql, itemId) > 0;
        } catch (SQLException e) {
            throw new DataAccessException("删除歌单项目失败: " + itemId, e);
        } finally {
            close();
        }
    }

    /**
     * 删除歌单下的全部条目（用于保留歌单本身、只清空的场景）。
     * <p>
     * 删除歌单行时无需调用本方法，外键级联会自动清理。
     *
     * @return 是否删除了至少一条，歌单下无条目时返回 false
     */
    public boolean deleteItemBySheetId(int sheetId) {
        String sql = "DELETE FROM asmr_collect_item WHERE sheet_id = ?";
        try {
            QueryRunner runner = new QueryRunner();
            return runner.update(getConn(), sql, sheetId) > 0;
        } catch (SQLException e) {
            throw new DataAccessException("按歌单 ID 删除项目失败: " + sheetId, e);
        } finally {
            close();
        }
    }

    /**
     * 更新条目的显示名与文件路径，不改动其所属歌单与作品关联。
     *
     * @return 是否更新了记录
     */
    public boolean updateItem(ItemData item) {
        String sql = "UPDATE asmr_collect_item SET item_name = ?, filepath = ? WHERE id = ?";
        try {
            QueryRunner runner = new QueryRunner();
            return runner.update(getConn(), sql,
                    item.getName(),
                    item.getFilepath(),
                    item.getId()) > 0;
        } catch (SQLException e) {
            throw new DataAccessException("更新歌单项目失败: " + item.getId(), e);
        } finally {
            close();
        }
    }

    /**
     * 把条目移动到其他歌单（仅改 sheet_id，不做去重检查）。
     *
     * @return 是否移动成功
     */
    public boolean moveItemToSheet(int itemId, int newSheetId) {
        String sql = "UPDATE asmr_collect_item SET sheet_id = ? WHERE id = ?";
        try {
            QueryRunner runner = new QueryRunner();
            return runner.update(getConn(), sql, newSheetId, itemId) > 0;
        } catch (SQLException e) {
            throw new DataAccessException("移动歌单项目失败: " + itemId, e);
        } finally {
            close();
        }
    }

    /**
     * 查询歌单下的条目并内联其关联作品的完整信息，按条目 ID 倒序。
     * <p>
     * 与作品表是 INNER JOIN：外键级联保证正常情况下条目总能在 {@code asmr_works} 中找到对应行。
     *
     * @return 条目列表，歌单为空时返回空列表
     */
    public List<ItemDataEx> getItemsWithWorkInfoBySheet(int sheetId) {

        String sql = "SELECT i.id, i.item_name, i.filepath, i.work_id, i.sheet_id, " +
                "w.id as 'w.id', w.work_id as 'w.work_id', w.title as 'w.title', " +
                "w.title_cn as 'w.title_cn', w.circle as 'w.circle', " +
                "w.release_date as 'w.release_date', w.rate as 'w.rate', " +
                "w.subtitle as 'w.subtitle', w.nsfw as 'w.nsfw', w.vas as 'w.vas', " +
                "w.categories as 'w.categories', w.tags as 'w.tags', " +
                "w.visits as 'w.visits', w.favorites as 'w.favorites', " +
                "w.sources as 'w.sources', w.filepath as 'w.filepath', " +
                "w.parent_path as 'w.parent_path', w.thumbnail_cover_url as 'w.thumbnail_cover_url', " +
                "w.main_cover_url as 'w.main_cover_url', w.storage_type as 'w.storage_type', w.storage_source_id as 'w.storage_source_id', " +
                "w.has_info as 'w.has_info', w.related_work_ids as 'w.related_work_ids', " +
                "w.origin_work_id as 'w.origin_work_id' " +
                "FROM asmr_collect_item i " +
                "JOIN asmr_works w ON i.work_id = w.id " +
                "WHERE i.sheet_id = ? " +
                "ORDER BY i.id DESC";
        try {
            QueryRunner runner = new QueryRunner();
            return runner.query(getConn(), sql,
                    new ItemDataExListHandler(),
                    sheetId);
        } catch (SQLException e) {
            throw new DataAccessException("获取带作品信息的歌单项目失败: " + sheetId, e);
        } finally {
            close();
        }
    }


    /**
     * 查询单个条目详情（不含作品信息）。
     *
     * @return 条目数据，ID 不存在时返回 null
     */
    public ItemData getItemById(int itemId) {
        String sql = "SELECT * FROM asmr_collect_item WHERE id = ?";
        try {
            QueryRunner runner = new QueryRunner();
            return runner.query(getConn(), sql, 
                    new BeanHandler<>(ItemData.class, getRowProcessor()), 
                    itemId);
        } catch (SQLException e) {
            throw new DataAccessException("获取项目详情失败: " + itemId, e);
        } finally {
            close();
        }
    }

    /**
     * 按文件路径精确匹配查询条目（可跨歌单），按条目 ID 倒序。
     *
     * @return 匹配的条目列表，无结果时返回空列表
     */
    public List<ItemData> getItemsByFilePath(String filePath) {
        String sql = "SELECT * FROM asmr_collect_item WHERE filepath = ? ORDER BY id DESC";
        try {
            QueryRunner runner = new QueryRunner();
            return runner.query(getConn(), sql,
                    new BeanListHandler<>(ItemData.class, getRowProcessor()),
                    filePath);
        } catch (SQLException e) {
            throw new DataAccessException("按文件路径获取歌单项目失败: " + filePath, e);
        } finally {
            close();
        }
    }

    /**
     * 查询歌单下的全部条目（不含作品信息），按条目 ID 倒序。
     *
     * @return 条目列表，歌单为空时返回空列表
     */
    public List<ItemData> getItemsBySheet(int sheetId) {
        String sql = "SELECT * FROM asmr_collect_item WHERE sheet_id = ? ORDER BY id DESC";
        try {
            QueryRunner runner = new QueryRunner();
            return runner.query(getConn(), sql, 
                    new BeanListHandler<>(ItemData.class, getRowProcessor()), 
                    sheetId);
        } catch (SQLException e) {
            throw new DataAccessException("按歌单 ID 获取项目失败: " + sheetId, e);
        } finally {
            close();
        }
    }

    /**
     * 检查是否已有条目使用相同的文件路径（跨歌单）。
     */
    public boolean isItemExist(String filepath) {
        String sql = "SELECT COUNT(*) FROM asmr_collect_item WHERE filepath = ?";
        try {
            QueryRunner runner = new QueryRunner();
            Number count = runner.query(getConn(), sql, new ScalarHandler<>(), filepath);
            return count != null && count.longValue() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("检查项目存在性失败: " + filepath, e);
        } finally {
            close();
        }
    }

    /**
     * 查询某作品记录在各歌单中的全部关联条目，无排序保证。
     *
     * @param workId {@code asmr_works.id} 主键，声明为 String 但按数值列匹配
     * @return 关联条目列表，未收藏时返回空列表
     */
    public List<ItemData> getItemsByWork(String workId) {
        String sql = "SELECT * FROM asmr_collect_item WHERE work_id = ?";
        try {
            QueryRunner runner = new QueryRunner();
            return runner.query(getConn(), sql,
                    new BeanListHandler<>(ItemData.class, getRowProcessor()),
                    workId);
        } catch (SQLException e) {
            throw new DataAccessException("按作品 ID 获取关联项目失败: " + workId, e);
        } finally {
            close();
        }
    }

    /**
     * 按作品记录 ID 与文件路径双重精确匹配查询关联条目，用于判断"同一作品的同一文件"是否已收藏。
     *
     * @return 匹配的条目列表，无结果时返回空列表
     */
    public List<ItemData> getItemsByWorkAndFilepath(int workId,String filepath) {
        String sql = "SELECT * FROM asmr_collect_item WHERE work_id = ? and filepath = ?";
        try {
            QueryRunner runner = new QueryRunner();
            return runner.query(getConn(), sql,
                    new BeanListHandler<>(ItemData.class, getRowProcessor()),
                    workId,filepath);
        } catch (SQLException e) {
            throw new DataAccessException("按作品和路径获取关联项目失败: " + workId, e);
        } finally {
            close();
        }
    }


    /**
     * 手动行映射器：将联表结果的 {@code w.} 前缀列装配进内嵌 {@link AsmrWork}，
     * rate/visits/storageType 等可空数值列保持为 null。
     */
    public class ItemDataExListHandler implements ResultSetHandler<List<ItemDataEx>> {
        @Override
        public List<ItemDataEx> handle(ResultSet rs) throws SQLException {
            List<ItemDataEx> itemList = new ArrayList<>();

            while (rs.next()) {
                ItemDataEx item = new ItemDataEx();
                // 映射ItemData基础字段
                item.setId(rs.getInt("id"));
                item.setName(rs.getString("item_name"));
                item.setFilepath(rs.getString("filepath"));
                item.setWorkId(rs.getInt("work_id"));
                item.setSheetId(rs.getInt("sheet_id"));

                // 映射AsmrWork字段
                AsmrWork work = new AsmrWork();
                work.setId(rs.getInt("w.id"));
                work.setWorkId(rs.getString("w.work_id"));
                work.setTitle(rs.getString("w.title"));
                work.setTitleCN(rs.getString("w.title_cn"));
                work.setCircle(rs.getString("w.circle"));
                work.setRelease(rs.getString("w.release_date"));

                // 处理可能为null的数值字段
                Object rateObj = rs.getObject("w.rate");
                work.setRate(rateObj != null ? ((Number)rateObj).doubleValue() : null);

                work.setSubtitle(rs.getBoolean("w.subtitle"));
                work.setNsfw(rs.getBoolean("w.nsfw"));
                work.setVas(rs.getString("w.vas"));
                work.setCategories(rs.getString("w.categories"));
                work.setTags(rs.getString("w.tags"));

                Object visitsObj = rs.getObject("w.visits");
                work.setVisits(visitsObj != null ? ((Number)visitsObj).intValue() : null);

                work.setFavorites(rs.getBoolean("w.favorites"));
                work.setSources(rs.getString("w.sources"));
                work.setFilepath(rs.getString("w.filepath"));
                work.setParentPath(rs.getString("w.parent_path"));
                work.setThumbnailCoverUrl(rs.getString("w.thumbnail_cover_url"));
                work.setMainCoverUrl(rs.getString("w.main_cover_url"));

                Object storageTypeObj = rs.getObject("w.storage_type");
                work.setStorageType(storageTypeObj != null ? ((Number)storageTypeObj).intValue() : null);

                work.setStorageSourceId(rs.getInt("w.storage_source_id"));
                work.setHasInfo(rs.getBoolean("w.has_info"));
                work.setRelatedWorkIds(rs.getString("w.related_work_ids"));
                work.setOriginWorkId(rs.getString("w.origin_work_id"));

                // 设置关联关系
                item.setWork(work);

                itemList.add(item);
            }

            return itemList;
        }
    }
}
