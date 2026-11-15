package cn.tealc.asmr.ui.tools;

import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.di.Inject;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.DuplicatedWork;
import cn.tealc.asmr.model.MediaLibrary;

import cn.tealc.asmr.service.MediaLibraryService;
import cn.tealc.asmr.service.WorkService;
import cn.tealc.asmr.mvvm.ViewModel;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;

/**
 * {@link ToolView} 的视图模型，提供媒体库列表与重复作品分组数据。
 * <p>
 * 重复检测按作品的原始编号（忽略大小写）分组，仅保留包含两个及以上版本的组。
 *
 * @author Leck
 */
public class ToolViewModel implements ViewModel {
    @Inject
    private WorkService workService;
    private final MediaLibraryService mediaLibraryService = DI.get(MediaLibraryService.class);
    private ObservableList<MediaLibrary> mediaLibrariesList;
    private ObservableList<DuplicatedWork> duplicatedWorks;
    private SimpleStringProperty duplicatedTip;
    private static final String TIP_TEMPLATE="检测到%d组重复作品";

    public ToolViewModel() {
        mediaLibrariesList= FXCollections.observableArrayList(
                mediaLibraryService.getAll());
        duplicatedWorks=FXCollections.observableArrayList();
        duplicatedTip=new SimpleStringProperty();
    }

    /**
     * 重新扫描全部作品，刷新重复分组列表与提示文本。
     */
    public void checkDuplicate(){
        duplicatedTip.set("检测中，请耐心等待");

        duplicatedWorks.setAll(groupDuplicates(workService.getAllWorks()));
        duplicatedTip.set(String.format(TIP_TEMPLATE,duplicatedWorks.size()));

    }

    /**
     * 按原始作品编号（忽略大小写）分组，返回含两个及以上版本的重复作品组。
     *
     * @param works 待检测的全部作品
     * @return 重复组列表，每组以首个作品为基准；无重复时返回空列表
     */
    static List<DuplicatedWork> groupDuplicates(List<AsmrWork> works) {
        Map<String, List<AsmrWork>> groups = new LinkedHashMap<>();
        for (AsmrWork work : works) {
            String origin = work.getOriginWorkId();
            if (origin != null && !origin.isBlank()) {
                groups.computeIfAbsent(origin.toUpperCase(Locale.ROOT), ignored -> new ArrayList<>()).add(work);
            }
        }
        return groups.values().stream().filter(group -> group.size() > 1)
                .map(group -> new DuplicatedWork(group.getFirst(), List.copyOf(group))).toList();
    }

    /**
     * 从重复列表中剔除已删除的作品编号，重新分组并刷新提示文本。
     *
     * @param ids 已被移除的作品主键集合
     */
    public void removeDuplicateWorks(Set<Integer> ids) {
        List<AsmrWork> remaining = duplicatedWorks.stream().flatMap(group -> group.getDuplicatedList().stream())
                .filter(work -> !ids.contains(work.getId())).toList();
        duplicatedWorks.setAll(groupDuplicates(remaining));
        duplicatedTip.set(String.format(TIP_TEMPLATE, duplicatedWorks.size()));
    }


    public ObservableList<MediaLibrary> getMediaLibrariesList() {
        return mediaLibrariesList;
    }

    public void setMediaLibrariesList(ObservableList<MediaLibrary> mediaLibrariesList) {
        this.mediaLibrariesList = mediaLibrariesList;
    }

    public ObservableList<DuplicatedWork> getDuplicatedWorks() {
        return duplicatedWorks;
    }

    public void setDuplicatedWorks(ObservableList<DuplicatedWork> duplicatedWorks) {
        this.duplicatedWorks = duplicatedWorks;
    }

    public String getDuplicatedTip() {
        return duplicatedTip.get();
    }

    public SimpleStringProperty duplicatedTipProperty() {
        return duplicatedTip;
    }

    public void setDuplicatedTip(String duplicatedTip) {
        this.duplicatedTip.set(duplicatedTip);
    }
}
