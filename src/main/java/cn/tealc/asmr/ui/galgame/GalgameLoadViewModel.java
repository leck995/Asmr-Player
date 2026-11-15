package cn.tealc.asmr.ui.galgame;

import cn.tealc.asmr.eventbus.EventBus;
import cn.tealc.asmr.Main;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.di.Inject;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.GalgameSaveData;
import cn.tealc.asmr.model.AudioTrack;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.model.message.MessageType;
import cn.tealc.asmr.player.VlcPlayer;
import cn.tealc.asmr.service.SaveDataService;
import cn.tealc.asmr.service.WorkService;
import cn.tealc.asmr.service.PlaylistService;
import cn.tealc.asmr.mvvm.ViewModel;
import cn.tealc.asmr.ui.dialog.Alerts;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Duration;

import java.io.File;

/**
 * Galgame 存档列表的视图模型：分页提供存档，并把读取、删除动作落到存档服务与播放器上。
 * <p>
 * 内容宽度与每页条数在构造时按主窗口宽度一次性确定（&gt;1550 取 1500/8 条，&lt;1200 取 900/4 条，
 * 其余取 1250/6 条），之后不随窗口缩放变化。
 * {@code pageIndex} 是 0 基页码，构造时定位到最后一页；查询服务时换算为 1 基。
 *
 * @author Leck
 */
public class GalgameLoadViewModel implements ViewModel {
    @Inject
    private WorkService workService;
    private final SaveDataService saveDataService = DI.get(SaveDataService.class);
    private final PlaylistService playlistService = DI.get(PlaylistService.class);
    private ObservableList<GalgameSaveData> saves;
    private SimpleIntegerProperty pageCount;
    private SimpleIntegerProperty pageSize;
    private SimpleIntegerProperty pageIndex;

    private SimpleStringProperty selectedSaveText;
    private SimpleStringProperty selectedSaveTitle;

    private SimpleDoubleProperty contentMaxWidth;

    private SimpleIntegerProperty saveSize;

    /**
     * 按主窗口宽度确定内容最大宽度与每页条数，加载初始页存档，并让页码变化时自动重新查询。
     */
    public GalgameLoadViewModel() {
        saves = FXCollections.observableArrayList();
        saveSize = new SimpleIntegerProperty();
        pageCount = new SimpleIntegerProperty();
        pageIndex = new SimpleIntegerProperty();
        pageSize = new SimpleIntegerProperty(6);
        selectedSaveTitle = new SimpleStringProperty();
        selectedSaveText = new SimpleStringProperty();
        contentMaxWidth = new SimpleDoubleProperty();

        if (Main.window.getWidth() > 1550) {
            contentMaxWidth.set(1500);
            pageSize.set(8);
        } else if (Main.window.getWidth() < 1200) {
            contentMaxWidth.set(900);
            pageSize.set(4);
        } else {
            contentMaxWidth.set(1250);
        }

        saveSize.set(saveDataService.getSaveCount());
        pageCount.set(Math.max(1, (getSaveSize() + getPageSize() - 1) / getPageSize()));
        // 默认定位到最后一页，即最新存档所在页；页码 0 基，查询服务时加 1
        pageIndex.set(getPageCount() - 1);
        saves.addAll(saveDataService.getSaves(getPageIndex() + 1, getPageSize()));

        pageIndex.addListener((observableValue, number, t1) -> {
            saves.setAll(saveDataService.getSaves(t1.intValue() + 1, getPageSize()));
        });

    }


    /**
     * 更新悬停预览：传入存档时同步标题（章节）与字幕文本，传 null 表示鼠标移出、清空预览。
     */
    public void updateSelectedSave(GalgameSaveData saveData) {
        selectedSaveTitle.set(saveData == null ? null : saveData.getChapter());
        selectedSaveText.set(saveData == null ? null : saveData.getText());
    }

