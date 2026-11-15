package cn.tealc.asmr.service;

import cn.tealc.asmr.BaseDatabaseTest;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.AudioTrack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlaylistServiceTest extends BaseDatabaseTest {
    @TempDir(cleanup = CleanupMode.NEVER)
    Path directory;
    private AsmrWork work;
    private final PlaylistService service = new PlaylistService();

    @BeforeEach
    void setup() {
        var source = DI.get(StorageSourceService.class).createLocal("测试存储源", directory.toString());
        work = new AsmrWork();
        work.setWorkId("RJ01676393");
        work.setFilepath(directory.toString());
        work.setStorageSourceId(source.getId());
        work.setStorageType(0);
    }

    @Test
    void quickPlayUsesActualTracksInsideDirectoryNamedMp3() throws Exception {
        Path tracks = Files.createDirectory(directory.resolve("02.mp3"));
        Path first = Files.writeString(tracks.resolve("01_曲目.mp3"), "audio");
        Path second = Files.writeString(tracks.resolve("02_曲目.mp3"), "audio");
        Files.writeString(tracks.resolve("01_曲目.vtt"), "subtitle");
        Files.writeString(directory.resolve("cover.jpg"), "image");

        var playlist = service.buildPlaylist(work);

        assertNotNull(playlist);
        assertEquals(List.of(first.toString(), second.toString()), playlist.stream().map(AudioTrack::getUrl).toList());
        assertTrue(playlist.stream().allMatch(music -> Files.isRegularFile(Path.of(music.getUrl()))));
        assertTrue(playlist.stream().allMatch(music -> music.getWork() == work && !music.isOnline()));
    }

    @Test
    void directTracksTakePriorityOverAlternativeSubdirectories() throws Exception {
        Path direct = Files.writeString(directory.resolve("01.mp3"), "audio");
        Path alternate = Files.createDirectory(directory.resolve("wav"));
        Files.writeString(alternate.resolve("01.wav"), "audio");

        assertEquals(List.of(direct.toString()), service.buildPlaylist(work).stream().map(AudioTrack::getUrl).toList());
    }

    @Test
    void recursiveFallbackIncludesAllSupportedFormatsAndKeepsStrmMarker() throws Exception {
        Path nested = Files.createDirectories(directory.resolve("音频/子目录"));
        Files.writeString(nested.resolve("01.OGG"), "audio");
        Files.writeString(nested.resolve("02.WAV"), "audio");
        Files.writeString(nested.resolve("03.strm"), "https://example.test/track.mp3");
        Files.writeString(nested.resolve("cover.png"), "image");

        var playlist = service.buildPlaylist(work);

        assertEquals(List.of("01.OGG", "02.WAV", "03.strm"), playlist.stream().map(AudioTrack::getName).toList());
        assertTrue(playlist.getLast().isStrm());
    }

    @Test
    void explicitSubdirectoryDoesNotIncludeSiblingAudio() throws Exception {
        Path target = Files.createDirectory(directory.resolve("target"));
        Path nested = Files.createDirectory(target.resolve("nested"));
        Path track = Files.writeString(nested.resolve("01.mp3"), "audio");
        Files.writeString(directory.resolve("sibling.mp3"), "audio");

        assertEquals(List.of(track.toString()), service.buildPlaylist(work, target.toString()).stream()
                .map(AudioTrack::getUrl).toList());
    }

    @Test
    void imageOnlyDirectoriesHaveNoPlaylist() throws Exception {
        Path images = Files.createDirectory(directory.resolve("插图.mp3"));
        Files.writeString(images.resolve("cover.jpg"), "image");

        assertNull(service.buildPlaylist(work));
    }

    @Test
    void fileListPlaybackNeverTreatsDirectoriesAsTracks() {
        AsmrFile folder = new AsmrFile("02.mp3", "/audio/02.mp3", "mp3", true, "02.mp3", 0, null);
        assertNull(service.buildPlaylistFromFiles(work, List.of(folder)));
    }
}
