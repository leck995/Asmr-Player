package cn.tealc.asmr.service;

import cn.tealc.asmr.BaseDatabaseTest;
import cn.tealc.asmr.model.AsmrKeyType;
import cn.tealc.asmr.dao.AnywhereDoorDao;
import cn.tealc.asmr.dao.DataAccessException;
import cn.tealc.asmr.dao.JdbcUtils;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AnywhereDoor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class AnywhereDoorServiceTest extends BaseDatabaseTest {
    @TempDir Path directory;
    private AnywhereDoorService service;

    @BeforeEach
    void createService() { service = DI.get(AnywhereDoorService.class); }

    @Test
    void persistsAllTypesAndAliasesAcrossInstances() {
        service.createFolder(directory.toString(), " 本地收藏 ");
        for (AsmrKeyType type : List.of(AsmrKeyType.ONLINE, AsmrKeyType.CATEGORY,
                AsmrKeyType.VA, AsmrKeyType.CIRCLE, AsmrKeyType.TAG)) {
            service.add(type, "目标", "别名");
        }
        DI.reset();
        var loaded = DI.get(AnywhereDoorService.class).getEntries();
        assertEquals(6, loaded.size());
        assertEquals("本地收藏", loaded.getFirst().getDisplayName());
        assertEquals(directory.toAbsolutePath().normalize().toString(), loaded.getFirst().getKey());
        for (int index = 0; index < loaded.size(); index++) {
            assertTrue(loaded.get(index).getId() > 0);
            assertEquals(index, loaded.get(index).getSortOrder());
        }
    }

    @Test
    void updatesTargetAndAliasWithoutChangingOrderOrIdentity() {
        service.add(AsmrKeyType.TAG, "标签一", "别名");
        service.add(AsmrKeyType.TAG, "标签二", null);
        AnywhereDoor before = service.getEntries().getFirst();
        service.update(before, " 标签三 ", "  ");
        AnywhereDoor after = new AnywhereDoorService().getEntries().getFirst();
        assertEquals(before.getId(), after.getId());
        assertEquals(before.getSortOrder(), after.getSortOrder());
        assertEquals("标签三", after.getKey());
        assertNull(after.getName());
        assertEquals("标签三", after.getDisplayName());
    }

    @Test
    void normalizesFolderPathsAndRejectsDuplicates() {
        service.createFolder(directory.toString(), null);
        String equivalent = directory.resolve(".").toString().replace('\\', '/');
        assertTrue(service.contains(AsmrKeyType.FOLDER, equivalent));
        assertThrows(IllegalArgumentException.class, () -> service.createFolder(equivalent, "重复"));
        if (java.io.File.separatorChar == '\\') {
            assertTrue(service.contains(AsmrKeyType.FOLDER, equivalent.toUpperCase(Locale.ROOT)));
            assertThrows(IllegalArgumentException.class, () ->
                    service.createFolder(equivalent.toUpperCase(Locale.ROOT), null));
        }
        assertEquals(1, service.getEntries().size());
    }

    @Test
    void rejectsDuplicateTargetsButAllowsDifferentTypes() {
        service.add(AsmrKeyType.TAG, "目标", null);
        service.add(AsmrKeyType.CATEGORY, "目标", null);
        service.add(AsmrKeyType.TAG, "其他", null);
        AnywhereDoor other = service.getEntries().getLast();
        assertThrows(IllegalArgumentException.class, () -> service.update(other, "目标", "别名"));
        assertEquals("其他", service.getEntries().getLast().getKey());
        assertEquals(3, new AnywhereDoorService().getEntries().size());
    }

    @Test
    void validatesTargetsAndTypes() {
        assertThrows(IllegalArgumentException.class, () -> service.createFolder(directory.resolve("missing").toString(), null));
        assertThrows(IllegalArgumentException.class, () -> service.add(AsmrKeyType.TAG, " ", null));
        assertThrows(IllegalArgumentException.class, () -> service.add(null, "目标", null));
        assertEquals(0, service.getEntries().size());
    }

    @Test
    void canRenameOfflineFolderButCannotChangeToMissingFolder() throws Exception {
        Path offline = directory.resolve("offline");
        // 直接建库记录模拟离线目录，不依赖测试机上额外的磁盘或网络盘。
        DI.get(AnywhereDoorDao.class).add(new AnywhereDoor(offline.toString(), AsmrKeyType.FOLDER),
                java.io.File.separatorChar == '\\' ? offline.toString().toLowerCase(Locale.ROOT) : offline.toString());
        service.refresh();
        AnywhereDoor entry = service.getEntries().getFirst();
        service.update(entry, offline.toString(), "离线收藏");
        assertEquals("离线收藏", service.getEntries().getFirst().getDisplayName());
        assertThrows(IllegalArgumentException.class, () -> service.update(entry, directory.resolve("other-missing").toString(), null));
    }

    @Test
    void reordersBothDirectionsAndPersistsOrderWithStableBoundaries() {
        service.add(AsmrKeyType.TAG, "一", null);
        service.add(AsmrKeyType.TAG, "二", null);
        service.add(AsmrKeyType.TAG, "三", null);
        AnywhereDoor third = service.getEntries().getLast();
        service.move(third, -1);
        assertEquals(List.of("一", "三", "二"), keys(service));
        service.move(third, -1);
        service.move(third, -1);
        assertEquals(List.of("三", "一", "二"), keys(new AnywhereDoorService()));
        service.move(third, 1);
        service.move(third, 1);
        service.move(third, 1);
        assertEquals(List.of("一", "二", "三"), keys(new AnywhereDoorService()));
        assertThrows(IllegalArgumentException.class, () -> service.move(third, 0));
    }

    @Test
    void deletingOnlyRemovesShortcutAndAllowsRecreation() throws Exception {
        Path file = Files.createFile(directory.resolve("audio.mp3"));
        service.createFolder(directory.toString(), null);
        AnywhereDoor entry = service.getEntries().getFirst();
        service.remove(entry);
        assertTrue(Files.isDirectory(directory));
        assertTrue(Files.exists(file));
        assertTrue(new AnywhereDoorService().getEntries().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.update(entry, directory.toString(), null));
        assertThrows(IllegalArgumentException.class, () -> service.remove(entry));
        service.createFolder(directory.toString(), null);
        service.remove(AsmrKeyType.FOLDER, directory.resolve(".").toString());
        assertTrue(service.getEntries().isEmpty());
    }

    @Test
    void sharedListIsReadOnlyAndPublishedOnlyAfterSuccessfulWrites() throws Exception {
        var shared = service.getEntries();
        assertSame(shared, DI.get(AnywhereDoorService.class).getEntries());
        assertThrows(UnsupportedOperationException.class, () -> shared.add(new AnywhereDoor("invalid")));
        service.add(AsmrKeyType.TAG, "一", null);
        assertEquals(List.of("一"), shared.stream().map(AnywhereDoor::getKey).toList());
        execute("CREATE TRIGGER fail_shortcut_insert BEFORE INSERT ON asmr_anywhere_door BEGIN SELECT RAISE(ABORT, 'test failure'); END");
        try {
            assertThrows(DataAccessException.class, () -> service.add(AsmrKeyType.TAG, "二", null));
            assertEquals(List.of("一"), keys(service));
            assertEquals(List.of("一"), keys(new AnywhereDoorService()));
        } finally { execute("DROP TRIGGER fail_shortcut_insert"); }
    }

    @Test
    void reorderingFailureRollsBackPartialUpdatesAndDoesNotPublish() throws Exception {
        service.add(AsmrKeyType.TAG, "一", null);
        service.add(AsmrKeyType.TAG, "二", null);
        service.add(AsmrKeyType.TAG, "三", null);
        int secondId = service.getEntries().get(1).getId();
        execute("CREATE TRIGGER fail_shortcut_sort BEFORE UPDATE OF sort_order ON asmr_anywhere_door "
                + "WHEN NEW.id = " + secondId + " BEGIN SELECT RAISE(ABORT, 'test failure'); END");
        try {
            assertThrows(DataAccessException.class, () -> service.move(service.getEntries().getLast(), -1));
            assertEquals(List.of("一", "二", "三"), keys(service));
            var loaded = new AnywhereDoorService();
            assertEquals(List.of("一", "二", "三"), keys(loaded));
            assertEquals(List.of(0, 1, 2), loaded.getEntries().stream().map(AnywhereDoor::getSortOrder).toList());
        } finally { execute("DROP TRIGGER fail_shortcut_sort"); }
    }

    @Test
    void failedEditsAndDeletesLeavePublishedEntriesUnchanged() throws Exception {
        service.add(AsmrKeyType.TAG, "一", "别名");
        AnywhereDoor before = service.getEntries().getFirst();
        for (String operation : List.of("UPDATE", "DELETE")) {
            execute("CREATE TRIGGER fail_shortcut_write BEFORE " + operation
                    + " ON asmr_anywhere_door BEGIN SELECT RAISE(ABORT, 'test failure'); END");
            try {
                assertThrows(DataAccessException.class, () -> {
                    if (operation.equals("UPDATE")) service.update(before, "二", "新别名");
                    else service.remove(before);
                });
                assertSame(before, service.getEntries().getFirst());
                assertEquals("一", new AnywhereDoorService().getEntries().getFirst().getKey());
                assertEquals("别名", before.getName());
            } finally { execute("DROP TRIGGER fail_shortcut_write"); }
        }
    }

    @Test
    void databaseUniquenessProtectsAgainstStaleServiceInstances() {
        AnywhereDoorService stale = new AnywhereDoorService();
        service.add(AsmrKeyType.TAG, "目标", null);
        assertThrows(DataAccessException.class, () -> stale.add(AsmrKeyType.TAG, "目标", null));
        assertTrue(stale.getEntries().isEmpty());
        stale.refresh();
        assertEquals(List.of("目标"), keys(stale));
    }

    private List<String> keys(AnywhereDoorService source) {
        return source.getEntries().stream().map(AnywhereDoor::getKey).toList();
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = JdbcUtils.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
