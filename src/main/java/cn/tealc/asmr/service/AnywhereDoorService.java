package cn.tealc.asmr.service;

import cn.tealc.asmr.model.AsmrKeyType;
import cn.tealc.asmr.dao.AnywhereDoorDao;
import cn.tealc.asmr.dao.MediaLibraryDao;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AnywhereDoor;
import cn.tealc.asmr.model.MediaLibrary;
import cn.tealc.asmr.model.StorageSource;
import cn.tealc.asmr.source.MediaSource;
import cn.tealc.asmr.source.MediaSourceFactory;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 数据库为快捷入口的唯一数据源，只在事务提交成功后刷新共享只读列表。 */
public class AnywhereDoorService {
    private static final Set<AsmrKeyType> TYPES = Set.of(AsmrKeyType.FOLDER, AsmrKeyType.ONLINE,
            AsmrKeyType.CATEGORY, AsmrKeyType.VA, AsmrKeyType.CIRCLE, AsmrKeyType.TAG);
    private final AnywhereDoorDao dao;
    private final ObservableList<AnywhereDoor> entries = FXCollections.observableArrayList();
    private final ObservableList<AnywhereDoor> readOnlyEntries = FXCollections.unmodifiableObservableList(entries);

    public AnywhereDoorService() {
        dao = DI.get(AnywhereDoorDao.class);
        refresh();
    }

    public ObservableList<AnywhereDoor> getEntries() { return readOnlyEntries; }

    public void refresh() { entries.setAll(dao.getAll()); }

    public boolean contains(AsmrKeyType type, String key) {
        String normalized = normalizedKey(type, normalizeTarget(type, key, false));
        return entries.stream().anyMatch(item -> item.getType() == type
                && normalizedKey(type, item.getKey()).equals(normalized));
    }

    public void createFolder(String directory, String alias) { add(AsmrKeyType.FOLDER, directory, alias); }

    public List<MediaLibrary> getLibraries() { return DI.get(MediaLibraryDao.class).getAll(); }

    public MediaSource getDirectorySource(MediaLibrary library) {
        MediaLibrary current = requireLibrary(library);
        StorageSource source = requireSource(current);
        if (!DI.get(StorageSourceService.class).containsPath(source, current.getPath())) {
            throw new IllegalArgumentException("媒体库目录超出存储源范围或不可访问");
        }
        return MediaSourceFactory.forStorageSource(source);
    }

    public void createFolder(MediaLibrary library, String directory, String alias) {
        AnywhereDoor entry = folderEntry(library, directory, alias);
        requireUnique(0, entry.getType(), entry.getKey(), entry.getMediaLibraryId());
        entries.setAll(dao.add(entry, persistenceKey(entry)));
    }

    public void updateFolder(AnywhereDoor existing, MediaLibrary library, String directory, String alias) {
        AnywhereDoor current = requireEntry(existing.getId());
        if (current.getType() != AsmrKeyType.FOLDER && current.getType() != AsmrKeyType.ONLINE) {
            throw new IllegalArgumentException("该入口不是目录类型");
        }
        AnywhereDoor updated = folderEntry(library, directory, alias);
        updated.setId(current.getId());
        requireUnique(current.getId(), updated.getType(), updated.getKey(), updated.getMediaLibraryId());
        entries.setAll(dao.update(updated, persistenceKey(updated)));
    }

    private AnywhereDoor folderEntry(MediaLibrary library, String directory, String alias) {
        MediaLibrary current = requireLibrary(library);
        StorageSource source = requireSource(current);
        AsmrKeyType type = current.isLocal() ? AsmrKeyType.FOLDER : AsmrKeyType.ONLINE;
        String target = normalizeTarget(type, directory, true);
        StorageSourceService sources = DI.get(StorageSourceService.class);
        if (!sources.containsPath(source, target)) throw new IllegalArgumentException("目录超出存储源范围或不可访问");
        if (current.isLocal()) {
            try {
                target = Path.of(target).toRealPath().toString();
                if (!Path.of(target).startsWith(Path.of(current.getPath()).toRealPath())) {
                    throw new IllegalArgumentException("请选择该媒体库根目录或其子目录");
                }
            } catch (java.io.IOException error) {
                throw new IllegalArgumentException("媒体库目录不可访问", error);
            }
        } else {
            target = sources.normalizeRemotePath(target);
            String base = sources.normalizeRemotePath(current.getPath());
            if (!base.equals("/") && !target.equals(base) && !target.startsWith(base + "/")) {
                throw new IllegalArgumentException("请选择该媒体库根目录或其子目录");
            }
        }
        AnywhereDoor entry = new AnywhereDoor(target, type, normalizeAlias(alias));
        entry.setMediaLibraryId(current.getId());
        return entry;
    }

