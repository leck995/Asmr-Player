package cn.tealc.asmr.model;

/**
 * 存储源连接状态。
 */
public enum StorageSourceStatus {
    ACTIVE,
    AUTH_REQUIRED,
    AUTH_EXPIRED,
    UNAVAILABLE,
    DISABLED
}
