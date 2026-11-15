package cn.tealc.asmr.utils;

import cn.tealc.asmr.model.AsmrWork;
import javafx.scene.image.Image;

import java.io.File;

/**
 * 封面图查找工具。
 * <p>
 * 查找顺序：app 缓存目录 → 作品目录 → 兜底图。
 * <p>
 * app 缓存路径：{@code <dataDir>/cover/<subDir>/<workId>.{png,jpg,jpeg}}<br>
 * 作品目录路径：{@code <workPath>/<coverName>.{png,jpg,jpeg}}
 *
 * @author Leck
 */
public final class CoverUtils {

    private static final String[] EXTENSIONS = {".png", ".jpg", ".jpeg"};

    private CoverUtils() {
    }

    /**
     * 下载、读取和清理封面共用的应用缓存目录。
     */
    public static File getCacheDirectory(String subDir) {
        return AppPaths.coverDir(subDir);
    }

    /**
     * 从 app 缓存目录查找封面图。
     *
     * @param workId     作品编号
     * @param subDir 子目录（{@code "main"} 或 {@code "thumb"}）
     * @param width  目标宽度
     * @param height 目标高度
     * @param smooth 是否平滑缩放
     * @return 图片，不存在返回 null
     */
    public static Image getFromAppDir(String workId, String subDir, int width, int height, boolean smooth) {
        if (workId == null || workId.isBlank()) {
            return null;
        }
        String basePath = new File(getCacheDirectory(subDir), workId).getPath();
        return findImage(basePath, width, height, smooth);
    }

    /**
     * 从作品目录查找封面图。
     *
     * @param workPath  作品目录路径
     * @param coverName 封面文件名（{@code "cover-main"} 或 {@code "cover-thumb"}）
     * @param width     目标宽度
     * @param height    目标高度
     * @param smooth    是否平滑缩放
     * @return 图片，不存在返回 null
     */
    public static Image getFromWorkDir(String workPath, String coverName, int width, int height, boolean smooth) {
        if (workPath == null || workPath.isBlank()) {
            return null;
        }
        String basePath = workPath + File.separator + coverName;
        return findImage(basePath, width, height, smooth);
    }

    /**
     * 查找封面图：先查 app 缓存目录，找不到查作品目录，都找不到返回兜底图。
     *
     * @param workId           作品编号
     * @param workPath     作品目录路径
     * @param subDir       app 缓存子目录（{@code "main"} 或 {@code "thumb"}）
     * @param coverName    作品目录封面文件名（{@code "cover-main"} 或 {@code "cover-thumb"}）
     * @param width        目标宽度
     * @param height       目标高度
     * @param smooth       是否平滑缩放
     * @param defaultImage 兜底图
     * @return 封面图
     */
    public static Image getCover(String workId, String workPath, String subDir, String coverName,
                                 int width, int height, boolean smooth, Image defaultImage) {
        Image image = getFromAppDir(workId, subDir, width, height, smooth);
        if (image != null) {
            return image;
        }
        image = getFromWorkDir(workPath, coverName, width, height, smooth);
        if (image != null) {
            return image;
        }
        return defaultImage;
    }

    /**
     * 按作品来源类型查找封面图。
     * <p>
     * 本地作品（storageType=0）走完整查找链：作品封面缓存 → 作品目录 → 默认图。<br>
     * 网络作品（storageType!=0）只查 作品封面缓存，查不到返回默认图。
     *
     * @param work         作品对象（含 workId、filepath、storageType）
     * @param subDir       app 缓存子目录（{@code "main"} 或 {@code "thumb"})
     * @param coverName    作品目录封面文件名（{@code "cover-main"} 或 {@code "cover-thumb"}）
     * @param width        目标宽度
     * @param height       目标高度
     * @param smooth       是否平滑缩放
     * @param defaultImage 兜底图
     * @return 封面图
     */
    public static Image getCoverForWork(AsmrWork work, String subDir, String coverName,
                                        int width, int height, boolean smooth, Image defaultImage) {
        // 1. 先查本地 作品封面缓存（本地和远程都查）
        Image cached = getFromAppDir(work.getWorkId(), subDir, width, height, smooth);
        if (cached != null) {
            return cached;
        }
        // 2. 本地作品再查作品目录
        if (Integer.valueOf(0).equals(work.getStorageType())) {
            Image fromWorkDir = getFromWorkDir(work.getFilepath(), coverName, width, height, smooth);
            if (fromWorkDir != null) {
                return fromWorkDir;
            }
        }
        // 3. 网络作品没有其他来源，返回默认图
        return defaultImage;
    }

    /**
     * 在指定基路径下按扩展名依次查找图片。
     */
    private static Image findImage(String basePath, int width, int height, boolean smooth) {
        for (String ext : EXTENSIONS) {
            File file = new File(basePath + ext);
            if (file.exists()) {
                return new Image("file:" + file.toURI().getPath(), width, height, true, smooth, true);
            }
        }
        return null;
    }
}
