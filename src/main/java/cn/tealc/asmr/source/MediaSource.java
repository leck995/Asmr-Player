package cn.tealc.asmr.source;

import cn.tealc.asmr.model.AsmrFile;

import java.util.List;

/**
 * 统一文件访问接口。
 * <p>
 * 屏蔽底层文件来源（本地磁盘、Alist/OpenList 等）的差异，为上层播放器、文件浏览器、
 * 歌词加载等逻辑提供统一的文件读写 API。上层通过 {@link MediaSourceFactory} 获取实例，
 * 不直接依赖 {@code java.io.File} 或具体的 HTTP 客户端。
 * <p>
 * 当前已有实现：
 * <ul>
 *     <li>{@code LocalMediaSource} —— 本地磁盘，封装 {@code java.io.File} 操作</li>
 *     <li>{@code OpenListMediaSource} —— Alist/OpenList，通过远程 API 读写</li>
 * </ul>
 *
 * @author Leck
 * @see MediaSourceFactory
 * @see StorageType
 */
public interface MediaSource {

    // ===================== 读取 =====================

    /**
     * 列出目录下所有文件和子目录（不做格式过滤）。
     *
     * @param dirPath 目录路径
     * @return 文件列表，目录不存在或为空时返回空列表
     */
    List<AsmrFile> listFiles(String dirPath);

    /**
     * 列出目录内容，可选择是否强制刷新来源侧缓存。
     * <p>
     * 远程来源（如 OpenList）可据此避免浏览时反复回源；本地来源没有远端缓存，
     * 默认实现忽略该参数。
     *
     * @param dirPath      目录路径
     * @param forceRefresh 为 {@code true} 时要求来源丢弃自身目录缓存并重新列举
     * @return 文件列表，目录不存在或为空时返回空列表
     */
    default List<AsmrFile> listFiles(String dirPath, boolean forceRefresh) {
        return listFiles(dirPath);
    }

    /**
     * 列出目录下的音频文件（按 {@code SupportedAudioFormat} 过滤）。
     *
     * @param dirPath 目录路径
     * @return 音频文件列表，目录不存在或无音频时返回空列表
     */
    List<AsmrFile> listAudioFiles(String dirPath);

    /**
     * 递归列出目录下所有指定后缀的文件（不区分大小写）。
     * <p>
     * 用于歌词查找等需要全目录递归的场景。
     *
     * @param dirPath   目录路径
     * @param extension 目标后缀（不含点，如 {@code "lrc"}、{@code "vtt"}）
     * @return 匹配的文件列表
     */
    List<AsmrFile> listFilesRecursive(String dirPath, String extension);

    /**
     * 递归列出目录下所有指定后缀的文件（不区分大小写），一次遍历同时收集多种后缀。
     * <p>
     * 用于字幕查找场景，避免对同一目录树重复遍历多次（如 lrc/vtt/srt 各一遍）。
     * 默认实现回退到单后缀版本，由具体实现重写以获得性能优势。
     *
     * @param dirPath    目录路径
     * @param extensions 目标后缀数组（不含点，如 {@code {"lrc","vtt","srt"}}）
     * @return 匹配的文件列表
     */
    default List<AsmrFile> listFilesRecursive(String dirPath, String[] extensions) {
        return listFilesRecursive(dirPath, extensions[0]);
    }

    /**
     * 递归列出目录下所有指定后缀的文件，跳过指定子目录。
     * <p>
     * 用于字幕查找场景：第 1 级已列举了音频同目录，第 2 级递归时跳过该目录避免重复请求。
     * 默认实现回退到单后缀版本，由具体实现重写以获得性能优势。
     *
     * @param dirPath    目录路径
     * @param extensions 目标后缀数组（不含点，如 {@code {"lrc","vtt","srt"}}）
     * @param skipDir    要跳过的子目录路径（如音频所在目录），为 null 不跳过
     * @return 匹配的文件列表
     */
    default List<AsmrFile> listFilesRecursive(String dirPath, String[] extensions, String skipDir) {
        return listFilesRecursive(dirPath, extensions[0]);
    }

    /**
     * 读取文本文件内容。
     *
     * @param filePath 文件路径
     * @return 文件文本内容，文件不存在时返回 {@code null}
     */
    String readTextFile(String filePath);

    /**
     * 读取二进制文件内容，返回 byte 数组。
     * <p>
     * 用于图片等非文本文件的读取。本地来源直接读磁盘，远程来源通过代理下载。
     *
     * @param filePath 文件路径
     * @return 文件二进制内容，文件不存在返回 {@code null}
     */
    byte[] readBinaryFile(String filePath);

    /**
     * 判断文件或目录是否存在。
     *
     * @param path 路径
     * @return 存在返回 {@code true}
     */
    boolean exists(String path);

    /**
     * 判断路径是否为目录。
     *
     * @param path 路径
     * @return 是目录返回 {@code true}
     */
    boolean isDirectory(String path);

    /**
     * 获取可播放的 URL。
     * <p>
     * 本地来源：普通文件直接返回磁盘路径；{@code .strm} 文件返回其内部记录的 HTTP URL。
     * 远程来源：返回可由播放器直接请求的代理播放地址（而非按申请方 UA 签发的网盘直链）。
     *
     * @param filePath 文件路径（来自 {@code AsmrFile.getFilepath()} 或 {@code AudioTrack.getUrl()}）
     * @return VLC 可播放的 URL
     */
    String resolvePlayUrl(String filePath);

    /**
     * 获取文件缩略图的直链地址。
     * <p>
     * 远程来源可返回带时效的缩略图 URL（如 OpenList 驱动返回的 {@code thumb}）；
     * 地址可能过期，调用方不应持久化，下载失败时重新解析。本地来源没有缩略图。
     *
     * @param filePath 文件路径
     * @return 缩略图 URL；不支持或不可用时返回 {@code null}
     */
    default String resolveThumbnailUrl(String filePath) {
        return null;
    }

    // ===================== 写入 =====================

    /**
     * 永久删除文件或目录。
     * <p>
     * 远程来源在 API 调用失败时可能抛出运行时异常（携带服务端错误信息），
     * 上层业务建议通过 {@code FileOperationService} 统一转换为可展示的结果。
     *
     * @param path 文件/目录路径
     * @return 删除成功返回 {@code true}
     */
    boolean delete(String path);

    /**
     * 重命名文件或目录（同目录内改名）。
     *
     * @param path    原路径
     * @param newName 新名称（不含目录部分）
     * @return 重命名成功返回 {@code true}
     */
    boolean rename(String path, String newName);

    /**
     * 移动文件或目录到目标目录。
     *
     * @param srcPath    源路径
     * @param destDirPath 目标目录路径
     * @return 移动成功返回 {@code true}
     */
    boolean move(String srcPath, String destDirPath);

    /**
     * 创建目录（含父目录）。
     *
     * @param path 目录路径
     * @return 目录已存在或创建成功返回 {@code true}
     */
    boolean createDirectory(String path);

    /**
     * 写入文本文件（覆盖写入）。
     *
     * @param filePath 文件路径
     * @param content  文本内容
     * @return 写入成功返回 {@code true}
     */
    boolean writeTextFile(String filePath, String content);

    /**
     * 用系统默认程序打开文件或文件夹（仅本地来源有效，远程来源返回 {@code false}）。
     *
     * @param path 文件/目录路径
     * @return 打开成功返回 {@code true}
     */
    boolean openInSystem(String path);

    // ===================== 来源标识 =====================

    /**
     * 获取此实现的来源类型。
     *
     * @return 存储类型枚举
     */
    StorageType getType();
}
