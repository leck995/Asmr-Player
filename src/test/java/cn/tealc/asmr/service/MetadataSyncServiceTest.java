package cn.tealc.asmr.service;

import cn.tealc.asmr.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataSyncServiceTest {
    private String originalUrl;

    @BeforeEach
    void rememberUrl() {
        originalUrl = Config.setting.sourcesUrl.get();
    }

    @AfterEach
    void restoreUrl() {
        Config.setting.sourcesUrl.set(originalUrl);
    }

    @Test
    void isSyncUrlConfigured_requiresHttpsUrl() {
        Config.setting.sourcesUrl.set(null);
        assertFalse(MetadataSyncService.isSyncUrlConfigured());

        Config.setting.sourcesUrl.set("");
        assertFalse(MetadataSyncService.isSyncUrlConfigured());

        Config.setting.sourcesUrl.set("http://example.com");
        assertFalse(MetadataSyncService.isSyncUrlConfigured());

        Config.setting.sourcesUrl.set("https://example.com");
        assertTrue(MetadataSyncService.isSyncUrlConfigured());
    }
}
