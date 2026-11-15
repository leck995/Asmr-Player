package cn.tealc.asmr.source.openlist;

/**
 * OpenList 操作异常。
 * <p>
 * 用于 OpenList 客户端的登录失败、API 调用失败、网络错误等场景，
 * 代替旧代码中散落的 {@code null 返回 + log.error} 模式，使调用方能区分错误类型。
 *
 * @author Leck
 */
public class OpenListException extends RuntimeException {
    private final boolean authenticationFailure;
    private final boolean notFound;

    public OpenListException(String message) {
        this(message, null, false, false);
    }

    public OpenListException(String message, Throwable cause) {
        this(message, cause, false, false);
    }

    public OpenListException(String message, boolean authenticationFailure) {
        this(message, null, authenticationFailure, false);
    }

    public OpenListException(String message, boolean authenticationFailure, boolean notFound) {
        this(message, null, authenticationFailure, notFound);
    }

    private OpenListException(String message, Throwable cause, boolean authenticationFailure, boolean notFound) {
        super(message, cause);
        this.authenticationFailure = authenticationFailure;
        this.notFound = notFound;
    }

    /**
     * 是否由令牌失效或无权限导致。
     */
    public boolean isAuthenticationFailure() {
        return authenticationFailure;
    }

    /**
     * 是否为目标文件/目录不存在。
     * <p>
     * OpenList 的 {@code /api/fs/get} 对不存在的路径同样返回 HTTP 200 + 错误 code，
     * 因此需要根据响应内容识别，供 {@code exists} 等方法把“不存在”和网络错误区分开。
     */
    public boolean isNotFound() {
        return notFound;
    }
}
