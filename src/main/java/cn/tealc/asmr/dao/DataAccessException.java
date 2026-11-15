package cn.tealc.asmr.dao;

/**
 * 数据访问层统一异常，包装 SQLException 为运行时异常。
 * <p>
 * DAO 层抛出，由 Service/UI 层决定如何处理。
 * 与直接抛 SQLException 相比，避免污染 Service/ViewModel 方法签名。
 *
 * @author Leck
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
