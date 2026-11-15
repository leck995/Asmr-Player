package cn.tealc.asmr.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalPlayerServiceTest {

    @Test
    void detectsKnownPlayersByExecutableName() {
        assertEquals(ExternalPlayerService.PlayerKind.MPV,
                ExternalPlayerService.detectPlayerKind("C:/Tools/mpv.exe"));
        assertEquals(ExternalPlayerService.PlayerKind.VLC,
                ExternalPlayerService.detectPlayerKind("C:/Program Files/VideoLAN/VLC/vlc.exe"));
        assertEquals(ExternalPlayerService.PlayerKind.POTPLAYER,
                ExternalPlayerService.detectPlayerKind("C:/PotPlayer/PotPlayerMini64.exe"));
    }

    @Test
    void detectionIsCaseInsensitive() {
        assertEquals(ExternalPlayerService.PlayerKind.MPV,
                ExternalPlayerService.detectPlayerKind("C:/Tools/MPV.EXE"));
        assertEquals(ExternalPlayerService.PlayerKind.VLC,
                ExternalPlayerService.detectPlayerKind("C:/VLC/VLC.exe"));
    }

    @Test
    void unknownOrEmptyPathFallsBackToGeneric() {
        assertEquals(ExternalPlayerService.PlayerKind.GENERIC,
                ExternalPlayerService.detectPlayerKind("C:/Tools/some-player.exe"));
        assertEquals(ExternalPlayerService.PlayerKind.GENERIC,
                ExternalPlayerService.detectPlayerKind(""));
        assertEquals(ExternalPlayerService.PlayerKind.GENERIC,
                ExternalPlayerService.detectPlayerKind(null));
    }

    @Test
    void detectionReadsFileNameOnlyNotDirectories() {
        // 目录名里带 mpv 不应影响判断
        assertEquals(ExternalPlayerService.PlayerKind.GENERIC,
                ExternalPlayerService.detectPlayerKind("C:/mpv/vlc-tools/other.exe"));
    }

    @Test
    void blankTemplatePassesUrlAsPositionalArgument() {
        List<String> command = ExternalPlayerService.resolveCommand(
                "C:/Tools/mpv.exe", "https://example.test/a.mp4", "");
        assertEquals(List.of("C:/Tools/mpv.exe", "https://example.test/a.mp4"), command);
    }

    @Test
    void nullTemplateBehavesLikeBlank() {
        List<String> command = ExternalPlayerService.resolveCommand(
                "C:/Tools/mpv.exe", "https://example.test/a.mp4", null);
        assertEquals(List.of("C:/Tools/mpv.exe", "https://example.test/a.mp4"), command);
    }

    @Test
    void templateWithoutPlaceholderAppendsUrl() {
        List<String> command = ExternalPlayerService.resolveCommand(
                "C:/Tools/mpv.exe", "https://example.test/a.mp4", "--fs --no-osc");
        assertEquals(List.of("C:/Tools/mpv.exe", "--fs", "--no-osc",
                "https://example.test/a.mp4"), command);
    }

    @Test
    void placeholderIsReplacedInPlace() {
        List<String> command = ExternalPlayerService.resolveCommand(
                "C:/Tools/mpv.exe", "https://example.test/a.mp4", "--start=10 {url} --fs");
        assertEquals(List.of("C:/Tools/mpv.exe", "--start=10",
                "https://example.test/a.mp4", "--fs"), command);
    }

    @Test
    void quotedArgumentsKeepTheirSpaces() {
        List<String> command = ExternalPlayerService.resolveCommand(
                "C:/Tools/mpv.exe", "https://example.test/a.mp4", "--title \"my video\"");
        assertEquals(List.of("C:/Tools/mpv.exe", "--title", "my video",
                "https://example.test/a.mp4"), command);
    }

    @Test
    void executablePathIsAlwaysFirstArgument() {
        List<String> command = ExternalPlayerService.resolveCommand(
                "C:/Program Files/Some Player/player.exe", "https://example.test/a.mp4", "--fs");
        assertEquals("C:/Program Files/Some Player/player.exe", command.get(0));
    }

    @Test
    void missingConfigurationFailsWithoutThrowing() {
        String previousPath = cn.tealc.asmr.config.Config.setting.externalPlayerPath.get();
        try {
            cn.tealc.asmr.config.Config.setting.externalPlayerPath.set("");
            ExternalPlayerService.LaunchResult result = new ExternalPlayerService().launch("https://example.test/a.mp4");
            assertTrue(!result.success());
            assertTrue(result.message().contains("尚未指定"));
        } finally {
            cn.tealc.asmr.config.Config.setting.externalPlayerPath.set(previousPath);
        }
    }

    @Test
    void blankUrlFailsWithoutThrowing() {
        ExternalPlayerService.LaunchResult result = new ExternalPlayerService().launch("  ");
        assertTrue(!result.success());
    }
}
