package cn.tealc.asmr.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 远程元数据服务的通用响应体，由 Jackson 反序列化（忽略未知字段）。
 * <p>
 * DLSite 信息抓取任务（{@code DLSiteThread} 等）用 {@code code} 与
 * {@link StatusCode} 比对判断成败，业务载荷放在 {@code data}。
 *
 * @author Leck
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResponseBody<T> {
    private Integer code;
    private String msg;
    private T data;
    private Boolean success;


    public ResponseBody() {
    }

    public ResponseBody(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
        this.success = false;
    }

    public ResponseBody(Integer code, String msg, Boolean success) {
        this.code = code;
        this.msg = msg;
        this.success = success;
    }




    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }


    public static class StatusCode{
        public static final int SUCCESS = 200;
        public static final int ERROR = 500;
    }
}