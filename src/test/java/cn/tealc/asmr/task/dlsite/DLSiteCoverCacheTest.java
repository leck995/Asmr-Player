package cn.tealc.asmr.task.dlsite;

import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DLSiteCoverCacheTest {
    @TempDir Path directory;
    private String originalUrl;

    @BeforeEach
    void configureSyncUrl() {
        originalUrl = Config.setting.sourcesUrl.get();
        Config.setting.sourcesUrl.set("https://example.com");
    }

    @AfterEach
    void restoreSyncUrl() {
        Config.setting.sourcesUrl.set(originalUrl);
    }

    @ParameterizedTest
    @ValueSource(strings = {".jpg", ".jpeg", ".png"})
    void recognizesExistingCoverInsideCacheDirectory(String extension) throws Exception {
        var worker = new DLSiteThread(null, new AsmrWork());
        var check = DLSiteThread.class.getDeclaredMethod("checkImageExist", java.io.File.class, String.class);
        check.setAccessible(true);
        assertFalse((boolean) check.invoke(worker, directory.toFile(), "RJ123456"));
        Files.createFile(directory.resolve("RJ123456" + extension));
        assertTrue((boolean) check.invoke(worker, directory.toFile(), "RJ123456"));
        assertFalse((boolean) check.invoke(worker, directory.toFile(), "RJ654321"));
    }
}
