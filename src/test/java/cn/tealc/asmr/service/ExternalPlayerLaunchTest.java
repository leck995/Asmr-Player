package cn.tealc.asmr.service;

import cn.tealc.asmr.config.Config;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端验证：用一个记录参数的临时脚本充当播放器，确认确实被拉起且收到正确地址。
 */
class ExternalPlayerLaunchTest {

    @Test
    void launchesConfiguredExecutableWithResolvedUrl() throws Exception {
        Path output = Files.createTempFile("asmr-player-args-", ".txt");
        Files.deleteIfExists(output);
        Path script = Files.createTempFile("asmr-fake-player-", ".cmd");
        // Windows 批处理：把收到的参数写入文件，模拟播放器接收地址
        Files.writeString(script, "@echo off\r\necho %* > \"" + output + "\"\r\n");

        String previousPath = Config.setting.externalPlayerPath.get();
        String previousArgs = Config.setting.externalPlayerArgs.get();
        try {
            Config.setting.externalPlayerPath.set(script.toString());
            Config.setting.externalPlayerArgs.set("");

            ExternalPlayerService.LaunchResult result =
                    new ExternalPlayerService().launch("https://example.test/movie.mp4");
            assertTrue(result.success(), "应成功拉起外部程序: " + result.message());

            // 进程是异步的，等待脚本写出参数文件
            String content = null;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                if (Files.exists(output)) {
                    content = Files.readString(output).trim();
                    if (!content.isEmpty()) {
                        break;
                    }
                }
                Thread.sleep(50);
            }
            assertNotNull(content, "播放器应收到启动参数");
            assertTrue(content.contains("https://example.test/movie.mp4"),
                    "命令行应包含播放地址，实际为: " + content);
            // 路径未被引号包裹时应原样作为第一个参数
            assertTrue(content.startsWith("https://example.test/movie.mp4"),
                    "地址应作为位置参数传入，实际为: " + content);
        } finally {
            Config.setting.externalPlayerPath.set(previousPath);
            Config.setting.externalPlayerArgs.set(previousArgs);
            Files.deleteIfExists(script);
            Files.deleteIfExists(output);
        }
    }

    @Test
    void customTemplateIsPassedThrough() throws Exception {
        Path output = Files.createTempFile("asmr-player-args2-", ".txt");
        Files.deleteIfExists(output);
        Path script = Files.createTempFile("asmr-fake-player2-", ".cmd");
        Files.writeString(script, "@echo off\r\necho %* > \"" + output + "\"\r\n");

        String previousPath = Config.setting.externalPlayerPath.get();
        String previousArgs = Config.setting.externalPlayerArgs.get();
        try {
            Config.setting.externalPlayerPath.set(script.toString());
            Config.setting.externalPlayerArgs.set("--fullscreen {url}");

            ExternalPlayerService.LaunchResult result =
                    new ExternalPlayerService().launch("https://example.test/clip.mkv");
            assertTrue(result.success(), result.message());

            String content = null;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                if (Files.exists(output)) {
                    content = Files.readString(output).trim();
                    if (!content.isEmpty()) {
                        break;
                    }
                }
                Thread.sleep(50);
            }
            assertNotNull(content);
            assertEquals("--fullscreen https://example.test/clip.mkv", content);
        } finally {
            Config.setting.externalPlayerPath.set(previousPath);
            Config.setting.externalPlayerArgs.set(previousArgs);
            Files.deleteIfExists(script);
            Files.deleteIfExists(output);
        }
    }

    @Test
    void nonexistentExecutableReportsFailure() {
        String previousPath = Config.setting.externalPlayerPath.get();
        try {
            Config.setting.externalPlayerPath.set("C:/definitely/not/here/player.exe");
            ExternalPlayerService.LaunchResult result =
                    new ExternalPlayerService().launch("https://example.test/a.mp4");
            assertTrue(!result.success());
            assertTrue(result.message().contains("路径无效"));
        } finally {
            Config.setting.externalPlayerPath.set(previousPath);
        }
    }

    @Test
    void commandLineIsBuiltForAllSupportedKinds() {
        for (String exe : List.of("C:/a/mpv.exe", "C:/a/vlc.exe", "C:/a/PotPlayerMini64.exe", "C:/a/x.exe")) {
            List<String> command = ExternalPlayerService.resolveCommand(exe, "https://t/a.mp4", "");
            assertEquals(2, command.size());
            assertEquals(exe, command.get(0));
            assertEquals("https://t/a.mp4", command.get(1));
        }
    }
}
