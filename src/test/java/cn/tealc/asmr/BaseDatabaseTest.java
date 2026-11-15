package cn.tealc.asmr;

import cn.tealc.asmr.dao.JdbcUtils;
import cn.tealc.asmr.di.DI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 数据库测试基类。
 * <p>
 * 每个测试类使用独立临时文件库，{@link BeforeAll} 初始化当前模式，
 * {@link BeforeEach} 清空表数据确保测试间隔离。
 *
 * @author Leck
 */
public abstract class BaseDatabaseTest {
    private static String originalUrl;

    @BeforeAll
    static void initDatabase() throws Exception {
        Field field = JdbcUtils.class.getDeclaredField("url");
        field.setAccessible(true);
        originalUrl = (String) field.get(null);
        field.set(null, "jdbc:sqlite:" + Files.createTempDirectory("asmr-database-tests-").resolve("test.db"));
        JdbcUtils.init();
    }

    @AfterAll
    static void restoreDatabase() throws Exception {
        Field field = JdbcUtils.class.getDeclaredField("url");
        field.setAccessible(true);
        field.set(null, originalUrl);
        DI.reset();
    }

    @BeforeEach
    void clearTables() {
        DI.reset();
        try (Connection conn = JdbcUtils.getConnection(); Statement st = conn.createStatement()) {
            st.execute("DELETE FROM asmr_galgame_save");
            st.execute("DELETE FROM asmr_collect_item");
            st.execute("DELETE FROM asmr_collect_sheet");
            st.execute("DELETE FROM asmr_work_album");
            st.execute("DELETE FROM asmr_works");
            st.execute("DELETE FROM asmr_anywhere_door");
            st.execute("DELETE FROM asmr_categories");
            st.execute("DELETE FROM asmr_vas");
            st.execute("DELETE FROM asmr_circles");
            st.execute("DELETE FROM asmr_tags");
        } catch (SQLException e) {
            throw new RuntimeException("清空测试表失败", e);
        }
    }

    /**
     * 清空指定单张表。
     */
    protected void clearTable(String tableName) {
        try (Connection conn = JdbcUtils.getConnection(); Statement st = conn.createStatement()) {
            st.execute("DELETE FROM " + tableName);
        } catch (SQLException e) {
            throw new RuntimeException("清空表 " + tableName + " 失败", e);
        }
    }
}
