package cn.tealc.asmr.source;

import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.service.CacheService;
import cn.tealc.asmr.source.openlist.OpenListClient;

import java.util.List;

/**
 * {@link MediaSource} 缓存装饰器。
 * <p>
 * 包装任意远程 {@link MediaSource} 实现，在 {@link #readTextFile} 和
 * {@link #readBinaryFile} 上增加磁盘缓存：命中缓存直接读本地，未命中才委托
 * 被包装的 source 下载。其余方法全部直接委托，不改变原有行为。
 * <p>
 * 装饰器模式使缓存逻辑与具体 MediaSource 实现解耦：
 * <ul>
 *   <li>未来新增 WebDAV、S3 等远程实现，工厂用本装饰器包装即自动获得缓存能力</li>
 *   <li>本地来源（{@link cn.tealc.asmr.source.local.LocalMediaSource}）不包装，无缓存开销</li>
 *   <li>缓存开关关闭时工厂不包装，返回原始 source，零开销</li>
 * </ul>
 *
 * @author Leck
 */
public class CachedMediaSource implements MediaSource {

    /** 被包装的真实 source */
    private final MediaSource delegate;

    /** 来源标识（如 {@code "openlist:http://host:port"}），参与缓存键生成，区分不同服务器 */
    private final String sourceIdentifier;

    /** 缓存服务，DI 惰性获取 */
    private final CacheService cacheService;

    /**
     * 构造缓存装饰器。
     *
     * @param delegate          被包装的真实 source
     * @param sourceIdentifier  来源标识，参与缓存键生成
     */
    public CachedMediaSource(MediaSource delegate, String sourceIdentifier) {
        this.delegate = delegate;
        this.sourceIdentifier = sourceIdentifier;
        this.cacheService = DI.get(CacheService.class);
    }

    // ===================== 缓存读取 =====================

    @Override
    public String readTextFile(String filePath) {
        String ext = OpenListClient.getExtension(filePath);
        return cacheService.readText(sourceIdentifier, filePath, ext,
                () -> delegate.readTextFile(filePath));
    }

    @Override
    public byte[] readBinaryFile(String filePath) {
        String ext = OpenListClient.getExtension(filePath);
        return cacheService.readBinary(sourceIdentifier, filePath, ext,
                () -> delegate.readBinaryFile(filePath));
    }

    // ===================== 委托方法 =====================

    @Override
    public List<AsmrFile> listFiles(String dirPath) {
        return delegate.listFiles(dirPath);
    }

    @Override
    public List<AsmrFile> listFiles(String dirPath, boolean forceRefresh) {
        return delegate.listFiles(dirPath, forceRefresh);
    }

    @Override
    public List<AsmrFile> listAudioFiles(String dirPath) {
        return delegate.listAudioFiles(dirPath);
    }

    @Override
    public List<AsmrFile> listFilesRecursive(String dirPath, String extension) {
        return delegate.listFilesRecursive(dirPath, extension);
    }

    @Override
    public List<AsmrFile> listFilesRecursive(String dirPath, String[] extensions) {
        return delegate.listFilesRecursive(dirPath, extensions);
    }

    @Override
    public List<AsmrFile> listFilesRecursive(String dirPath, String[] extensions, String skipDir) {
        return delegate.listFilesRecursive(dirPath, extensions, skipDir);
    }

    @Override
    public boolean exists(String path) {
        return delegate.exists(path);
    }

    @Override
    public boolean isDirectory(String path) {
        return delegate.isDirectory(path);
    }

    @Override
    public String resolvePlayUrl(String filePath) {
        return delegate.resolvePlayUrl(filePath);
    }

    @Override
    public String resolveThumbnailUrl(String filePath) {
        return delegate.resolveThumbnailUrl(filePath);
    }

    @Override
    public boolean delete(String path) {
        boolean deleted = delegate.delete(path);
        if (deleted) {
            cacheService.invalidatePrefix(sourceIdentifier, path);
        }
        return deleted;
    }

    @Override
    public boolean rename(String path, String newName) {
        boolean renamed = delegate.rename(path, newName);
        if (renamed) {
            cacheService.invalidatePrefix(sourceIdentifier, path);
        }
        return renamed;
    }

    @Override
    public boolean move(String srcPath, String destDirPath) {
        boolean moved = delegate.move(srcPath, destDirPath);
        if (moved) {
            cacheService.invalidatePrefix(sourceIdentifier, srcPath);
        }
        return moved;
    }

    @Override
    public boolean createDirectory(String path) {
        return delegate.createDirectory(path);
    }

    @Override
    public boolean writeTextFile(String filePath, String content) {
        return delegate.writeTextFile(filePath, content);
    }

    @Override
    public boolean openInSystem(String path) {
        return delegate.openInSystem(path);
    }

    @Override
    public StorageType getType() {
        return delegate.getType();
    }
}
