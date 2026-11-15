package cn.tealc.asmr.player;

import cn.tealc.asmr.utils.AppPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * VLC native 库加载器。
 * <p>
 * 在 VlcPlayer 构造前调用 {@link #setup()}，检测内置的 vlc/ 目录。
 * 若对应平台的 native 库存在，设置 JNA 搜索路径，
 * 使程序在未安装 VLC 的机器上也能播放。
 * <p>
 * 目录结构：
 * <pre>
 * vlc/
 *   windows-amd64/
 *     libvlc.dll
 *     libvlccore.dll
 *     plugins/          (VLC 编解码器插件)
 *   macos-aarch64/
 *     lib/
 *       libvlc.dylib
 *       libvlccore.dylib
 *     plugins/
 *   macos-x86_64/       (同上)
 *   linux-amd64/
 *     libvlc.so.5
 *     libvlccore.so.9
 *     lib/              (VLC 依赖库，RPATH 指向 $ORIGIN)
 *     plugins/
 * </pre>
 * macOS 的插件路径由 vlcj 按 {@code <库目录>/../plugins} 推导，
 * 因此 jna.library.path 必须指向 {@code lib/} 子目录；
 * Linux 插件位于库目录的 {@code plugins/} 下。
 * vlc/ 目录按以下顺序查找，不再依赖启动时的工作目录：
 * <ol>
 *   <li>{@code -Dvlc.dir} 指定的目录（或其下的平台子目录）</li>
 *   <li>开发环境项目根目录下的 {@code vlc/}</li>
 *   <li>jpackage 安装目录下的 {@code vlc/} 或 {@code app/vlc/}</li>
 *   <li>以 jar 方式运行时 jar 所在目录下的 {@code vlc/}</li>
 * </ol>
 * 若均不存在，回退到 JNA 默认发现机制（系统已安装的 VLC）。
 *
 * @author Leck
 */
public class VlcNativeLoader {

    private static final Logger LOG = LoggerFactory.getLogger(VlcNativeLoader.class);

    /** 标记是否已执行过 setup，避免重复设置 */
    private static boolean initialized = false;

    /**
     * 检测并设置内置 VLC native 库路径。
     * <p>
     * 必须在 {@code new MediaPlayerFactory(...)} 之前调用，
     * 因为 JNA 在首次加载 native 库时解析搜索路径。
     */
    public static void setup() {
        if (initialized) {
            return;
        }
        initialized = true;

        File libraryDir = resolveLibraryDir();
        if (libraryDir == null) {
            LOG.info("未找到内置 VLC native 库，将使用系统已安装的 VLC");
            return;
        }

        String dirPath = libraryDir.getAbsolutePath();

        // 设置 JNA 库搜索路径（JNA 读 jna.library.path 系统属性）
        String existing = System.getProperty("jna.library.path", "");
        if (existing.isEmpty()) {
            System.setProperty("jna.library.path", dirPath);
        } else {
            System.setProperty("jna.library.path", existing + File.pathSeparator + dirPath);
        }

        // 插件目录由 vlcj 发现到该库目录后设置 VLC_PLUGIN_PATH（Linux: plugins/，macOS: ../plugins）。

        LOG.info("已加载内置 VLC native 库: {}", dirPath);
    }

    /**
     * 解析实际包含 native 库的目录（可能位于平台目录或平台目录下的 lib/）。
     *
     * @return 包含 libvlc native 库的目录，null 表示不支持的平台或未找到内置库
     */
    private static File resolveLibraryDir() {
        String platformDir = platformDirName();
        if (platformDir == null) {
            return null;
        }

        String override = System.getProperty("vlc.dir", "").trim();
        if (!override.isEmpty()) {
            File dir = new File(override);
            File found = findLibraryDir(dir);
            if (found != null) {
                return found;
            }
            File platform = new File(dir, platformDir);
            found = findLibraryDir(platform);
            if (found != null) {
                return found;
            }
            LOG.warn("-Dvlc.dir 指向的目录缺少 {} native 库: {}", platformDir, dir.getAbsolutePath());
        }

        for (File root : vlcRootCandidates()) {
            File found = findLibraryDir(new File(root, platformDir));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static File findLibraryDir(File base) {
        if (!base.isDirectory()) {
            return null;
        }
        if (hasLibraryFile(base)) {
            return base;
        }
        File lib = new File(base, "lib");
        return hasLibraryFile(lib) ? lib : null;
    }

    /** 检查目录中是否存在 libvlc 主库（而非任意动态库，避免误判） */
    private static boolean hasLibraryFile(File dir) {
        if (new File(dir, "libvlc.dll").isFile()) {
            return true;
        }
        if (new File(dir, "libvlc.dylib").isFile()) {
            return true;
        }
        File[] soFiles = dir.listFiles((d, name) -> name.matches("libvlc\\.so(\\.\\d)*"));
        return soFiles != null && soFiles.length > 0;
    }

    /**
     * 可能包含 vlc/ 子目录的根目录，按优先级排列。
     */
    private static List<File> vlcRootCandidates() {
        List<File> candidates = new ArrayList<>();
        File projectRoot = AppPaths.projectRoot();
        if (projectRoot != null) {
            candidates.add(new File(projectRoot, "vlc"));
        }
        File appDir = AppPaths.appDir();
        if (appDir != null) {
            candidates.add(new File(appDir, "vlc"));
            candidates.add(new File(new File(appDir, "app"), "vlc"));
        }
        return candidates;
    }

    /**
     * 按当前操作系统和 CPU 架构得到平台子目录名。
     *
     * @return 平台目录名，null 表示不支持的平台
     */
    private static String platformDirName() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "");

        if (osName.contains("windows")) {
            return arch.contains("64") ? "windows-amd64" : "windows-x86";
        }
        if (osName.contains("mac")) {
            return arch.contains("aarch64") ? "macos-aarch64" : "macos-x86_64";
        }
        if (osName.contains("linux")) {
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                return "linux-aarch64";
            }
            return arch.contains("64") ? "linux-amd64" : "linux-x86";
        }
        return null;
    }
}
