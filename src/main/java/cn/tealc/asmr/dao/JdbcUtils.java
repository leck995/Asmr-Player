package cn.tealc.asmr.dao;

import cn.tealc.asmr.utils.AppPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.Properties;


/**
 * SQLite 连接的统一入口与数据库结构初始化。
 * <p>
 * 数据库文件位于应用数据目录（{@link AppPaths#databaseFile()}），驱动类名从
 * classpath 下的 {@code db.properties} 读取；静态初始化失败只记录日志、不抛异常。
 * 每次 {@link #getConnection()} 返回的新连接均已开启 {@code PRAGMA foreign_keys}，
 * 使外键的 ON DELETE CASCADE 生效。
 *
 * @author Leck
 */
public class JdbcUtils {
    static {
        // 先确定数据目录，再初始化 logger
        AppPaths.init();
    }

    private static final Logger LOG = LoggerFactory.getLogger(JdbcUtils.class);
    private static String url;

    static {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("db.properties")) {
            Properties properties = new Properties();
            properties.load(in);
            String driver = properties.getProperty("driver");
            url = "jdbc:sqlite:" + AppPaths.databaseFile().getAbsolutePath()
                    + "?date_string_format=yyyy-MM-dd";
            Class.forName(driver);
        } catch (NullPointerException | IOException e) {
            LOG.error("找不到db.properties文件，请在resources目录下创建db.properties文件，并写入数据库配置", e);
        } catch (ClassNotFoundException e) {
            LOG.error("找不到数据库驱动类", e);
        }
    }


    /**
     * 获取数据库连接。
     * <p>
     * 每次创建新连接，调用方负责通过 {@link #close(Connection, Statement...)} 释放。
     * SQLite 单文件场景下短连接开销很小，且天然避免多线程共享同一 Connection 的并发问题。
     *
     * @return 新的数据库连接
     */
    public static Connection getConnection() {
        try {
            Connection conn = DriverManager.getConnection(url);
            // 启用 SQLite 外键约束，让 ON DELETE CASCADE 生效
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
            }
            return conn;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 关闭连接与其关联语句，吞掉 SQLException 只记日志。
     * <p>
     * 所有参数均允许为 null；先关闭语句、后关闭连接。
     */
    public static void close(Connection conn, Statement... sts) {
        try {
            if (sts != null) {
                for (Statement st : sts) {
                    if (st != null) {
                        st.close();
                    }
                }
            }
            if (conn != null) {
                conn.close();
            }
        } catch (SQLException e) {
            LOG.error("关闭连接出错", e);
        }
    }

    /**
     * 关闭结果集、语句与连接，吞掉 SQLException 只记日志。
     * <p>
     * 按 rs → 语句 → 连接的顺序释放；{@code rs} 与各语句允许为 null，
     * 但语句数组本身不能为 null（与 {@link #close(Connection, Statement...)} 不同）。
     */
    public static void close(Connection conn, ResultSet rs, Statement... sts) {
        try {
            if (rs != null) {
                rs.close();
            }
            for (Statement st : sts) {
                if (st != null) {
                    st.close();
                }
            }
            if (conn != null) {
                conn.close();
            }
        } catch (SQLException e) {
            LOG.error("关闭连接出错", e);
        }
    }

    /**
     * 兼容旧调用点的空实现：连接由各 DAO 的 ThreadLocal 管理，此处无需批量释放。
     */
    public static void exit() {
        // 连接现由各 DAO 的 ThreadLocal 管理，程序退出时由 JVM 自动释放
    }


    /**
     * 幂等地建立全部业务表、索引，并对既有库做加列迁移，最后更新 {@code PRAGMA user_version}。
     * <p>
     * 全程在单事务中执行，失败回滚并抛出 {@link RuntimeException}；
     * 结构不受支持的既有库会在 {@link #validateNewDatabase} 中直接拒绝，不会被改动。
     */
    public static void init() {
        Connection con = null;
        try {
            con = DriverManager.getConnection(url);
            validateNewDatabase(con);
            con.setAutoCommit(false);
            try (Statement foreignKeySt = con.createStatement()) {
                foreignKeySt.execute("PRAGMA foreign_keys = ON");
            }
            String createWorks = "CREATE TABLE IF NOT EXISTS `asmr_works` (\n" +
                    "  id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                    "  work_id VARCHAR(30),\n" +
                    "  title VARCHAR(100) NOT NULL,\n" +
                    "  title_cn VARCHAR(100),\n" +
                    "  circle VARCHAR(30) ,\n" +
                    "  release_date TEXT DEFAULT '1990-01-01',\n" +
                    "  rate DOUBLE DEFAULT 0,\n" +
                    "  subtitle BOOL DEFAULT 0,\n" +
                    "  nsfw BOOL DEFAULT 1,\n" +
                    "  vas VARCHAR(255),\n" +
                    "  categories VARCHAR(255),\n" +
                    "  tags VARCHAR(255),\n" +
                    "  visits INTEGER DEFAULT 0,\n" +
                    "  favorites BOOL DEFAULT 0,\n" +
                    "  sources VARCHAR(20),\n" +
                    "  filepath VARCHAR(255),\n" +
                    "  parent_path VARCHAR(255),\n" +
                    "  thumbnail_cover_url VARCHAR(255),\n" +
                    "  main_cover_url VARCHAR(255),\n" +
                    "  storage_type INTEGER NOT NULL DEFAULT 0,\n" +
                    "  media_library_id INTEGER,\n" +
                    "  storage_source_id INTEGER NOT NULL,\n" +
                    "  has_info BOOL DEFAULT 0,\n" +
                    "  related_work_ids VARCHAR(255),\n" +
                    "  origin_work_id VARCHAR(255),\n" +
                    "  UNIQUE(work_id, storage_source_id),\n" +
                    "  UNIQUE(filepath, storage_source_id)\n" +
                    ");\n";
            String createCategories = "CREATE TABLE IF NOT EXISTS 'asmr_categories'(\n" +
                    "  id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                    "  name VARCHAR(20) NOT NULL UNIQUE\n" +
                    ");\n";
            String createTags = "CREATE TABLE IF NOT EXISTS 'asmr_tags'(\n" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                    "name VARCHAR(20) NOT NULL UNIQUE,\n" +
                    "description TEXT NOT NULL DEFAULT '',\n" +
                    "create_date_time TEXT NOT NULL DEFAULT '',\n" +
                    "update_date_time TEXT NOT NULL DEFAULT ''\n" +
                    ");\n";
            String createVas = "CREATE TABLE IF NOT EXISTS 'asmr_vas'(\n" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                    "name VARCHAR(20) NOT NULL UNIQUE\n" +
                    ");\n";
            String createCircles = "CREATE TABLE IF NOT EXISTS 'asmr_circles'(\n" +
                    "  id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                    "  name VARCHAR(20) NOT NULL UNIQUE\n" +
                    ");\n";
            String createSaveData = "CREATE TABLE IF NOT EXISTS `asmr_galgame_save` (\n" +
                    "  id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                    "  title VARCHAR(100) NOT NULL,\n" +
                    "  chapter VARCHAR(100) NOT NULL,\n" +
                    "  filepath VARCHAR(255) NOT NULL,\n" +
                    "  thumb VARCHAR(255) NOT NULL,\n" +
                    "  background VARCHAR(255) ,\n" +
                    "  save_time DOUBLE,\n" +
                    "  text VARCHAR(100),\n" +
                    "  save_date VARCHAR(100) NOT NULL,\n" +
                    "  asmr_id INTEGER\n" +
                    ");\n";

            String createBlacklistTable = """
                    CREATE TABLE IF NOT EXISTS 'asmr_blacklist_text'(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    row TEXT NOT NULL UNIQUE
                    );""";

            String createSheetTable = """
                    CREATE TABLE IF NOT EXISTS 'asmr_collect_sheet'(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    sheet_name TEXT NOT NULL,
                    description TEXT NOT NULL DEFAULT '',
                    create_date_time TEXT NOT NULL,
                    update_date_time TEXT NOT NULL DEFAULT '',
                    top INTEGER NOT NULL DEFAULT 1
                    );""";
            String createItemTable = """
                    CREATE TABLE IF NOT EXISTS 'asmr_collect_item' (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        item_name TEXT NOT NULL,
                        filepath TEXT NOT NULL,
                        work_id INTEGER NOT NULL,
                        sheet_id INTEGER NOT NULL,
                        FOREIGN KEY (sheet_id) REFERENCES asmr_collect_sheet(id) ON DELETE CASCADE,
                        FOREIGN KEY (work_id) REFERENCES asmr_works(id) ON DELETE CASCADE
                    );""";

            Statement st = con.createStatement();
            st.execute(createWorks);
            st.execute(createCategories);
            st.execute(createTags);
            st.execute(createVas);
            st.execute(createCircles);
            st.execute(createSaveData);
            st.execute(createSheetTable);
            st.execute(createItemTable);
            st.execute(createBlacklistTable);

            ensureColumn(st, "asmr_collect_sheet", "description", "TEXT NOT NULL DEFAULT ''");
            ensureColumn(st, "asmr_collect_sheet", "update_date_time", "TEXT NOT NULL DEFAULT ''");
            ensureColumn(st, "asmr_tags", "description", "TEXT NOT NULL DEFAULT ''");
            ensureColumn(st, "asmr_tags", "create_date_time", "TEXT NOT NULL DEFAULT ''");
            ensureColumn(st, "asmr_tags", "update_date_time", "TEXT NOT NULL DEFAULT ''");
            st.executeUpdate("UPDATE asmr_collect_sheet SET update_date_time = create_date_time WHERE update_date_time = ''");
            st.executeUpdate("UPDATE asmr_tags SET create_date_time = datetime('now','localtime') WHERE create_date_time = ''");
            st.executeUpdate("UPDATE asmr_tags SET update_date_time = create_date_time WHERE update_date_time = ''");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS asmr_work_album (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL CHECK(length(trim(name)) > 0),
                        description TEXT NOT NULL DEFAULT '',
                        created_at TEXT NOT NULL,
                        last_added_at TEXT
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS asmr_work_album_item (
                        album_id INTEGER NOT NULL REFERENCES asmr_work_album(id) ON DELETE CASCADE,
                        work_record_id INTEGER NOT NULL REFERENCES asmr_works(id) ON DELETE CASCADE,
                        added_at TEXT NOT NULL,
                        PRIMARY KEY (album_id, work_record_id)
                    )
                    """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_work_album_item_work ON asmr_work_album_item(work_record_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_work_album_item_added ON asmr_work_album_item(album_id, added_at DESC, work_record_id DESC)");

            st.execute("""
                    CREATE TABLE IF NOT EXISTS asmr_anywhere_door (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        type TEXT NOT NULL CHECK(type IN ('FOLDER','ONLINE','CATEGORY','VA','CIRCLE','TAG')),
                        target_key TEXT NOT NULL CHECK(length(trim(target_key)) > 0),
                        normalized_key TEXT NOT NULL CHECK(length(trim(normalized_key)) > 0),
                        alias TEXT,
                        sort_order INTEGER NOT NULL CHECK(sort_order >= 0),
                        UNIQUE(type, normalized_key)
                    )
                    """);

            String createMediaLibrary = """
                    CREATE TABLE IF NOT EXISTS 'asmr_media_library' (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name VARCHAR(100) NOT NULL,
                        path VARCHAR(255) NOT NULL,
                        level INTEGER NOT NULL DEFAULT 1,
                        storage_type INTEGER NOT NULL DEFAULT 0,
                        storage_source_id INTEGER NOT NULL,
                        last_imported_at TEXT,
                        last_scraped_at TEXT
                    );""";
            st.execute(createMediaLibrary);

            String createStorageCredential = """
                    CREATE TABLE IF NOT EXISTS 'asmr_storage_credential' (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        auth_type VARCHAR(30) NOT NULL,
                        secret_value TEXT NOT NULL
                    );""";
            st.execute(createStorageCredential);

            String createStorageSource = """
                    CREATE TABLE IF NOT EXISTS 'asmr_storage_source' (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name VARCHAR(100) NOT NULL,
                        type INTEGER NOT NULL,
                        target_path VARCHAR(255) NOT NULL,
                        connection_config TEXT,
                        credential_id INTEGER,
                        status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
                        last_error TEXT,
                        FOREIGN KEY (credential_id) REFERENCES asmr_storage_credential(id)
                    );""";
            st.execute(createStorageSource);

            String createCacheEntry = """
                    CREATE TABLE IF NOT EXISTS 'asmr_cache_entry' (
                        cache_key TEXT PRIMARY KEY,
                        source_path TEXT NOT NULL UNIQUE,
                        file_type TEXT NOT NULL,
                        local_path TEXT NOT NULL,
                        size INTEGER NOT NULL,
                        last_access_time INTEGER NOT NULL,
                        create_time INTEGER NOT NULL
                    );""";
            st.execute(createCacheEntry);

            String createWorkBackground = """
                    CREATE TABLE IF NOT EXISTS 'asmr_work_background' (
                        work_record_id INTEGER PRIMARY KEY,
                        storage_type INTEGER NOT NULL,
                        storage_source_id INTEGER NOT NULL,
                        source_path TEXT,
                        scanned_at INTEGER NOT NULL,
                        FOREIGN KEY (work_record_id) REFERENCES asmr_works(id) ON DELETE CASCADE
                    );""";
            st.execute(createWorkBackground);

            // 创建索引
            st.execute("CREATE INDEX IF NOT EXISTS idx_works_parent_path ON asmr_works(parent_path)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_works_favorites ON asmr_works(favorites)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_works_storage_type ON asmr_works(storage_type)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_works_media_library_id ON asmr_works(media_library_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_works_storage_source_id ON asmr_works(storage_source_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_media_library_storage_source_id ON asmr_media_library(storage_source_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_anywhere_door_sort_order ON asmr_anywhere_door(sort_order, id)");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS asmr_anywhere_door_library (
                        shortcut_id INTEGER PRIMARY KEY REFERENCES asmr_anywhere_door(id) ON DELETE CASCADE,
                        media_library_id INTEGER NOT NULL REFERENCES asmr_media_library(id)
                    )
                    """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_anywhere_door_library ON asmr_anywhere_door_library(media_library_id)");

            st.execute("PRAGMA user_version = 6");
            st.close();
            con.commit();
        } catch (SQLException e) {
            if (con != null) {
                try { con.rollback(); } catch (SQLException ignored) {}
            }
            throw new RuntimeException(e);
        } finally {
            if (con != null) {
                try { con.setAutoCommit(true); } catch (SQLException ignored) {}
                try { con.close(); } catch (SQLException ignored) {}
            }
        }
    }


    private static void ensureColumn(Statement statement, String table, String column, String definition)
            throws SQLException {
        try (ResultSet columns = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (columns.next()) {
                if (column.equals(columns.getString("name"))) {
                    return;
                }
            }
        }
        statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }

    /**
     * 校验库结构版本：user_version 为 3~6 直接放行；为 0（无版本标记）时要求库中没有任何业务表，
     * 否则抛出 SQLException，保证不改动来源不明的既有数据库。
     */
    private static void validateNewDatabase(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet version = statement.executeQuery("PRAGMA user_version")) {
            if (version.next()) {
                int value = version.getInt(1);
                if (value == 3 || value == 4 || value == 5 || value == 6) return;
                if (value != 0) throw new SQLException("数据库版本不受支持；现有数据库未被修改");
            }
        }
        try (Statement statement = connection.createStatement();
             ResultSet tables = statement.executeQuery(
                     "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' LIMIT 1")) {
            if (tables.next()) {
                throw new SQLException("数据库不是当前版本的新数据库，请使用新的空数据库；现有数据库未被修改");
            }
        }
    }
}
