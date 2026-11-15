package cn.tealc.asmr.utils;

import cn.tealc.asmr.model.SupportedVideoFormat;
import cn.tealc.asmr.model.AsmrFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoFileResolverTest {

    @Test
    void recognizesVideoExtensionsCaseInsensitively() {
        assertTrue(VideoFileResolver.isVideoUrl("https://example.test/a.mp4"));
        assertTrue(VideoFileResolver.isVideoUrl("https://example.test/a.MKV"));
        assertTrue(VideoFileResolver.isVideoUrl("https://example.test/a.WebM"));
        assertTrue(VideoFileResolver.isVideoUrl("https://example.test/a.m3u8"));
        assertTrue(VideoFileResolver.isVideoUrl("https://example.test/a.ts"));
    }

    @Test
    void ignoresQueryStringWhenDetecting() {
        assertTrue(VideoFileResolver.isVideoUrl("https://example.test/a.mp4?sign=abc&t=1"));
        assertTrue(VideoFileResolver.isVideoUrl("https://example.test/a.mkv?token=x.y.z"));
    }

    @Test
    void rejectsNonVideoAndMalformedUrls() {
        assertFalse(VideoFileResolver.isVideoUrl("https://example.test/a.mp3"));
        assertFalse(VideoFileResolver.isVideoUrl("https://example.test/a.lrc"));
        assertFalse(VideoFileResolver.isVideoUrl("https://example.test/no-extension"));
        assertFalse(VideoFileResolver.isVideoUrl("https://example.test/dir.mp4/"));
        assertFalse(VideoFileResolver.isVideoUrl(""));
        assertFalse(VideoFileResolver.isVideoUrl(null));
    }

    @Test
    void extensionLookalikeInDirectoryNameIsIgnored() {
        // 目录名带 .mp4 不应被误判为视频
        assertFalse(VideoFileResolver.isVideoUrl("https://example.test/a.mp4/file.bin"));
    }

    @Test
    void supportedFormatsCoverCommonStreamTargets() {
        assertTrue(SupportedVideoFormat.contains("mp4"));
        assertTrue(SupportedVideoFormat.contains("MP4"));
        assertTrue(SupportedVideoFormat.contains("Mkv"));
        assertTrue(SupportedVideoFormat.contains("m3u8"));
        assertTrue(SupportedVideoFormat.contains("webm"));
        assertFalse(SupportedVideoFormat.contains("mp3"));
        assertFalse(SupportedVideoFormat.contains(""));
        assertFalse(SupportedVideoFormat.contains(null));
    }

    @Test
    void directoryIsNeverTreatedAsVideo() {
        AsmrFile dir = new AsmrFile("movies", "/movies", "dir", true, "movies", 0, null);
        assertFalse(VideoFileResolver.isVideoFile(dir, null));
    }

    @Test
    void plainVideoFileIsRecognizedWithoutResolving() {
        AsmrFile file = new AsmrFile("a.mp4", "/a.mp4", "mp4", false, "a.mp4", 100, null);
        // 普通视频按扩展名判定，无需访问远程来源
        assertTrue(VideoFileResolver.isVideoFile(file, null));
    }

    @Test
    void audioFileIsNotVideo() {
        AsmrFile file = new AsmrFile("a.mp3", "/a.mp3", "mp3", false, "a.mp3", 100, null);
        assertFalse(VideoFileResolver.isVideoFile(file, null));
    }

    @Test
    void nullFileIsNotVideo() {
        assertFalse(VideoFileResolver.isVideoFile(null, null));
    }
}