    private MediaLibrary requireLibrary(MediaLibrary library) {
        if (library == null || library.getId() == null) throw new IllegalArgumentException("请先选择媒体库");
        MediaLibrary current = DI.get(MediaLibraryDao.class).getById(library.getId());
        if (current == null) throw new IllegalArgumentException("媒体库已被移除，请重新选择");
        return current;
    }

    private StorageSource requireSource(MediaLibrary library) {
        StorageSource source = library.getStorageSourceId() == null ? null
                : DI.get(StorageSourceService.class).getById(library.getStorageSourceId());
        if (source == null || source.getType() != library.getStorageType()) {
            throw new IllegalArgumentException("媒体库关联的存储源不存在或类型不匹配");
        }
        if (source.getStatus() != cn.tealc.asmr.model.StorageSourceStatus.ACTIVE) {
            throw new IllegalArgumentException("媒体库关联的存储源当前不可用");
        }
        return source;
    }

    private String persistenceKey(AnywhereDoor entry) {
        // 唯一键包含媒体库范围，路径本身单独保存供浏览和查询。
        String target = normalizedKey(entry.getType(), entry.getKey());
        return entry.getMediaLibraryId() == null ? target : "library:" + entry.getMediaLibraryId() + ":" + target;
    }

    public void add(AsmrKeyType type, String key, String alias) {
        String target = normalizeTarget(type, key, true);
        requireUnique(0, type, target);
        AnywhereDoor entry = new AnywhereDoor(target, type, normalizeAlias(alias));
        entries.setAll(dao.add(entry, normalizedKey(type, target)));
    }

    public void update(AnywhereDoor existing, String key, String alias) {
        AnywhereDoor current = requireEntry(existing.getId());
        if (current.getMediaLibraryId() != null) {
            MediaLibrary library = DI.get(MediaLibraryDao.class).getById(current.getMediaLibraryId());
            updateFolder(current, library, key, alias);
            return;
        }
        String target = normalizeTarget(current.getType(), key, false);
        boolean changed = !normalizedKey(current.getType(), current.getKey())
                .equals(normalizedKey(current.getType(), target));
        if (changed) target = normalizeTarget(current.getType(), target, true);
        requireUnique(current.getId(), current.getType(), target);
        AnywhereDoor updated = new AnywhereDoor(target, current.getType(), normalizeAlias(alias));
        updated.setId(current.getId());
        entries.setAll(dao.update(updated, normalizedKey(current.getType(), target)));
    }

    public void remove(AnywhereDoor entry) {
        requireEntry(entry.getId());
        entries.setAll(dao.delete(entry.getId()));
    }

    public void remove(AsmrKeyType type, String key) {
        String normalized = normalizedKey(type, normalizeTarget(type, key, false));
        entries.stream().filter(item -> item.getType() == type
                        && normalizedKey(type, item.getKey()).equals(normalized)).findFirst().ifPresent(this::remove);
    }

    public void move(AnywhereDoor entry, int direction) {
        requireEntry(entry.getId());
        entries.setAll(dao.move(entry.getId(), direction));
    }

    private AnywhereDoor requireEntry(int id) {
        return entries.stream().filter(entry -> entry.getId() == id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("该快捷入口已被移除"));
    }

    private void requireUnique(int id, AsmrKeyType type, String key) {
        requireUnique(id, type, key, null);
    }

    private void requireUnique(int id, AsmrKeyType type, String key, Integer libraryId) {
        String normalized = normalizedKey(type, key);
        if (entries.stream().anyMatch(entry -> entry.getId() != id && entry.getType() == type
                && Objects.equals(entry.getMediaLibraryId(), libraryId)
                && normalizedKey(type, entry.getKey()).equals(normalized))) {
            throw new IllegalArgumentException("该目标已存在快捷入口");
        }
    }

    private String normalizeTarget(AsmrKeyType type, String key, boolean mustExist) {
        if (type == null || !TYPES.contains(type)) throw new IllegalArgumentException("不支持的快捷入口类型");
        if (key == null || key.isBlank()) throw new IllegalArgumentException("请输入入口目标");
        if (type != AsmrKeyType.FOLDER) return key.trim();
        try {
            Path path = Path.of(key.trim()).toAbsolutePath().normalize();
            if (mustExist && !Files.isDirectory(path)) throw new IllegalArgumentException("文件夹不存在或不可访问");
            return path.toString();
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("文件夹路径无效", e);
        }
    }

    private String normalizedKey(AsmrKeyType type, String key) {
        return type == AsmrKeyType.FOLDER && File.separatorChar == '\\' ? key.toLowerCase(Locale.ROOT) : key;
    }

    private String normalizeAlias(String alias) { return alias == null || alias.isBlank() ? null : alias.trim(); }
}
