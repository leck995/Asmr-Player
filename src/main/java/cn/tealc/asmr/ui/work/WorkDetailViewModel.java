package cn.tealc.asmr.ui.work;

import cn.tealc.asmr.eventbus.EventBus;

import cn.tealc.asmr.model.AsmrKeyType;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.AudioTrack;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.model.message.MessageType;
import cn.tealc.asmr.player.VlcPlayer;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.service.PlaylistService;
import cn.tealc.asmr.service.FileOperationService;
import cn.tealc.asmr.service.WorkService;
import cn.tealc.asmr.ui.navigation.NavigationService;
import cn.tealc.asmr.ui.navigation.WorkSource;
import cn.tealc.asmr.ui.navigation.WorksRoute;
import cn.tealc.asmr.utils.CoverUtils;
import cn.tealc.asmr.utils.FXResourcesLoader;
import cn.tealc.asmr.ui.dialog.Alerts;
import cn.tealc.asmr.mvvm.ViewModel;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.util.Arrays;
import java.util.List;

/**
 * 作品详情页的视图模型，加载单个作品的展示数据并提供收藏、评分、跳转与播放等操作。
 * <p>
 * 构造时按主键重新读取作品，并订阅 {@code MAIN_GRID_WORK_UPDATE}：编号匹配时就地刷新详情页，
 * 使编辑标签或资料后详情与网格卡片保持一致。
 *
 * @author Leck
 */
public class WorkDetailViewModel implements ViewModel {
    private static final Image DEFAULT_COVER = new Image(FXResourcesLoader.image("cover-main.jpg"), 400, 300, true, true);
    private final WorkService workService = DI.get(WorkService.class);
    private final PlaylistService playlistService = DI.get(PlaylistService.class);
    private final FileOperationService fileOperationService = DI.get(FileOperationService.class);
    private AsmrWork work;
    private SimpleStringProperty title;
    private SimpleBooleanProperty star;
    private SimpleStringProperty circle;
    private SimpleBooleanProperty circleVisible;
    private ObservableList<String> categories;
    private ObservableList<String> vas;
    private ObservableList<String> tags;
    private SimpleObjectProperty<Image> cover;
    /** 作品编号，为空时界面隐藏编号行，与 {@code BigWorkCell} 一致 */
    private final SimpleStringProperty workId = new SimpleStringProperty();
    /** 发行日期，为空时隐藏日期行 */
    private final SimpleStringProperty release = new SimpleStringProperty();
    /** 评分快照，null 或 0 表示未评分 */
    private final SimpleObjectProperty<Double> rate = new SimpleObjectProperty<>();
    /** 是否有字幕 */
    private final SimpleBooleanProperty subtitle = new SimpleBooleanProperty(false);
    /** 是否全年龄（即非 NSFW） */
    private final SimpleBooleanProperty allAges = new SimpleBooleanProperty(false);


    private SimpleBooleanProperty online;



    /**
     * 按主键加载作品并订阅变更事件；{@code selected} 为 {@code null} 时进入空白详情、不加载数据。
     *
     * @param selected 选中的作品，仅用其主键重新查询
     */
    public WorkDetailViewModel (AsmrWork selected) {
        work = selected == null ? null : workService.getById(selected.getId());
        title=new SimpleStringProperty();
        star=new SimpleBooleanProperty(false);
        circle=new SimpleStringProperty();
        circleVisible=new SimpleBooleanProperty(true);
        categories= FXCollections.observableArrayList();
        vas=FXCollections.observableArrayList();
        tags=FXCollections.observableArrayList();
        cover=new SimpleObjectProperty<>();
        online=new SimpleBooleanProperty(work != null && Integer.valueOf(1).equals(work.getStorageType()));
        if (work != null) update();

        // 订阅作品数据变更（封面、标题等），就地刷新详情页
        EventBus.subscribe(NotificationKey.MAIN_GRID_WORK_UPDATE, (s, objects) -> {
            AsmrWork w = (AsmrWork) objects[0];
            if (work != null && work.getId() == w.getId()) {
                work = w;
                update();
            }
        });

    }

    /**
     * 重新从磁盘读取封面及各字段，刷新详情页展示
     */
    public void update(){
        if (work.getTitleCN()!=null){
            title.set(work.getTitleCN());
        }else{
            title.set(work.getTitle());
        }
        star.set(work.getFavorites());
        circle.set(work.getCircle());
        circleVisible.set(circle.get()!=null && !circle.get().isEmpty());

        // 编号 / 日期 / 标记 / 评分：数据为空时由界面隐藏对应行
        workId.set(work.getWorkId());
        release.set(work.getRelease());
        rate.set(work.getRate());
        subtitle.set(Boolean.TRUE.equals(work.getSubtitle()));
        allAges.set(Boolean.FALSE.equals(work.getNsfw()));

        categories.setAll(splitCsv(work.getCategories()));
        vas.setAll(splitCsv(work.getVas()));
        tags.setAll(splitCsv(work.getTags()));

        cover.set(getMainCoverImage(work));
    }

