package cn.tealc.asmr.utils;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.Locale;

/**
 * 应用路径统一入口。
 * <p>
 * 运行期产生的所有文件（配置、数据库、日志、缓存、封面、存档等）都放在数据目录下，
 * 不再依赖启动时的工作目录。数据目录按运行方式区分：
 * <ul>
 *   <li>开发环境：项目根目录下的 {@code dev-data/}</li>
 *   <li>安装/使用环境：操作系统标准的应用数据目录</li>
 *   <li>{@code -Dasmr.data.dir=<绝对路径>} 可覆盖以上规则</li>
 * </ul>
 * 平台默认位置：
 * <ul>
 *   <li>Windows: {@code %APPDATA%\AsmrPlayer}</li>
 *   <li>macOS: {@code ~/Library/Application Support/AsmrPlayer}</li>
 *   <li>Linux: {@code $XDG_DATA_HOME/AsmrPlayer} 或 {@code ~/.local/share/AsmrPlayer}</li>
 * </ul>
 * 必须在任何 SLF4J logger 初始化前调用 {@link #init()}，否则 logback 读不到日志目录。
 */
public final class AppPaths {

    /** 数据目录覆盖属性，同时供 logback 使用 */
    public static final String DATA_DIR_PROPERTY = "asmr.data.dir";
    /** 日志目录属性，由 {@link #init()} 写入供 logback 使用 */
    public static final String LOG_DIR_PROPERTY = "asmr.log.dir";
    /** jpackage 启动器写入的系统属性，同时用于区分安装环境 */
    public static final String JPACKAGE_APP_PATH_PROPERTY = "jpackage.app-path";

    private static final String APP_DIR_NAME = "AsmrPlayer";
    private static final String DEVELOPMENT_DIR_NAME = "dev-data";

    private static final String SETTINGS_FILE_NAME = "setting.json";
    private static final String DATABASE_FILE_NAME = "data.db";
    private static final String HISTORY_FILE_NAME = "history";
    private static final String BACKGROUND_FILE_NAME = "background";

    private static volatile boolean initialized;
    private static Path dataDir;
    private static File projectRoot;
    private static boolean projectRootResolved;

    private AppPaths() {
    }

    /**
     * 初始化数据目录与日志目录，并写入系统属性。重复调用无副作用。
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        Path resolved = resolveDataDir().toAbsolutePath().normalize();
        try {
            Files.createDirectories(resolved.resolve("logs"));
        } catch (IOException e) {
            throw new UncheckedIOException("无法创建应用数据目录: " + resolved, e);
        }
        dataDir = resolved;
        System.setProperty(DATA_DIR_PROPERTY, resolved.toString());
        System.setProperty(LOG_DIR_PROPERTY, resolved.resolve("logs").toString());
        initialized = true;
    }

    /**
     * @return 当前生效的数据目录
     */
    public static File dataDir() {
        ensureInitialized();
        return dataDir.toFile();
    }

    /**
     * @return 配置文件 {@code setting.json}
     */
    public static File settingsFile() {
        return new File(dataDir(), SETTINGS_FILE_NAME);
    }

    /**
     * @return SQLite 数据库文件
     */
    public static File databaseFile() {
        return new File(dataDir(), DATABASE_FILE_NAME);
    }

    /**
     * @return 日志根目录，供 logback 使用
     */
    public static File logsDir() {
        return new File(dataDir(), "logs");
    }

    /**
     * @param subDir 封面子目录（{@code main} 或 {@code thumb}）
     * @return 封面缓存目录
     */
    public static File coverDir(String subDir) {
        return new File(new File(dataDir(), "cover"), subDir);
    }

    /**
     * @return 远程资源缓存根目录
     */
    public static File cacheDir() {
        return new File(dataDir(), "cache");
    }

    /**
     * @param type 缓存类型（{@code text} 或 {@code binary}）
     * @return 远程资源缓存目录
     */
    public static File cacheDir(String type) {
        return new File(cacheDir(), type);
    }

    /**
     * @return 上次播放记录文件
     */
    public static File historyFile() {
        return new File(dataDir(), HISTORY_FILE_NAME);
    }

