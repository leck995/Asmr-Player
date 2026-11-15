package cn.tealc.asmr.service;

import cn.tealc.asmr.dao.WorkBackgroundDao;
import cn.tealc.asmr.di.Inject;
import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.BackgroundRef;
import cn.tealc.asmr.model.SupportedImageFormat;
import cn.tealc.asmr.model.WorkBackgroundRecord;
import cn.tealc.asmr.source.MediaSource;
import cn.tealc.asmr.source.MediaSourceFactory;
import cn.tealc.asmr.source.StorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 作品背景服务。
 * <p>
 * 负责背景的查找、读取与记录，不依赖 JavaFX：统一返回路径引用和字节数据，
 * 由 ViewModel 负责创建 {@code Image}。
 * <p>
 * 每部作品在 {@code asmr_work_background} 中最多记录当前使用的一张背景。
 * 进入作品时直接按记录读取（在线走缓存/下载，本地直接读文件），没有记录或读取
 * 失败时才遍历作品目录重新匹配。记录为空（{@code source_path} 为 null）表示
 * 已扫描但没有可用背景，同样避免重复扫描。
 *
 * @author Leck
 */
public class BackgroundService {

    private static final Logger LOG = LoggerFactory.getLogger(BackgroundService.class);

    /** 空结果/记录的复查间隔，避免外部新增背景后永远看不到 */
    private static final long EMPTY_SCAN_TTL_MS = 7L * 24 * 60 * 60 * 1000;

    /** 递归查找图片时的最小文件大小 */
    private static final long MIN_IMAGE_SIZE = 200_000L;

    @Inject
    private WorkBackgroundDao dao;

    /** 同一作品的扫描互斥，避免多个 ViewModel 同时重复请求 */
    private final ConcurrentHashMap<Integer, Object> scanLocks = new ConcurrentHashMap<>();

    /**
     * 加载作品当前背景。
     * <p>
     * 先读记录并尝试读取文件；记录不存在、已过期或读取失败时重新扫描目录，
     * 选中可用图片后写回记录。扫描失败时保留原记录。
     *
     * @param work 作品
     * @return 加载结果，无可用背景返回 null
     */
    public LoadedBackground load(AsmrWork work) {
        if (work == null || work.getFilepath() == null || work.getFilepath().isBlank()) {
            return null;
        }
        if (work.getId() <= 0) {
            return loadWithoutRecord(work);
        }

        synchronized (scanLock(work.getId())) {
            WorkBackgroundRecord record = findRecord(work.getId());
            if (record != null && record.sourcePath() != null) {
                BackgroundRef ref = toRef(record);
                byte[] bytes = read(work, ref);
                if (bytes != null) {
                    return new LoadedBackground(ref, bytes);
                }
                LOG.warn("背景记录读取失败，准备重新扫描: workId={}, sourceId={}, path={}",
                        work.getId(), record.storageSourceId(), record.sourcePath());
            } else if (record != null && !isExpired(record.scannedAt())) {
                return null;
            }
            return rescanAndLoad(work, record);
        }
    }

    /**
     * 遍历作品目录，返回所有候选背景。用于切换背景或选择器按需加载。
     * <p>
     * 该方法会访问网络，调用方应放在后台线程，并自行处理扫描失败。
     *
     * @param work 作品
     * @return 候选背景列表，目录中无图片时返回空列表
     */
    public List<BackgroundRef> scanCandidates(AsmrWork work) {
        if (work == null || work.getFilepath() == null || work.getFilepath().isBlank()) {
            return List.of();
        }
        return scan(work);
    }

    /**
     * 读取指定背景的字节内容。
     * <p>
     * 在线背景通过记录的存储源读取（缓存命中直接读盘，否则下载）；本地背景
     * 直接读文件。读取失败返回 null。
     *
     * @param work 作品，用于在线来源不可用时回退
     * @param ref  背景引用
     * @return 图片字节，失败返回 null
     */
    public byte[] read(AsmrWork work, BackgroundRef ref) {
        if (ref == null || ref.path() == null || ref.path().isBlank()) {
            return null;
        }
        try {
            MediaSource source = resolveSource(work, ref);
            return source.readBinaryFile(ref.path());
        } catch (RuntimeException e) {
            LOG.warn("读取背景失败: workId={}, online={}, sourceId={}, path={}",
                    work == null ? 0 : work.getId(), ref.online(), ref.storageSourceId(), ref.path(), e);
            return null;
        }
    }

