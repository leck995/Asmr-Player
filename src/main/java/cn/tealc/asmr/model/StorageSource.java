package cn.tealc.asmr.model;

import cn.tealc.asmr.source.StorageType;

/**
 * 可访问的文件存储源。
 * <p>
 * {@code connectionConfig} 仅保存不敏感的协议配置，认证信息由
 * {@code credentialId} 指向独立的凭据记录。
 */
public class StorageSource {
    private Integer id;
    private String name;
    private StorageType type;
    private String targetPath;
    private String connectionConfig;
    private Integer credentialId;
    private StorageSourceStatus status;
    private String lastError;

    public StorageSource() {
    }

    public StorageSource(String name, StorageType type, String targetPath,
                         String connectionConfig, Integer credentialId) {
        this.name = name;
        this.type = type;
        this.targetPath = targetPath;
        this.connectionConfig = connectionConfig;
        this.credentialId = credentialId;
        this.status = StorageSourceStatus.ACTIVE;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public StorageType getType() { return type; }
    public void setType(StorageType type) { this.type = type; }
    public String getTargetPath() { return targetPath; }
    public void setTargetPath(String targetPath) { this.targetPath = targetPath; }
    public String getConnectionConfig() { return connectionConfig; }
    public void setConnectionConfig(String connectionConfig) { this.connectionConfig = connectionConfig; }
    public Integer getCredentialId() { return credentialId; }
    public void setCredentialId(Integer credentialId) { this.credentialId = credentialId; }
    public StorageSourceStatus getStatus() { return status; }
    public void setStatus(StorageSourceStatus status) { this.status = status; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    @Override
    public String toString() {
        return name;
    }
}