    /**
     * 读取指定存档：优先按记录的作品 ID 定位，ID 缺失时回退按标题查找，
     * 再依据存档所在目录重建播放列表，并定位到路径与存档记录一致的曲目，从其记录的时间点开始播放。
     * <p>
     * 作品、曲目列表或目标曲目任一找不到时，用 {@link Alerts} 弹出警告并停留在存档弹窗；
     * 调用方根据返回值决定是否关闭弹窗。
     *
     * @param saveData 要读取的存档
     * @return 是否成功开始播放
     */
    public boolean jumpTo(GalgameSaveData saveData) {
        if (saveData == null) {
            return false;
        }
        AsmrWork work = saveData.getAsmrId() != 0
                ? workService.getById(saveData.getAsmrId())
                : workService.getWorkByTitle(saveData.getTitle());
        if (work == null) {
            warnMissing();
            return false;
        }
        ObservableList<AudioTrack> musics = playlistService.buildPlaylist(work, saveData.getFilepathDir());
        if (musics == null || musics.isEmpty()) {
            warnMissing();
            return false;
        }
        int index = -1;
        for (int i = 0; i < musics.size(); i++) {
            if (musics.get(i).getUrl().equals(saveData.getFilepath())) {
                index = i;
                break;
            }
        }
        if (index == -1) {
            warnMissing();
            return false;
        }
        VlcPlayer.getInstance().init(musics);
        VlcPlayer.getInstance().play(index, Duration.millis(saveData.getTime()));
        return true;
    }

    private void warnMissing() {
        Alerts.warning().title("提醒")
                .message("该音声不存在，可能已经被删除了")
                .cancel("确定").show();
    }


    /**
     * 删除指定存档：数据库记录删除成功后才删除缩略图文件并刷新列表，
     * 删除失败时列表与文件都保持原状；缩略图文件不存在时跳过。
     * <p>
     * 删除后重新计算页数与总数；当前页被删空或超出范围时回退到新的最后一页。
     *
     * @param saveData 要删除的存档
     */
    public void delete(GalgameSaveData saveData) {
        if (saveData == null) {
            return;
        }
        if (!saveDataService.deleteSaveById(saveData.getId())) {
            EventBus.publish(NotificationKey.MESSAGE,
                    new MessageInfo(MessageType.WARNING, "删除存档失败，请稍后重试"));
            return;
        }
        File thumbFile = new File(saveData.getThumb());
        if (thumbFile.exists()) {
            thumbFile.delete();
        }
        saves.remove(saveData);

        saveSize.set(saveDataService.getSaveCount());
        int pages = Math.max(1, (getSaveSize() + getPageSize() - 1) / getPageSize());
        pageCount.set(pages);
        if (getPageIndex() >= pages) {
            pageIndex.set(pages - 1);
        } else {
            saves.setAll(saveDataService.getSaves(getPageIndex() + 1, getPageSize()));
        }
    }


    public ObservableList<GalgameSaveData> getSaves() {
        return saves;
    }

    public void setSaves(ObservableList<GalgameSaveData> saves) {
        this.saves = saves;
    }

    public int getPageCount() {
        return pageCount.get();
    }

    public SimpleIntegerProperty pageCountProperty() {
        return pageCount;
    }

    public void setPageCount(int pageCount) {
        this.pageCount.set(pageCount);
    }

    public int getPageSize() {
        return pageSize.get();
    }

    public SimpleIntegerProperty pageSizeProperty() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize.set(pageSize);
    }

    public int getPageIndex() {
        return pageIndex.get();
    }

    public SimpleIntegerProperty pageIndexProperty() {
        return pageIndex;
    }

    public void setPageIndex(int pageIndex) {
        this.pageIndex.set(pageIndex);
    }

    public String getSelectedSaveText() {
        return selectedSaveText.get();
    }

    public SimpleStringProperty selectedSaveTextProperty() {
        return selectedSaveText;
    }

    public void setSelectedSaveText(String selectedSaveText) {
        this.selectedSaveText.set(selectedSaveText);
    }

    public String getSelectedSaveTitle() {
        return selectedSaveTitle.get();
    }

    public SimpleStringProperty selectedSaveTitleProperty() {
        return selectedSaveTitle;
    }

    public void setSelectedSaveTitle(String selectedSaveTitle) {
        this.selectedSaveTitle.set(selectedSaveTitle);
    }

    public double getContentMaxWidth() {
        return contentMaxWidth.get();
    }

    public SimpleDoubleProperty contentMaxWidthProperty() {
        return contentMaxWidth;
    }

    public void setContentMaxWidth(double contentMaxWidth) {
        this.contentMaxWidth.set(contentMaxWidth);
    }

    public int getSaveSize() {
        return saveSize.get();
    }

    public SimpleIntegerProperty saveSizeProperty() {
        return saveSize;
    }

    public void setSaveSize(int saveSize) {
        this.saveSize.set(saveSize);
    }
}