    /**
     * 记录作品当前使用的背景，后续进入直接按此读取。
     *
     * @param work 作品
     * @param ref  背景引用
     */
    public void remember(AsmrWork work, BackgroundRef ref) {
        if (work == null || ref == null || work.getId() <= 0) {
            return;
        }
        saveRecord(work, ref, System.currentTimeMillis());
    }

    /**
     * 清除作品背景记录，下次进入重新扫描目录。
     *
     * @param work 作品
     */
    public void invalidate(AsmrWork work) {
        if (work == null || work.getId() <= 0) {
            return;
        }
        try {
            dao.deleteByWorkId(work.getId());
        } catch (RuntimeException e) {
            LOG.warn("清除作品背景记录失败: workId={}", work.getId(), e);
        }
    }

    // ===================== 内部流程 =====================

    private LoadedBackground rescanAndLoad(AsmrWork work, WorkBackgroundRecord oldRecord) {
        List<BackgroundRef> candidates;
        try {
            candidates = scan(work);
        } catch (RuntimeException e) {
            LOG.error("背景目录扫描失败，保留原记录: workId={}, path={}",
                    work.getId(), work.getFilepath(), e);
            return null;
        }

        BackgroundRef preferred = choose(candidates, oldRecord);
        byte[] bytes = preferred == null ? null : read(work, preferred);
        if (bytes == null) {
            for (BackgroundRef candidate : candidates) {
                if (candidate.equals(preferred)) {
                    continue;
                }
                bytes = read(work, candidate);
                if (bytes != null) {
                    preferred = candidate;
                    break;
                }
            }
        }

        BackgroundRef persisted = preferred != null ? preferred
                : (candidates.isEmpty() ? null : candidates.get(0));
        saveRecord(work, persisted, System.currentTimeMillis());

        if (bytes == null) {
            LOG.warn("背景扫描完成但没有可读取的图片: workId={}, path={}, candidates={}",
                    work.getId(), work.getFilepath(), candidates.size());
            return null;
        }
        return new LoadedBackground(preferred, bytes);
    }

    private LoadedBackground loadWithoutRecord(AsmrWork work) {
        try {
            for (BackgroundRef candidate : scan(work)) {
                byte[] bytes = read(work, candidate);
                if (bytes != null) {
                    return new LoadedBackground(candidate, bytes);
                }
            }
        } catch (RuntimeException e) {
            LOG.warn("未入库作品背景加载失败: workId={}, path={}",
                    work.getId(), work.getFilepath(), e);
        }
        return null;
    }

    private BackgroundRef choose(List<BackgroundRef> candidates, WorkBackgroundRecord oldRecord) {
        if (candidates.isEmpty()) {
            return null;
        }
        if (oldRecord != null && oldRecord.sourcePath() != null) {
            for (BackgroundRef candidate : candidates) {
                if (candidate.path().equals(oldRecord.sourcePath())) {
                    return candidate;
                }
            }
        }
        return candidates.get(0);
    }

