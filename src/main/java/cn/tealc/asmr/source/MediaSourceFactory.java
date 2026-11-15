package cn.tealc.asmr.source;

import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.StorageSource;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.service.StorageSourceService;
import cn.tealc.asmr.source.local.LocalMediaSource;
import cn.tealc.asmr.source.openlist.OpenListMediaSource;

/**
 * {@link MediaSource} 工厂。
 * <p>
 * 根据作品的存储类型（{@code AsmrWork.storageType}）分派到对应的 {@code MediaSource} 实现。
 * 上层调用方通过此工厂获取文件访问实例，不直接依赖具体实现。
 * <p>
 * 当前已注册：
 * <ul>
 *     <li>{@link StorageType#LOCAL} —— {@link LocalMediaSource}（单例）</li>
 *     <li>{@link StorageType#OPENLIST} —— {@link OpenListMediaSource}（单例），开启缓存时用 {@link CachedMediaSource} 包装</li>
 * </ul>
 * <p>
 * 远程来源在缓存开关（{@code Config.setting.cacheEnable}）开启时，返回的实例会被
 * {@link CachedMediaSource} 装饰，自动具备磁盘缓存能力；关闭时返回原始实例，零开销。
 *
 * @author Leck
 */
public final class MediaSourceFactory {

    private static final LocalMediaSource LOCAL = LocalMediaSource.INSTANCE;
    private MediaSourceFactory() {
    }

    /**
     * 根据作品的存储类型获取对应的 {@link MediaSource}。
     *
     * @param work 媒体作品
     * @return 该作品所属来源的文件访问实例
     */
    public static MediaSource forWork(AsmrWork work) {
        if (work == null || work.getStorageSourceId() == null) {
            throw new IllegalArgumentException("作品未关联存储源");
        }
        return forStorageSourceId(work.getStorageSourceId());
    }

    /** 根据存储源 ID 获取文件访问实例。 */
    public static MediaSource forStorageSourceId(int storageSourceId) {
        StorageSource source = DI.get(StorageSourceService.class).getById(storageSourceId);
        if (source == null) {
            throw new IllegalStateException("存储源不存在: " + storageSourceId);
        }
        return forStorageSource(source);
    }

    /** 根据完整存储源配置获取文件访问实例。 */
    public static MediaSource forStorageSource(StorageSource source) {
        if (source.getStatus() != cn.tealc.asmr.model.StorageSourceStatus.ACTIVE) {
            throw new IllegalStateException("存储源当前不可用: " + source.getStatus());
        }
        return switch (source.getType()) {
            case LOCAL -> LOCAL;
            case OPENLIST -> {
                OpenListMediaSource openList = new OpenListMediaSource(source.getId(),
                        DI.get(StorageSourceService.class).getOpenListAccess(source),
                        source.getTargetPath());
                yield wrapIfCacheEnabled(openList, "openlist:" + source.getId());
            }
        };
    }

    /**
     * 缓存开启时用 {@link CachedMediaSource} 包装 source，否则返回原始 source。
     *
     * @param source          被包装的真实 source
     * @param sourceIdentifier 来源标识，参与缓存键生成
     * @return 装饰后的 source（缓存开启）或原始 source（缓存关闭）
     */
    private static MediaSource wrapIfCacheEnabled(MediaSource source, String sourceIdentifier) {
        if (Config.setting.cacheEnable.get()) {
            return new CachedMediaSource(source, sourceIdentifier);
        }
        return source;
    }
}
