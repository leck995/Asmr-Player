package cn.tealc.asmr.ui.main;

import cn.tealc.asmr.eventbus.EventBus;

import cn.tealc.asmr.di.Inject;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.model.message.MessageType;
import cn.tealc.asmr.mvvm.ViewModel;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.service.MetadataSyncService;
import cn.tealc.asmr.service.WorkService;
import cn.tealc.asmr.ui.work.list.WorkListModel;
import cn.tealc.asmr.ui.navigation.WorkSource;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;

/**
 * 主内容作品列表的页面状态。
 * <p>
 * 负责排序、筛选和当前数据来源；分页加载与局部更新由 {@link WorkListModel} 处理，
 * 页面切换由导航服务统一管理。
 *
 * @author Leck
 */
public class WorkGridViewModel implements ViewModel {

    private final WorkListModel listModel = new WorkListModel(() -> Config.setting.gridListCount.get());
    private final SimpleBooleanProperty sqlOrder = new SimpleBooleanProperty(true);
    private final SimpleBooleanProperty sqlSubtext = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty sqlNsfw = new SimpleBooleanProperty(true);
    private final SimpleIntegerProperty sqlType = new SimpleIntegerProperty(1);
    private final SimpleBooleanProperty sqlNoMate = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty showLocal = new SimpleBooleanProperty(true);
    private final SimpleBooleanProperty showNetwork = new SimpleBooleanProperty(true);
    private final StringProperty sourceTitle = new SimpleStringProperty("全部");

    private final ReadOnlyObjectWrapper<WorkSource> source = new ReadOnlyObjectWrapper<>(WorkSource.all());

    @Inject
    private WorkService workService;
    @Inject
    private MetadataSyncService metadataSyncService;

    public WorkGridViewModel() {
        ChangeListener<Object> reloadListener = (observable, oldValue, newValue) -> applySource(getSource());
        sqlNsfw.addListener(reloadListener);
        sqlSubtext.addListener(reloadListener);
        sqlOrder.addListener(reloadListener);
        sqlNoMate.addListener(reloadListener);
        showLocal.addListener(reloadListener);
        showNetwork.addListener(reloadListener);
        sqlType.addListener(reloadListener);
    }

    /**
     * 切换作品列表的数据来源并回到第一页。
     */
    public void applySource(WorkSource source) {
        WorkSource next = source == null ? WorkSource.all() : source;
        this.source.set(next);
        sourceTitle.set(next.title());
        listModel.setSource(new WorkServicePageSource(workService, next, currentFilter()), true);
    }

    public WorkSource getSource() {
        return source.get();
    }

    public ReadOnlyObjectProperty<WorkSource> sourceProperty() {
        return source.getReadOnlyProperty();
    }

    private WorkFilter currentFilter() {
        return new WorkFilter(sqlType.get(), sqlOrder.get(), sqlSubtext.get(), sqlNsfw.get(),
                sqlNoMate.get(), showLocal.get(), showNetwork.get());
    }

    public void syncMetadata() {
        var works = listModel.getItems();
        if (works.isEmpty()) {
            EventBus.publish(NotificationKey.MESSAGE,
                    new MessageInfo(MessageType.WARNING, "当前页面没有可同步的作品"));
            return;
        }
        String path = works.get(0).getParentPath();
        metadataSyncService.syncBatch(works, path);
    }

    public void changeItemShowModel() {
        Config.setting.gridItemDetailModel.set(!Config.setting.gridItemDetailModel.get());
    }

    /**
     * 重新加载当前页，保留页码与滚动位置。
     */
    public void reload() {
        listModel.reload();
    }

    public WorkListModel getListModel() {
        return listModel;
    }

    public StringProperty sourceTitleProperty() {
        return sourceTitle;
    }

    public boolean isSqlOrder() {
        return sqlOrder.get();
    }

    public SimpleBooleanProperty sqlOrderProperty() {
        return sqlOrder;
    }

    public boolean isSqlSubtext() {
        return sqlSubtext.get();
    }

    public SimpleBooleanProperty sqlSubtextProperty() {
        return sqlSubtext;
    }

    public boolean isSqlNsfw() {
        return sqlNsfw.get();
    }

    public SimpleBooleanProperty sqlNsfwProperty() {
        return sqlNsfw;
    }

    public int getSqlType() {
        return sqlType.get();
    }

    public SimpleIntegerProperty sqlTypeProperty() {
        return sqlType;
    }

    public boolean isSqlNoMate() {
        return sqlNoMate.get();
    }

    public SimpleBooleanProperty sqlNoMateProperty() {
        return sqlNoMate;
    }

    public boolean isShowLocal() {
        return showLocal.get();
    }

    public SimpleBooleanProperty showLocalProperty() {
        return showLocal;
    }

    public boolean isShowNetwork() {
        return showNetwork.get();
    }

    public SimpleBooleanProperty showNetworkProperty() {
        return showNetwork;
    }

    public void setSqlOrder(boolean sqlOrder) {
        this.sqlOrder.set(sqlOrder);
    }

    public void setSqlSubtext(boolean sqlSubtext) {
        this.sqlSubtext.set(sqlSubtext);
    }

    public void setSqlNsfw(boolean sqlNsfw) {
        this.sqlNsfw.set(sqlNsfw);
    }

    public void setSqlType(int sqlType) {
        this.sqlType.set(sqlType);
    }

    public void setSqlNoMate(boolean sqlNoMate) {
        this.sqlNoMate.set(sqlNoMate);
    }

    public void setShowLocal(boolean showLocal) {
        this.showLocal.set(showLocal);
    }

    public void setShowNetwork(boolean showNetwork) {
        this.showNetwork.set(showNetwork);
    }
}
