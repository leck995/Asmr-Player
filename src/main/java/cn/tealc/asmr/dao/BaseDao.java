package cn.tealc.asmr.dao;

import org.apache.commons.dbutils.RowProcessor;

import java.sql.*;

/**
 * DAO 基类，提供连接管理与执行模板。
 * <p>
 * 使用 ThreadLocal 为每个线程维护独立连接，避免多线程共享同一 Connection 的并发问题。
 * 子类通过 {@link #getConn()} 获取连接，通过 {@link #close()} 释放。
 * <p>
 * 推荐使用 {@link #execute} 模板方法，自动管理连接生命周期和异常包装，
 * DAO 方法无需手写 try-catch-finally 样板。
 *
 * @author Leck
 */
public abstract class BaseDao {

    private final ThreadLocal<Connection> connHolder = new ThreadLocal<>();

    /**
     * 执行模板：自动管理连接和异常包装。
     * <p>
     * 获取连接 → 执行操作 → close()，SQLException 包装为
     * {@link DataAccessException} 抛出。
     *
     * @param operation 操作描述（用于异常消息）
     * @param action    SQL 执行动作
     * @return 操作返回值
     */
    protected <T> T execute(String operation, SqlAction<T> action) {
        try {
            return action.run(getConn());
        } catch (SQLException e) {
            throw new DataAccessException(operation, e);
        } finally {
            close();
        }
    }

    /**
     * SQL 执行动作的函数式接口。
     */
    @FunctionalInterface
    protected interface SqlAction<T> {
        T run(Connection conn) throws SQLException;
    }

    /**
     * 获取数据库连接（每线程独立）。
     * <p>
     * 从 ThreadLocal 取出当前线程的连接；若不存在则新建一个。
     * 调用方需在 finally 中调用 {@link #close()} 释放。
     *
     * @return 当前线程的数据库连接
     */
    public Connection getConn() {
        Connection conn = connHolder.get();
        if (conn == null) {
            conn = JdbcUtils.getConnection();
            connHolder.set(conn);
        }
        return conn;
    }

    /**
     * 关闭当前线程的连接及关联资源。
     * <p>
     * 从 ThreadLocal 取出连接并关闭，然后清除 ThreadLocal 防止泄漏。
     */
    public void close() {
        Connection conn = connHolder.get();
        if (conn != null) {
            JdbcUtils.close(conn, (Statement[]) null);
            connHolder.remove();
        }
    }

    /**
     * 子类实现列名到属性名的映射处理器。
     */
    public abstract RowProcessor getRowProcessor();

}