    private List<BackgroundRef> scan(AsmrWork work) {
        MediaSource source = MediaSourceFactory.forWork(work);
        boolean online = source.getType() == StorageType.OPENLIST;
        Integer sourceId = work.getStorageSourceId();
        String rootPath = work.getFilepath();

        // 1. 当前目录下 background 命名的图片优先
        List<BackgroundRef> bgNamed = new ArrayList<>();
        for (AsmrFile file : source.listFiles(rootPath)) {
            if (!file.isDir()
                    && SupportedImageFormat.contains(file.getType())
                    && startsWithBackground(file)) {
                bgNamed.add(new BackgroundRef(file.getFilepath(), online, sourceId));
            }
        }
        if (!bgNamed.isEmpty()) {
            return bgNamed;
        }

        // 2. 递归查找所有大于 200KB 的支持格式图片
        List<AsmrFile> allImages = new ArrayList<>();
        collectImagesRecursive(source, rootPath, allImages);
        if (allImages.isEmpty()) {
            return List.of();
        }

        // 3. 优先 background 开头的，否则全部返回
        List<BackgroundRef> bgList = new ArrayList<>();
        List<BackgroundRef> others = new ArrayList<>();
        for (AsmrFile file : allImages) {
            BackgroundRef ref = new BackgroundRef(file.getFilepath(), online, sourceId);
            if (startsWithBackground(file)) {
                bgList.add(ref);
            } else {
                others.add(ref);
            }
        }
        return bgList.isEmpty() ? others : bgList;
    }

    private void collectImagesRecursive(MediaSource source, String dirPath, List<AsmrFile> result) {
        for (AsmrFile file : source.listFiles(dirPath)) {
            if (file.isDir()) {
                collectImagesRecursive(source, file.getFilepath(), result);
            } else if (SupportedImageFormat.contains(file.getType()) && file.getSize() > MIN_IMAGE_SIZE) {
                result.add(file);
            }
        }
    }

    private boolean startsWithBackground(AsmrFile file) {
        return file.getFilename() != null
                && file.getFilename().toLowerCase(Locale.ROOT).startsWith("background");
    }

    private MediaSource resolveSource(AsmrWork work, BackgroundRef ref) {
        if (ref.online() && ref.storageSourceId() != null && ref.storageSourceId() > 0) {
            try {
                return MediaSourceFactory.forStorageSourceId(ref.storageSourceId());
            } catch (RuntimeException e) {
                LOG.warn("背景存储源不可用，回退到作品当前存储源: sourceId={}, workId={}",
                        ref.storageSourceId(), work == null ? 0 : work.getId(), e);
            }
        }
        if (work != null) {
            return MediaSourceFactory.forWork(work);
        }
        throw new IllegalStateException("无法解析背景存储源");
    }

    private BackgroundRef toRef(WorkBackgroundRecord record) {
        return new BackgroundRef(record.sourcePath(),
                StorageType.fromCode(record.storageType()) == StorageType.OPENLIST,
                record.storageSourceId());
    }

    private WorkBackgroundRecord findRecord(int workRecordId) {
        try {
            return dao.findByWorkId(workRecordId);
        } catch (RuntimeException e) {
            LOG.warn("读取作品背景记录失败: workId={}", workRecordId, e);
            return null;
        }
    }

    private void saveRecord(AsmrWork work, BackgroundRef ref, long scannedAt) {
        if (work.getId() <= 0) {
            return;
        }
        int storageType = ref != null
                ? (ref.online() ? StorageType.OPENLIST.getCode() : StorageType.LOCAL.getCode())
                : StorageType.fromCode(work.getStorageType()).getCode();
        int sourceId = 0;
        if (ref != null && ref.storageSourceId() != null) {
            sourceId = ref.storageSourceId();
        } else if (work.getStorageSourceId() != null) {
            sourceId = work.getStorageSourceId();
        }
        try {
            dao.save(new WorkBackgroundRecord(work.getId(), storageType, sourceId,
                    ref == null ? null : ref.path(), scannedAt));
        } catch (RuntimeException e) {
            LOG.warn("保存作品背景记录失败: workId={}, sourceId={}, path={}",
                    work.getId(), sourceId, ref == null ? null : ref.path(), e);
        }
    }

    private boolean isExpired(long scannedAt) {
        return System.currentTimeMillis() - scannedAt >= EMPTY_SCAN_TTL_MS;
    }

    private Object scanLock(int workRecordId) {
        return scanLocks.computeIfAbsent(workRecordId, id -> new Object());
    }

    /**
     * 背景加载结果：实际使用的引用及图片字节。
     *
     * @param ref   背景引用
     * @param bytes 图片字节
     */
    public record LoadedBackground(BackgroundRef ref, byte[] bytes) {
    }
}
