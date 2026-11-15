package cn.tealc.asmr.source.openlist.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OpenList API 统一响应包装。
 * <p>
 * 所有 OpenList 接口返回的顶层结构均为 {@code { code, message, data }}，
 * 通过泛型参数 {@code T} 指定 {@code data} 的具体类型。
 *
 * @param <T> data 字段的目标类型
 * @author Leck
 */
public class OpenListResponse<T> {

    /** 状态码，200 表示成功 */
    private int code;

    /** 响应消息 */
    private String message;

    /** 响应数据 */
    private T data;

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    /**
     * 判断响应是否成功。
     *
     * @return code 为 200 时返回 true
     */
    public boolean isSuccess() {
        return code == 200;
    }

    /**
     * 判断响应是否为未授权（token 失效）。
     *
     * @return code 为 401 时返回 true
     */
    public boolean isUnauthorized() {
        return code == 401;
    }
}
