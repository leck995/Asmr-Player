package cn.tealc.asmr.service;

import cn.tealc.asmr.dao.StorageCredentialDao;
import cn.tealc.asmr.dao.StorageSourceDao;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.OpenListAccess;
import cn.tealc.asmr.model.StorageCredential;
import cn.tealc.asmr.model.StorageSource;
import cn.tealc.asmr.model.StorageSourceStatus;
import cn.tealc.asmr.source.StorageType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 存储源管理服务。 */
public class StorageSourceService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final StorageSourceDao sourceDao = DI.get(StorageSourceDao.class);
    private final StorageCredentialDao credentialDao = DI.get(StorageCredentialDao.class);

    public List<StorageSource> getAll() { return sourceDao.getAll(); }
    public StorageSource getById(int id) { return sourceDao.getById(id); }

    public void remove(int id) {
        if (!sourceDao.deleteIfUnused(id)) {
            throw new IllegalStateException("存储源不存在，或仍有媒体库、作品使用它，请先移除相关记录");
        }
    }

    public StorageSource createLocal(String name, String targetPath) {
        String path = normalizeLocalPath(targetPath);
        StorageSource source = new StorageSource(nonBlankName(name, path), StorageType.LOCAL, path, null, null);
        if (!sourceDao.add(source)) throw new IllegalStateException("保存本地存储源失败");
        return source;
    }

    public StorageSource createOpenList(String name, String baseUrl, String token, String targetPath) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("OpenList 令牌不能为空");
        String normalizedUrl = normalizeBaseUrl(baseUrl);
        StorageCredential credential = new StorageCredential("BEARER", token.trim());
        if (!credentialDao.add(credential)) throw new IllegalStateException("保存 OpenList 凭据失败");
        StorageSource source = new StorageSource(nonBlankName(name, normalizedUrl), StorageType.OPENLIST,
                normalizeRemotePath(targetPath), toConfig(Map.of("baseUrl", normalizedUrl)), credential.getId());
        if (!sourceDao.add(source)) throw new IllegalStateException("保存 OpenList 存储源失败");
        return source;
    }

    public OpenListAccess getOpenListAccess(StorageSource source) {
        if (source == null || source.getType() != StorageType.OPENLIST) {
            throw new IllegalArgumentException("存储源不是 OpenList 类型");
        }
        if (source.getCredentialId() == null) throw new IllegalStateException("OpenList 存储源缺少令牌");
        StorageCredential credential = credentialDao.getById(source.getCredentialId());
        if (credential == null || credential.getSecret() == null || credential.getSecret().isBlank()) {
            throw new IllegalStateException("OpenList 存储源令牌不可用");
        }
        String baseUrl = config(source).get("baseUrl");
        if (baseUrl == null || baseUrl.isBlank()) throw new IllegalStateException("OpenList 存储源地址不可用");
        return new OpenListAccess(baseUrl, credential.getSecret());
    }

    public String getOpenListBaseUrl(StorageSource source) {
        if (source == null || source.getType() != StorageType.OPENLIST) {
            throw new IllegalArgumentException("存储源不是 OpenList 类型");
        }
        String baseUrl = config(source).get("baseUrl");
        return baseUrl == null ? "" : baseUrl;
    }

    public StorageSource updateLocal(StorageSource source, String name, String targetPath) {
        if (source == null || source.getId() == null || source.getType() != StorageType.LOCAL) {
            throw new IllegalArgumentException("本地存储源不存在或类型不匹配");
        }
        String path = normalizeLocalPath(targetPath);
        source.setName(nonBlankName(name, path));
        source.setTargetPath(path);
        source.setStatus(StorageSourceStatus.ACTIVE);
        source.setLastError(null);
        if (!sourceDao.update(source)) throw new IllegalStateException("更新本地存储源失败");
        return source;
    }

    public StorageSource updateOpenList(StorageSource source, String name, String baseUrl, String token, String targetPath) {
        if (source == null || source.getId() == null || source.getType() != StorageType.OPENLIST) {
            throw new IllegalArgumentException("OpenList 存储源不存在或类型不匹配");
        }
        String normalizedUrl = normalizeBaseUrl(baseUrl);
        if (token != null && !token.isBlank()) {
            if (source.getCredentialId() == null) {
                StorageCredential credential = new StorageCredential("BEARER", token.trim());
                if (!credentialDao.add(credential)) throw new IllegalStateException("保存 OpenList 凭据失败");
                source.setCredentialId(credential.getId());
            } else {
                StorageCredential credential = new StorageCredential("BEARER", token.trim());
                credential.setId(source.getCredentialId());
                if (!credentialDao.update(credential)) throw new IllegalStateException("更新 OpenList 令牌失败");
            }
            source.setStatus(StorageSourceStatus.ACTIVE);
            source.setLastError(null);
        } else if (source.getCredentialId() == null) {
            throw new IllegalArgumentException("OpenList 令牌不能为空");
        }
        source.setName(nonBlankName(name, normalizedUrl));
        source.setTargetPath(normalizeRemotePath(targetPath));
        source.setConnectionConfig(toConfig(Map.of("baseUrl", normalizedUrl)));
        if (!sourceDao.update(source)) throw new IllegalStateException("更新 OpenList 存储源失败");
        return source;
    }

    public void updateStatus(int sourceId, StorageSourceStatus status, String error) {
        sourceDao.updateStatus(sourceId, status, error);
    }

    public boolean containsPath(StorageSource source, String path) {
        if (source.getType() == StorageType.LOCAL) {
            try {
                return Path.of(path).toRealPath().startsWith(Path.of(source.getTargetPath()).toRealPath());
            } catch (IOException e) {
                return false;
            }
        }
        String root = normalizeRemotePath(source.getTargetPath());
        String candidate = normalizeRemotePath(path);
        return root.equals("/") || candidate.equals(root) || candidate.startsWith(root + "/");
    }

    public String normalizeLocalPath(String path) {
        if (path == null || path.isBlank()) throw new IllegalArgumentException("本地目录不能为空");
        try {
            return Path.of(path).toRealPath().toString();
        } catch (IOException e) {
            throw new IllegalArgumentException("本地目录不可访问: " + path, e);
        }
    }

    public String normalizeRemotePath(String path) {
        if (path == null || path.isBlank()) return "/";
        String value = "/" + path.trim().replace('\\', '/').replaceAll("^/+", "");
        for (String segment : value.split("/")) {
            if ("..".equals(segment)) throw new IllegalArgumentException("远程路径不能包含上级目录");
        }
        return value.length() > 1 && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) throw new IllegalArgumentException("OpenList 地址不能为空");
        String value = baseUrl.trim();
        if (!value.startsWith("http://") && !value.startsWith("https://")) value = "http://" + value;
        return value.replaceAll("/+$", "");
    }

    private Map<String, String> config(StorageSource source) {
        if (source.getConnectionConfig() == null || source.getConnectionConfig().isBlank()) return Map.of();
        try {
            return MAPPER.readValue(source.getConnectionConfig(), new TypeReference<LinkedHashMap<String, String>>() {});
        } catch (IOException e) {
            throw new IllegalStateException("存储源连接配置无效", e);
        }
    }

    private String toConfig(Map<String, String> config) {
        try {
            return MAPPER.writeValueAsString(config);
        } catch (IOException e) {
            throw new IllegalStateException("序列化存储源连接配置失败", e);
        }
    }

    private String nonBlankName(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