    /**
     * 将逗号分隔的字符串拆成列表，空值时返回空列表，避免旧数据残留。
     */
    private List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::strip)
                .filter(item -> !item.isEmpty())
                .toList();
    }





    /**
     * 将作品收藏状态设为目标值并广播刷新，使卡片与详情页同步。
     *
     * @param star 目标收藏状态
     */
    public void star(boolean star){
        workService.setFavorite(work, star);
        EventBus.publish(NotificationKey.MAIN_GRID_WORK_UPDATE,work);
    }

    /**
     * 保存作品评分并同步到作品对象，使卡片与详情页显示一致。
     *
     * @param value 0 至 5 的评分，按半星步长存储
     */
    public void updateRate(double value) {
        if (work == null) {
            return;
        }
        workService.updateRate(work, value);
        work.setRate(value);
        rate.set(value);
        // 广播给作品卡片等订阅方；本 ViewModel 自身也订阅该键，重复刷新只是幂等赋值
        EventBus.publish(NotificationKey.MAIN_GRID_WORK_UPDATE, work);
    }

    /**
     * 复制作品编号到系统剪切板，并给出结果提示。
     */
    public void copyWorkId() {
        String currentWorkId = workId.get();
        if (currentWorkId == null || currentWorkId.isBlank()) {
            EventBus.publish(NotificationKey.MESSAGE,
                    new MessageInfo(MessageType.WARNING, "没有识别到作品编号"));
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(currentWorkId);
        Clipboard.getSystemClipboard().setContent(content);
        EventBus.publish(NotificationKey.MESSAGE,
                new MessageInfo(MessageType.SUCCESS, String.format("%s:已经复制到剪切板", currentWorkId)));
    }

    /**
     * 在系统文件管理器中打开作品所在目录；来源不支持时（如纯远程作品）给出警告提示。
     */
    public void openWorkDir(){
        if (work == null || !fileOperationService.openInSystem(work, work.getFilepath())) {
            EventBus.publish(NotificationKey.MESSAGE,
                    new cn.tealc.asmr.model.message.MessageInfo(cn.tealc.asmr.model.message.MessageType.WARNING,
                            "当前来源不支持在系统文件管理器中打开"));
        }
    }


    /**
     * 点击分类/标签/声优等元数据时跳转到对应的作品列表。
     *
     * @param key  要检索的元数据值（标签名、声优名等）
     * @param type 元数据类型，决定跳转后列表的过滤条件
     */
    public void gotoWorkList(String key, AsmrKeyType type){
        DI.get(NavigationService.class).push(new WorksRoute(WorkSource.of(type, key)));
    }


    /**
     * 播放当前列表所有的歌曲，注意是当前列表，不是该目录所有
     */
    public void playAll(List<AsmrFile> files){
        ObservableList<AudioTrack> list = playlistService.buildPlaylistFromFiles(work, files);
        if (list != null && !list.isEmpty()) {
            VlcPlayer.getInstance().initAndPlay(list, 0);
        } else {
            Alerts.warning()
                    .title("无法播放")
                    .message("当前文件夹中没有识别到可播放的音频文件。")
                    .ok("知道了")
                    .show();
        }
    }



    private Image getMainCoverImage(AsmrWork work) {
        return CoverUtils.getCoverForWork(work, "main", "cover-main", 400, 300, true, DEFAULT_COVER);
    }


    public AsmrWork getWork() {
        return work;
    }

    public boolean isOnline() {
        return online.get();
    }

    public SimpleBooleanProperty onlineProperty() {
        return online;
    }

    public boolean isStar() {
        return star.get();
    }

    public SimpleBooleanProperty starProperty() {
        return star;
    }

    public String getCircle() {
        return circle.get();
    }

    public SimpleStringProperty circleProperty() {
        return circle;
    }

    public ObservableList<String> getCategories() {
        return categories;
    }

    public ObservableList<String> getVas() {
        return vas;
    }

    public Image getCover() {
        return cover.get();
    }

    public SimpleObjectProperty<Image> coverProperty() {
        return cover;
    }

    public String getTitle() {
        return title.get();
    }

    public SimpleStringProperty titleProperty() {
        return title;
    }

    public boolean isCircleVisible() {
        return circleVisible.get();
    }

    public SimpleBooleanProperty circleVisibleProperty() {
        return circleVisible;
    }

    public ObservableList<String> getTags() {
        return tags;
    }

    public void setTags(ObservableList<String> tags) {
        this.tags = tags;
    }

    public String getWorkId() {
        return workId.get();
    }

    public SimpleStringProperty workIdProperty() {
        return workId;
    }

    public String getRelease() {
        return release.get();
    }

    public SimpleStringProperty releaseProperty() {
        return release;
    }

    public Double getRate() {
        return rate.get();
    }

    public SimpleObjectProperty<Double> rateProperty() {
        return rate;
    }

    public boolean isSubtitle() {
        return subtitle.get();
    }

    public SimpleBooleanProperty subtitleProperty() {
        return subtitle;
    }

    public boolean isAllAges() {
        return allAges.get();
    }

    public SimpleBooleanProperty allAgesProperty() {
        return allAges;
    }
}
