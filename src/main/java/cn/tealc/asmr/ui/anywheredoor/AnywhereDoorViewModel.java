package cn.tealc.asmr.ui.anywheredoor;

import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AnywhereDoor;
import cn.tealc.asmr.model.MediaLibrary;
import cn.tealc.asmr.source.MediaSource;
import cn.tealc.asmr.mvvm.ViewModel;
import cn.tealc.asmr.service.AnywhereDoorService;
import javafx.collections.ObservableList;
import java.util.List;

/** 快捷入口管理状态，所有修改由数据库服务提交。 */
public class AnywhereDoorViewModel implements ViewModel {
    private final AnywhereDoorService service = DI.get(AnywhereDoorService.class);

    public ObservableList<AnywhereDoor> getPathList() { return service.getEntries(); }
    public List<MediaLibrary> getLibraries() { return service.getLibraries(); }
    public MediaSource getDirectorySource(MediaLibrary library) { return service.getDirectorySource(library); }
    public void createFolder(MediaLibrary library, String directory, String alias) { service.createFolder(library, directory, alias); }
    public void updateFolder(AnywhereDoor entry, MediaLibrary library, String directory, String alias) { service.updateFolder(entry, library, directory, alias); }
    public void update(AnywhereDoor entry, String key, String alias) { service.update(entry, key, alias); }
    public void remove(AnywhereDoor entry) { service.remove(entry); }
    public void move(AnywhereDoor entry, int direction) { service.move(entry, direction); }
}