    /**
     * @return 用户自定义的 Galgame 背景图文件
     */
    public static File backgroundFile() {
        return new File(dataDir(), BACKGROUND_FILE_NAME);
    }

    /**
     * @return Galgame 存档截图目录
     */
    public static File saveDir() {
        return new File(dataDir(), "save");
    }

    /**
     * @return 媒体库导入报告目录
     */
    public static File importLogsDir() {
        return new File(logsDir(), "import");
    }

    /**
     * @return 元数据同步报告目录
     */
    public static File syncLogsDir() {
        return new File(logsDir(), "sync");
    }

    /**
     * 是否运行在开发环境：由 IDE 或 {@code mvn javafx:run} 从类目录启动，
     * 且能找到包含 {@code pom.xml} 的项目根目录。
     *
     * @return 开发环境返回 true
     */
    public static boolean isDevelopment() {
        if (!System.getProperty(JPACKAGE_APP_PATH_PROPERTY, "").isBlank()) {
            return false;
        }
        File location = codeSourceLocation();
        return location != null && location.isDirectory() && projectRoot() != null;
    }

    /**
     * @return 开发环境的项目根目录，无法识别时返回 null
     */
    public static synchronized File projectRoot() {
        if (projectRootResolved) {
            return projectRoot;
        }
        projectRootResolved = true;
        File location = codeSourceLocation();
        if (location == null) {
            return null;
        }
        File dir = location.isDirectory() ? location : location.getParentFile();
        for (int depth = 0; dir != null && depth < 8; depth++, dir = dir.getParentFile()) {
            if (new File(dir, "pom.xml").isFile()) {
                projectRoot = dir;
                break;
            }
        }
        return projectRoot;
    }

    /**
     * 安装目录：jpackage 启动器所在目录；以 jar 方式运行时为 jar 所在目录。
     *
     * @return 安装目录，开发环境或无法识别时返回 null
     */
    public static File appDir() {
        String appPath = System.getProperty(JPACKAGE_APP_PATH_PROPERTY, "").trim();
        if (!appPath.isEmpty()) {
            File launcher = new File(appPath).getAbsoluteFile();
            return launcher.getParentFile();
        }
        File location = codeSourceLocation();
        if (location != null && location.isFile()) {
            return location.getAbsoluteFile().getParentFile();
        }
        return null;
    }

    private static Path resolveDataDir() {
        String override = System.getProperty(DATA_DIR_PROPERTY, "").trim();
        if (!override.isEmpty()) {
            return Paths.get(override);
        }
        if (isDevelopment()) {
            return projectRoot().toPath().resolve(DEVELOPMENT_DIR_NAME);
        }
        return defaultUserDataDir();
    }

    private static Path defaultUserDataDir() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String userHome = System.getProperty("user.home", ".");
        if (osName.contains("win")) {
            String appData = System.getenv("APPDATA");
            Path base = appData != null && !appData.isBlank()
                    ? Paths.get(appData)
                    : Paths.get(userHome, "AppData", "Roaming");
            return base.resolve(APP_DIR_NAME);
        }
        if (osName.contains("mac")) {
            return Paths.get(userHome, "Library", "Application Support", APP_DIR_NAME);
        }
        String xdgDataHome = System.getenv("XDG_DATA_HOME");
        Path base = xdgDataHome != null && !xdgDataHome.isBlank()
                ? Paths.get(xdgDataHome)
                : Paths.get(userHome, ".local", "share");
        return base.resolve(APP_DIR_NAME);
    }

    /**
     * 当前类所在的位置：开发时为类目录，打包后为 jar 文件。
     */
    private static File codeSourceLocation() {
        try {
            CodeSource codeSource = AppPaths.class.getProtectionDomain().getCodeSource();
            URL location = codeSource == null ? null : codeSource.getLocation();
            if (location == null || !"file".equals(location.getProtocol())) {
                return null;
            }
            return new File(location.toURI());
        } catch (Exception e) {
            return null;
        }
    }

    private static void ensureInitialized() {
        if (!initialized) {
            init();
        }
    }
}
