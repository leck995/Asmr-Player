package cn.tealc.asmr.source.local;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地媒体来源操作测试。
 */
@DisplayName("本地媒体来源测试")
class LocalMediaSourceTest {

    @TempDir
    Path tempDirectory;

    @Test
    @DisplayName("移动目录后保留目录内容")
    void move_movesDirectoryToTargetDirectory() throws Exception {
        Path sourceDirectory = Files.createDirectory(tempDirectory.resolve("source-work"));
        Files.writeString(sourceDirectory.resolve("track.mp3"), "audio");
        Path destinationDirectory = Files.createDirectory(tempDirectory.resolve("destination"));

        boolean moved = LocalMediaSource.INSTANCE.move(sourceDirectory.toString(), destinationDirectory.toString());

        Path targetDirectory = destinationDirectory.resolve("source-work");
        assertTrue(moved);
        assertFalse(Files.exists(sourceDirectory));
        assertTrue(Files.exists(targetDirectory.resolve("track.mp3")));
    }

    @Test
    @DisplayName("目标目录非空时仍可移入")
    void move_movesDirectoryIntoNonEmptyTargetDirectory() throws Exception {
        Path sourceDirectory = Files.createDirectory(tempDirectory.resolve("source-work"));
        Files.writeString(sourceDirectory.resolve("track.mp3"), "audio");
        Path destinationDirectory = Files.createDirectory(tempDirectory.resolve("destination"));
        Files.writeString(destinationDirectory.resolve("existing.txt"), "existing");

        boolean moved = LocalMediaSource.INSTANCE.move(sourceDirectory.toString(), destinationDirectory.toString());

        assertTrue(moved);
        assertTrue(Files.exists(destinationDirectory.resolve("existing.txt")));
        assertTrue(Files.exists(destinationDirectory.resolve("source-work/track.mp3")));
    }

    @Test
    @DisplayName("目标存在同名目录时不覆盖")
    void move_doesNotOverwriteExistingDirectory() throws Exception {
        Path sourceDirectory = Files.createDirectory(tempDirectory.resolve("source-work"));
        Path destinationDirectory = Files.createDirectory(tempDirectory.resolve("destination"));
        Path existingDirectory = Files.createDirectory(destinationDirectory.resolve("source-work"));
        Files.writeString(existingDirectory.resolve("existing.txt"), "existing");

        boolean moved = LocalMediaSource.INSTANCE.move(sourceDirectory.toString(), destinationDirectory.toString());

        assertFalse(moved);
        assertTrue(Files.exists(sourceDirectory));
        assertTrue(Files.exists(existingDirectory.resolve("existing.txt")));
    }

    @Test
    @DisplayName("目录原子移动失败时复制后删除源目录")
    void move_fallsBackToCopyAndDeleteWhenAtomicMoveFails() throws Exception {
        Path sourceDirectory = Files.createDirectory(tempDirectory.resolve("source-work"));
        Files.writeString(sourceDirectory.resolve("track.mp3"), "audio");
        Path destinationDirectory = Files.createDirectory(tempDirectory.resolve("destination"));

        LocalMediaSource source = new LocalMediaSource() {
            @Override
            protected void movePath(Path source, Path destination) throws java.io.IOException {
                throw new java.nio.file.DirectoryNotEmptyException(destination.toString());
            }
        };

        boolean moved = source.move(sourceDirectory.toString(), destinationDirectory.toString());

        assertTrue(moved);
        assertFalse(Files.exists(sourceDirectory));
        assertTrue(Files.exists(destinationDirectory.resolve("source-work/track.mp3")));
    }

    @Test
    @DisplayName("永久删除普通文件")
    void delete_removesRegularFile() throws Exception {
        Path file = Files.writeString(tempDirectory.resolve("track.mp3"), "audio");

        boolean deleted = LocalMediaSource.INSTANCE.delete(file.toString());

        assertTrue(deleted);
        assertFalse(Files.exists(file));
    }

    @Test
    @DisplayName("永久删除非空目录")
    void delete_removesNonEmptyDirectory() throws Exception {
        Path directory = Files.createDirectory(tempDirectory.resolve("folder"));
        Files.writeString(directory.resolve("track.mp3"), "audio");

        boolean deleted = LocalMediaSource.INSTANCE.delete(directory.toString());

        assertTrue(deleted);
        assertFalse(Files.exists(directory));
    }
}
