package cn.tealc.asmr.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoverCachePathTest {
    @ParameterizedTest
    @ValueSource(strings = {"main", "thumb"})
    void cachesCoversDirectlyUnderCoverDirectory(String subDir) {
        assertEquals(AppPaths.coverDir(subDir).toPath(), CoverUtils.getCacheDirectory(subDir).toPath());
    }
}
