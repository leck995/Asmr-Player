package cn.tealc.asmr.service;

import cn.tealc.asmr.BaseDatabaseTest;
import cn.tealc.asmr.model.AsmrKeyType;
import cn.tealc.asmr.dao.AsmrWorkDao;
import cn.tealc.asmr.dao.DataAccessException;
import cn.tealc.asmr.dao.JdbcUtils;
import cn.tealc.asmr.dao.MediaLibraryDao;
import cn.tealc.asmr.dao.WorkDao;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AnywhereDoor;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.MediaLibrary;
import cn.tealc.asmr.model.StorageSource;
import cn.tealc.asmr.source.StorageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LibraryShortcutTest extends BaseDatabaseTest {
    @TempDir Path directory;
    private AnywhereDoorService shortcuts;
    private StorageSourceService sources;

    @BeforeEach
    void setup() {
        DI.bind(WorkDao.class, AsmrWorkDao::new);
        shortcuts = DI.get(AnywhereDoorService.class);
        sources = DI.get(StorageSourceService.class);
    }

    private MediaLibrary library(StorageSource source, String path) {
        MediaLibrary library = new MediaLibrary("媒体库", path, 2, source.getType(), source.getId());
        assertTrue(DI.get(MediaLibraryDao.class).add(library));
        return library;
    }

    @Test
    void localSelectionIsBoundToLibraryAndRejectsSiblingDirectories() throws Exception {
        Path root = Files.createDirectory(directory.resolve("library"));
        Path child = Files.createDirectory(root.resolve("child"));
        MediaLibrary library = library(sources.createLocal("本机", directory.toString()), root.toString());
        assertThrows(IllegalArgumentException.class, () -> shortcuts.createFolder(null, child.toString(), null));
        assertThrows(IllegalArgumentException.class, () -> shortcuts.createFolder(library, directory.toString(), null));
        shortcuts.createFolder(library, child.toString(), "子目录");
        AnywhereDoor entry = new AnywhereDoorService().getEntries().getFirst();
        assertEquals(library.getId(), entry.getMediaLibraryId());
        assertEquals(AsmrKeyType.FOLDER, entry.getType());
        assertEquals(child.toRealPath().toString(), entry.getKey());
        assertThrows(IllegalArgumentException.class, () -> shortcuts.createFolder(library, child.toString(), null));
        shortcuts.update(entry, child.toString(), "新的别名");
        assertEquals(library.getId(), shortcuts.getEntries().getFirst().getMediaLibraryId());
        assertEquals("新的别名", shortcuts.getEntries().getFirst().getName());
        assertNotNull(shortcuts.getDirectorySource(library));
    }

    @Test
    void remotePathsAreScopedByLibraryAndCannotEscapeRoot() {
        StorageSource firstSource = sources.createOpenList("远程一", "http://localhost:18881", "openlist-test", "/audio");
        StorageSource secondSource = sources.createOpenList("远程二", "http://localhost:18882", "openlist-test", "/audio");
        MediaLibrary first = library(firstSource, "/audio/library");
        MediaLibrary second = library(secondSource, "/audio/library");
        shortcuts.createFolder(first, "/audio/library/child", "甲");
        shortcuts.createFolder(second, "/audio/library/child", "乙");
        var loaded = new AnywhereDoorService().getEntries();
        assertEquals(List.of(first.getId(), second.getId()), loaded.stream().map(AnywhereDoor::getMediaLibraryId).toList());
        assertTrue(loaded.stream().allMatch(entry -> entry.getType() == AsmrKeyType.ONLINE));
        assertThrows(IllegalArgumentException.class, () -> shortcuts.createFolder(first, "/audio/library-other", null));
        assertThrows(IllegalArgumentException.class, () -> shortcuts.createFolder(first, "/audio/library/../outside", null));
        assertThrows(IllegalArgumentException.class, () -> shortcuts.updateFolder(loaded.getFirst(), second, "/audio/library/child", null));
        shortcuts.remove(loaded.getFirst());
        assertEquals(second.getId(), new AnywhereDoorService().getEntries().getFirst().getMediaLibraryId());
    }

    @Test
    void movingShortcutBetweenLibrariesKeepsIdentityAndOrder() throws Exception {
        Path firstRoot = Files.createDirectory(directory.resolve("one"));
        Path secondRoot = Files.createDirectory(directory.resolve("two"));
        StorageSource source = sources.createLocal("本机", directory.toString());
        MediaLibrary first = library(source, firstRoot.toString());
        MediaLibrary second = library(source, secondRoot.toString());
        shortcuts.createFolder(first, firstRoot.toString(), "收藏");
        shortcuts.add(AsmrKeyType.TAG, "标签", null);
        AnywhereDoor before = shortcuts.getEntries().getFirst();
        shortcuts.updateFolder(before, second, secondRoot.toString(), "新收藏");
        AnywhereDoor after = new AnywhereDoorService().getEntries().getFirst();
        assertEquals(before.getId(), after.getId());
        assertEquals(before.getSortOrder(), after.getSortOrder());
        assertEquals(second.getId(), after.getMediaLibraryId());
        assertEquals(secondRoot.toRealPath().toString(), after.getKey());
    }

    @Test
    void remoteQueryFiltersLibraryAndIncludesNestedWorksWithoutPrefixCollisions() {
        StorageSource firstSource = sources.createOpenList("远程一", "http://localhost:18881", "openlist-test", "/");
        StorageSource secondSource = sources.createOpenList("远程二", "http://localhost:18882", "openlist-test", "/");
        MediaLibrary first = library(firstSource, "/audio");
        MediaLibrary second = library(secondSource, "/audio");
        WorkService works = DI.get(WorkService.class);
        addWork(works, first, "/audio/group/RJ001", "甲");
        addWork(works, first, "/audio/group/nested/RJ002", "乙");
        addWork(works, first, "/audio/group-other/RJ003", "不应显示");
        addWork(works, second, "/audio/group/RJ001", "其他服务器");
        var page = works.queryPage(AsmrKeyType.ONLINE, "/audio/group", 1, 1,
                false, true, false, false, true, true, first.getId());
        assertEquals(List.of("甲", "乙"), page.stream().map(AsmrWork::getTitle).toList());
        assertEquals(2, works.queryCount(AsmrKeyType.ONLINE, "/audio/group", false, true, true, true, first.getId()));
        assertEquals(0, works.queryCount(AsmrKeyType.ONLINE, "/audio/group", false, true, true, false, first.getId()));
        assertEquals(1, works.queryCount(AsmrKeyType.ONLINE, "/audio/group", false, true, true, true, second.getId()));
    }

    @Test
    void failedScopeUpdateRollsBackShortcutAndLibraryAssociation() throws Exception {
        Path firstRoot = Files.createDirectory(directory.resolve("one"));
        Path secondRoot = Files.createDirectory(directory.resolve("two"));
        StorageSource source = sources.createLocal("本机", directory.toString());
        MediaLibrary first = library(source, firstRoot.toString());
        MediaLibrary second = library(source, secondRoot.toString());
        shortcuts.createFolder(first, firstRoot.toString(), "收藏");
        AnywhereDoor before = shortcuts.getEntries().getFirst();
        try (var connection = JdbcUtils.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER fail_scope BEFORE INSERT ON asmr_anywhere_door_library BEGIN SELECT RAISE(ABORT, 'test failure'); END");
        }
        try {
            assertThrows(DataAccessException.class, () -> shortcuts.updateFolder(before, second, secondRoot.toString(), "新收藏"));
            assertSame(before, shortcuts.getEntries().getFirst());
            AnywhereDoor loaded = new AnywhereDoorService().getEntries().getFirst();
            assertEquals(first.getId(), loaded.getMediaLibraryId());
            assertEquals(before.getKey(), loaded.getKey());
            assertEquals("收藏", loaded.getName());
        } finally {
            try (var connection = JdbcUtils.getConnection(); var statement = connection.createStatement()) {
                statement.execute("DROP TRIGGER fail_scope");
            }
        }
    }

    @Test
    void deletingLibraryAlsoRemovesItsShortcutsButKeepsOthers() throws Exception {
        Path firstRoot = Files.createDirectory(directory.resolve("one"));
        Path secondRoot = Files.createDirectory(directory.resolve("two"));
        StorageSource source = sources.createLocal("本机", directory.toString());
        MediaLibrary first = library(source, firstRoot.toString());
        MediaLibrary second = library(source, secondRoot.toString());
        shortcuts.createFolder(first, firstRoot.toString(), null);
        shortcuts.createFolder(second, secondRoot.toString(), null);
        shortcuts.add(AsmrKeyType.TAG, "标签", null);
        assertTrue(DI.get(MediaLibraryService.class).deleteLibraryKeepWorks(first));
        assertEquals(2, shortcuts.getEntries().size());
        assertEquals(second.getId(), shortcuts.getEntries().getFirst().getMediaLibraryId());
        assertTrue(DI.get(MediaLibraryService.class).deleteLibraryWithWorks(second));
        assertEquals(1, shortcuts.getEntries().size());
        assertTrue(Files.isDirectory(firstRoot));
        assertTrue(Files.isDirectory(secondRoot));
    }

    private void addWork(WorkService works, MediaLibrary library, String path, String title) {
        AsmrWork work = new AsmrWork();
        work.setTitle(title);
        work.setFilepath(path);
        work.setParentPath(library.getPath());
        work.setStorageSourceId(library.getStorageSourceId());
        work.setMediaLibraryId(library.getId());
        work.setStorageType(StorageType.OPENLIST.getCode());
        assertTrue(works.addWork(work));
    }
}
