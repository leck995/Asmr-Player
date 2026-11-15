package cn.tealc.asmr.filter;

import cn.tealc.asmr.model.SupportedAudioFormat;
import cn.tealc.asmr.source.local.LocalMediaSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AudioFilterTest {
    @TempDir(cleanup = CleanupMode.NEVER)
    Path directory;

    @Test
    void audioSuffixDirectoriesAreNotAudioFiles() throws Exception {
        Files.createDirectory(directory.resolve("02.mp3"));
        Files.createDirectory(directory.resolve("03.FLAC"));
        Files.writeString(directory.resolve("01.MP3"), "audio");
        Files.writeString(directory.resolve("cover.jpg"), "image");

        var files = LocalMediaSource.INSTANCE.listAudioFiles(directory.toString());

        assertEquals(List.of("01.MP3"), files.stream().map(file -> file.getFilename()).toList());
        assertTrue(files.stream().noneMatch(file -> file.isDir()));
        assertFalse(new AudioFilter().accept(directory.toFile(), "missing.mp3"));
    }

    @Test
    void supportedAudioAndStrmFilesRemainAccepted() throws Exception {
        AudioFilter filter = new AudioFilter();
        for (SupportedAudioFormat format : SupportedAudioFormat.values()) {
            String name = "track." + format.name();
            Files.writeString(directory.resolve(name), "audio");
            assertTrue(filter.accept(directory.toFile(), name), name);
        }
    }
}
