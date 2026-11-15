package cn.tealc.asmr.model;

/**
 * 存储源凭据的运行时表示。
 * <p>
 * secret 只在内存中保存，DAO 持久化的是加密后的值。
 */
public class StorageCredential {
    private Integer id;
    private String authType;
    private String secret;

    public StorageCredential() {
    }

    public StorageCredential(String authType, String secret) {
        this.authType = authType;
        this.secret = secret;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
}
