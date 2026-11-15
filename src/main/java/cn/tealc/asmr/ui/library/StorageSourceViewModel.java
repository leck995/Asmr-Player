package cn.tealc.asmr.ui.library;

import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.OpenListAccess;
import cn.tealc.asmr.model.StorageSource;
import cn.tealc.asmr.mvvm.ViewModel;
import cn.tealc.asmr.service.StorageSourceService;
import cn.tealc.asmr.source.openlist.OpenListClient;
import cn.tealc.asmr.source.openlist.OpenListMediaSource;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** 存储源管理界面的状态与操作。 */
public class StorageSourceViewModel implements ViewModel {
    private final StorageSourceService storageSourceService = DI.get(StorageSourceService.class);
    private final ObservableList<StorageSource> sources = FXCollections.observableArrayList();

    public StorageSourceViewModel() {
        refresh();
    }

    public ObservableList<StorageSource> getSources() { return sources; }
    public void refresh() { sources.setAll(storageSourceService.getAll()); }
    public StorageSource createLocal(String name, String path) {
        StorageSource source = storageSourceService.createLocal(name, path);
        sources.add(source);
        return source;
    }
    public StorageSource createOpenList(String name, String baseUrl, String token, String targetPath) {
        StorageSource source = storageSourceService.createOpenList(name, baseUrl, token, targetPath);
        sources.add(source);
        return source;
    }
    public StorageSource updateLocal(StorageSource source, String name, String path) {
        StorageSource updated = storageSourceService.updateLocal(source, name, path);
        refresh();
        return updated;
    }
    public StorageSource updateOpenList(StorageSource source, String name, String baseUrl, String token, String targetPath) {
        StorageSource updated = storageSourceService.updateOpenList(source, name, baseUrl, token, targetPath);
        refresh();
        return updated;
    }
    public String getOpenListBaseUrl(StorageSource source) {
        return storageSourceService.getOpenListBaseUrl(source);
    }
    public void remove(StorageSource source) {
        storageSourceService.remove(source.getId());
        refresh();
    }
    public OpenListMediaSource testOpenList(String baseUrl, String token, StorageSource existing) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException("请输入 OpenList 地址");
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) normalized = "http://" + normalized;
        if ((token == null || token.isBlank()) && existing != null) {
            token = storageSourceService.getOpenListAccess(existing).token();
        }
        if (token == null || token.isBlank()) throw new IllegalArgumentException("请输入 OpenList 令牌");
        OpenListAccess access = new OpenListAccess(normalized.replaceAll("/+$", ""), token.trim());
        new OpenListClient(access).listFiles("/");
        return new OpenListMediaSource(access);
    }
}
