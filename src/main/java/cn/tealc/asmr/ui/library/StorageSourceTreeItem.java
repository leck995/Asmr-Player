package cn.tealc.asmr.ui.library;

import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.source.MediaSource;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;

import java.util.List;
import java.util.function.Consumer;

/** 存储源目录的异步懒加载节点。 */
public class StorageSourceTreeItem extends TreeItem<AsmrFile> {
    private final MediaSource mediaSource;
    private final Consumer<RuntimeException> onError;
    private boolean childrenLoaded;

    public StorageSourceTreeItem(AsmrFile file, MediaSource mediaSource) {
        this(file, mediaSource, null);
    }

    public StorageSourceTreeItem(AsmrFile file, MediaSource mediaSource, Consumer<RuntimeException> onError) {
        super(file);
        this.mediaSource = mediaSource;
        this.onError = onError;
    }

    @Override
    public ObservableList<TreeItem<AsmrFile>> getChildren() {
        if (!childrenLoaded && getValue().isDir()) {
            childrenLoaded = true;
            Thread.startVirtualThread(() -> {
                try {
                    List<TreeItem<AsmrFile>> children = mediaSource.listFiles(getValue().getFilepath()).stream()
                            .filter(AsmrFile::isDir)
                            .map(item -> (TreeItem<AsmrFile>) new StorageSourceTreeItem(item, mediaSource, onError))
                            .toList();
                    Platform.runLater(() -> super.getChildren().setAll(children));
                } catch (RuntimeException error) {
                    if (onError == null) throw error;
                    Platform.runLater(() -> onError.accept(error));
                }
            });
        }
        return super.getChildren();
    }

    @Override
    public boolean isLeaf() {
        return !getValue().isDir();
    }
}
