package cn.tealc.asmr.service;

import cn.tealc.asmr.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 外部播放器调用服务。
 * <p>
 * 负责把「视频地址 + 用户指定的播放器可执行文件」拼成命令行并启动外部进程。
 * 不持有进程句柄、不接管其输出：播放器是用户自己的程序，本应用只负责拉起，
 * 不关心其生命周期，也不在退出时结束它。
 * <p>
 * 参数模板按可执行文件名自动识别（mpv / VLC / PotPlayer），识别不出则退化为
 * 「可执行文件 + URL」，并把实际命令行写入日志便于排查。
 *
 * @author Leck
 */
public class ExternalPlayerService {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalPlayerService.class);

    /** 参数模板占位符，替换为待播放地址 */
    public static final String URL_PLACEHOLDER = "{url}";

    /**
     * 播放器类型。决定参数模板的默认形式。
     */
    public enum PlayerKind {
        MPV("mpv"),
        VLC("VLC"),
        POTPLAYER("PotPlayer"),
        GENERIC("其他播放器");

        private final String displayName;

        PlayerKind(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 启动结果，供调用方展示提示信息。
     *
     * @param success 是否成功拉起
     * @param message 结果描述（失败原因或播放器类型）
     */
    public record LaunchResult(boolean success, String message) {
        static LaunchResult ok(String message) {
            return new LaunchResult(true, message);
        }

        static LaunchResult fail(String message) {
            return new LaunchResult(false, message);
        }
    }

    /**
     * 按可执行文件名识别播放器类型，大小写不敏感。
     *
     * @param executablePath 播放器可执行文件路径
     * @return 识别出的类型，无法识别时返回 {@link PlayerKind#GENERIC}
     */
    public static PlayerKind detectPlayerKind(String executablePath) {
        if (executablePath == null || executablePath.isBlank()) {
            return PlayerKind.GENERIC;
        }
        String name = new File(executablePath).getName().toLowerCase(Locale.ROOT);
        if (name.startsWith("mpv")) {
            return PlayerKind.MPV;
        }
        if (name.startsWith("vlc")) {
            return PlayerKind.VLC;
        }
        if (name.startsWith("potplayer")) {
            return PlayerKind.POTPLAYER;
        }
        return PlayerKind.GENERIC;
    }

    /**
     * 构造完整命令行。
     * <p>
     * 用户填写的参数模板优先：模板中出现 {@value #URL_PLACEHOLDER} 时原地替换，
     * 否则把地址追加到末尾。模板留空时按播放器类型使用内置形式——
     * 这几种播放器都直接接受地址作为位置参数，无需额外开关。
     *
     * @param executablePath 播放器可执行文件路径
     * @param url            待播放地址
     * @param argumentTemplate 用户自定义参数模板，可为空
     * @return 命令行，第一项为可执行文件路径
     */
    public static List<String> resolveCommand(String executablePath, String url, String argumentTemplate) {
        List<String> command = new ArrayList<>();
        command.add(executablePath);
        if (argumentTemplate != null && !argumentTemplate.isBlank()) {
            if (argumentTemplate.contains(URL_PLACEHOLDER)) {
                for (String token : splitArguments(argumentTemplate.replace(URL_PLACEHOLDER, url))) {
                    command.add(token);
                }
            } else {
                for (String token : splitArguments(argumentTemplate)) {
                    command.add(token);
                }
                command.add(url);
            }
            return command;
        }
        command.add(url);
        return command;
    }

    /**
     * 按空白切分参数，保留双引号内的空格。
     * <p>
     * 例如 {@code --title "my video"} 切为 {@code --title} 与 {@code my video}。
     *
     * @param template 参数模板
     * @return 切分后的参数列表
     */
    static List<String> splitArguments(String template) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < template.length(); index++) {
            char ch = template.charAt(index);
            if (ch == '"') {
                quoted = !quoted;
                continue;
            }
            if (!quoted && Character.isWhitespace(ch)) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(ch);
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    /**
     * 用当前设置中的播放器播放指定地址。
     * <p>
     * 配置缺失或路径失效时返回失败结果，不抛异常。
     *
     * @param url 待播放地址
     * @return 启动结果
     */
    public LaunchResult launch(String url) {
        if (url == null || url.isBlank()) {
            return LaunchResult.fail("播放地址为空");
        }
        String executable = Config.setting.externalPlayerPath.get();
        if (executable == null || executable.isBlank()) {
            return LaunchResult.fail("尚未指定外部播放器，请先在设置中配置");
        }
        File file = new File(executable);
        if (!file.exists() || !file.isFile()) {
            return LaunchResult.fail("播放器路径无效：" + executable);
        }
        List<String> command = resolveCommand(executable, url, Config.setting.externalPlayerArgs.get());
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            // 不接管子进程的输出：播放器独立运行，其输出与本应用无关。
            // stdin 不能设为 DISCARD（该值仅用于输出流），关闭即可，播放器无需从本进程读取输入。
            builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            builder.start();
            LOG.info("已调用外部播放器: {}", command);
            return LaunchResult.ok("已使用 " + detectPlayerKind(executable).getDisplayName() + " 打开");
        } catch (IOException e) {
            LOG.error("启动外部播放器失败: {}", command, e);
            return LaunchResult.fail("启动播放器失败：" + e.getMessage());
        }
    }
}
