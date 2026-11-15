package cn.tealc.asmr.dao.album;

import cn.tealc.asmr.dao.AsmrWorkDao;
import cn.tealc.asmr.dao.BaseDao;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.album.AlbumWorkPage;
import cn.tealc.asmr.model.album.WorkAlbum;
import cn.tealc.asmr.model.album.WorkAlbumSummary;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.RowProcessor;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/** 作品集与成员关联操作；所有成员变更按作品记录主键定位。 */
public class WorkAlbumDao extends BaseDao {
    private final QueryRunner runner = new QueryRunner();

    @Override
    public RowProcessor getRowProcessor() {
        return new AsmrWorkDao().getRowProcessor();
    }

    public WorkAlbum create(String name, String description, LocalDateTime now) {
        return execute("创建作品集失败", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO asmr_work_album(name, description, created_at) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, name);
                statement.setString(2, description);
                statement.setString(3, now.toString());
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("无法获取作品集主键");
                    return new WorkAlbum(keys.getInt(1), name, description, now, null);
                }
            }
        });
    }

    public boolean update(int id, String name, String description) {
        return execute("修改作品集失败", connection -> runner.update(connection,
                "UPDATE asmr_work_album SET name = ?, description = ? WHERE id = ?", name, description, id) > 0);
    }

    public boolean delete(int id) {
        return execute("删除作品集失败", connection -> runner.update(connection,
                "DELETE FROM asmr_work_album WHERE id = ?", id) > 0);
    }

    public List<WorkAlbumSummary> summaries() {
        return summaries("ORDER BY a.created_at DESC, a.id DESC", null);
    }

    /**
     * 按最近加入时间倒序查询作品集摘要，用于右键菜单的快捷入口。
     * <p>
     * 从未加入过作品的集册排在最后，因为它们没有 last_added_at。
     *
     * @param limit 最多返回多少条
     * @return 最近有变动的作品集
     */
    public List<WorkAlbumSummary> recentSummaries(int limit) {
        return summaries("ORDER BY a.last_added_at IS NULL, a.last_added_at DESC, a.created_at DESC, a.id DESC", limit);
    }

    /**
     * 查询作品集摘要。
     *
     * @param orderBy 排序子句
     * @param limit   限制条数，null 表示不限制
     */
    private List<WorkAlbumSummary> summaries(String orderBy, Integer limit) {
        String sql = """
                SELECT a.id AS album_record_id, a.name AS album_name, a.description AS album_description,
                       a.created_at AS album_created_at, a.last_added_at AS album_last_added_at,
                       (SELECT COUNT(*) FROM asmr_work_album_item i WHERE i.album_id = a.id) AS work_count,
                       w.*
                FROM asmr_work_album a
                LEFT JOIN asmr_works w ON w.id = (
                    SELECT i.work_record_id FROM asmr_work_album_item i WHERE i.album_id = a.id
                    ORDER BY i.added_at DESC, i.work_record_id DESC LIMIT 1
                )
                """ + orderBy + (limit == null ? "" : " LIMIT " + limit);
        return execute("获取作品集失败", connection -> runner.query(connection, sql, result -> {
            List<WorkAlbumSummary> albums = new ArrayList<>();
            RowProcessor processor = getRowProcessor();
            while (result.next()) {
                WorkAlbum album = new WorkAlbum(result.getInt("album_record_id"), result.getString("album_name"),
                        result.getString("album_description"), time(result.getString("album_created_at")),
                        time(result.getString("album_last_added_at")));
                AsmrWork cover = result.getObject("id") == null ? null : processor.toBean(result, AsmrWork.class);
                albums.add(new WorkAlbumSummary(album, result.getInt("work_count"), cover));
            }
            return albums;
        }));
    }

    /** 一批作品可以加入多个作品集；任一外键无效则整批回滚。 */
    public int addWorks(Collection<Integer> albumIds, Collection<Integer> workIds, LocalDateTime now) {
        return execute("添加作品到作品集失败", connection -> {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO asmr_work_album_item(album_id, work_record_id, added_at) VALUES (?, ?, ?)
                    ON CONFLICT(album_id, work_record_id) DO NOTHING
                    """);
                 PreparedStatement update = connection.prepareStatement(
                         "UPDATE asmr_work_album SET last_added_at = ? WHERE id = ?")) {
                int total = 0;
                for (int albumId : new LinkedHashSet<>(albumIds)) {
                    int added = 0;
                    for (int workId : new LinkedHashSet<>(workIds)) {
                        insert.setInt(1, albumId);
                        insert.setInt(2, workId);
                        insert.setString(3, now.toString());
                        added += insert.executeUpdate();
                    }
                    if (added > 0) {
                        update.setString(1, now.toString());
                        update.setInt(2, albumId);
                        update.executeUpdate();
                    }
                    total += added;
                }
                connection.commit();
                return total;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        });
    }

    public boolean removeWork(int albumId, int workId) {
        return execute("移出作品集失败", connection -> runner.update(connection,
                "DELETE FROM asmr_work_album_item WHERE album_id = ? AND work_record_id = ?", albumId, workId) > 0);
    }

    public Set<Integer> albumsForWork(int workId) {
        return execute("获取作品所属集册失败", connection -> runner.query(connection,
                "SELECT album_id FROM asmr_work_album_item WHERE work_record_id = ?", result -> {
                    Set<Integer> ids = new HashSet<>();
                    while (result.next()) ids.add(result.getInt(1));
                    return ids;
                }, workId));
    }

    public AlbumWorkPage works(int albumId, String search, int page, int pageSize) {
        String from = " FROM asmr_works w JOIN asmr_work_album_item i ON i.work_record_id = w.id WHERE i.album_id = ?";
        return queryWorks(from, List.of(albumId), search, page, pageSize, "i.added_at DESC, w.id DESC");
    }

    public AlbumWorkPage works(int albumId, String search, int page, int pageSize,
                               int sortType, boolean descending, boolean subtitle, boolean nsfw,
                               boolean noMate, boolean showLocal, boolean showNetwork) {
        StringBuilder from = new StringBuilder(
                " FROM asmr_works w JOIN asmr_work_album_item i ON i.work_record_id = w.id WHERE i.album_id = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(albumId);
        appendWorkFilters(from, parameters, subtitle, nsfw, noMate, showLocal, showNetwork);
        String order;
        if (sortType == 5) {
            order = "RANDOM()";
        } else {
            String column = switch (sortType) {
                case 2 -> "w.work_id";
                case 3 -> "w.release_date";
                case 4 -> "w.rate";
                default -> "i.added_at";
            };
            String direction = descending ? " DESC" : " ASC";
            order = column + direction + ", w.id" + direction;
        }
        return queryWorks(from.toString(), parameters, search, page, pageSize, order);
    }

    private static void appendWorkFilters(StringBuilder from, List<Object> parameters,
                                          boolean subtitle, boolean nsfw, boolean noMate,
                                          boolean showLocal, boolean showNetwork) {
        if (!nsfw) {
            from.append(" AND w.nsfw = ?");
            parameters.add(false);
        }
        if (subtitle) {
            from.append(" AND w.subtitle = ?");
            parameters.add(true);
        }
        if (noMate) {
            from.append(" AND w.sources IS NULL");
        }
        if (showLocal && !showNetwork) {
            from.append(" AND w.storage_type = 0");
        } else if (!showLocal && showNetwork) {
            from.append(" AND w.storage_type <> 0");
        } else if (!showLocal && !showNetwork) {
            from.append(" AND 1=0");
        }
    }

    public AlbumWorkPage candidates(int albumId, Integer libraryId, String search, int page, int pageSize) {
        String from = " FROM asmr_works w WHERE NOT EXISTS (SELECT 1 FROM asmr_work_album_item i WHERE i.album_id = ? AND i.work_record_id = w.id)";
        List<Object> parameters = new ArrayList<>(List.of(albumId));
        if (libraryId != null) {
            from += " AND w.media_library_id = ?";
            parameters.add(libraryId);
        }
        return queryWorks(from, parameters, search, page, pageSize, "w.id DESC");
    }

    private AlbumWorkPage queryWorks(String from, List<Object> baseParameters, String search,
                                     int page, int pageSize, String order) {
        String sqlFrom = from;
        List<Object> parameters = new ArrayList<>(baseParameters);
        if (search != null && !search.isBlank()) {
            sqlFrom += " AND (w.title LIKE ? ESCAPE '\\' OR w.title_cn LIKE ? ESCAPE '\\' OR w.work_id LIKE ? ESCAPE '\\')";
            String pattern = "%" + search.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            parameters.addAll(List.of(pattern, pattern, pattern));
        }
        String finalFrom = sqlFrom;
        return execute("获取作品集作品失败", connection -> {
            connection.setAutoCommit(false);
            try {
                int count = runner.query(connection, "SELECT COUNT(*)" + finalFrom,
                        result -> result.next() ? result.getInt(1) : 0, parameters.toArray());
                List<Object> pageParameters = new ArrayList<>(parameters);
                pageParameters.add(pageSize);
                pageParameters.add((long) (page - 1) * pageSize);
                List<AsmrWork> works = runner.query(connection,
                        "SELECT w.*" + finalFrom + " ORDER BY " + order + " LIMIT ? OFFSET ?",
                        result -> getRowProcessor().toBeanList(result, AsmrWork.class), pageParameters.toArray());
                connection.commit();
                return new AlbumWorkPage(works, count);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        });
    }

    private static LocalDateTime time(String value) {
        return value == null ? null : LocalDateTime.parse(value);
    }
}
