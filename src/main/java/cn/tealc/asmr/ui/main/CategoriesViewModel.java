package cn.tealc.asmr.ui.main;

import cn.tealc.asmr.model.AsmrKeyType;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.eventbus.EventBus;
import cn.tealc.asmr.ui.dialog.Alerts;
import cn.tealc.asmr.model.Actor;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.model.message.MessageType;
import cn.tealc.asmr.service.AnywhereDoorService;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.service.DictionaryService;
import cn.tealc.asmr.task.CategoriesService;
import cn.tealc.asmr.ui.main.MainView;
import cn.tealc.asmr.mvvm.ViewModel;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * 分类浏览页的 ViewModel，负责按类型异步取字典条目并维护选中项。
 * <p>
 * 查询走 {@link CategoriesService}（{@code ScheduledService}）：结果回主线程后
 * 立即 {@code cancel()} 终止周期调度，只做一次性取数，再把列表灌入 {@code items}。
 * <p>
 * 删除与快捷入口固定都作用于 {@code selectActor}（由视图右键时写入），
 * 字典写入通过 {@link DictionaryService} 与 {@link AnywhereDoorService} 完成。
 *
 * @author Leck
 */
public class CategoriesViewModel implements ViewModel {
    private final DictionaryService dictService = DI.get(DictionaryService.class);
    private SimpleBooleanProperty loading;
    private ObservableList<Actor> items;
    private CategoriesService categoriesService;

    private SimpleObjectProperty<AsmrKeyType> type;

    /** 右键选中的条目，供删除与固定快捷入口使用；未选中时为 null */
    private SimpleObjectProperty<Actor> selectActor;
    /**
     * 创建即发起一次按 CATEGORY 的异步查询，{@code loading} 先置为 true。
     */
    public CategoriesViewModel() {
        type=new SimpleObjectProperty<>(AsmrKeyType.CATEGORY);
        loading=new SimpleBooleanProperty(true);
        items= FXCollections.observableArrayList();
        selectActor=new SimpleObjectProperty<>();
        categoriesService=new CategoriesService();
        categoriesService.valueProperty().addListener((observableValue, actors, list) -> {
            if (list!=null){
                categoriesService.cancel();
                loading.set(false);
                items.setAll(list);
            }
        });
        categoriesService.setAsmrKeyType(AsmrKeyType.CATEGORY);
        categoriesService.start();

    }

    /**
     * 切换列表类型并重新查询。
     * <p>
     * 用 {@code restart()} 而非 {@code start()}：上一次查询可能已被结果回调
     * {@code cancel()} 掉，重启才能再次进入 READY→RUNNING。
     *
     * @param type 目标类型，仅支持 CATEGORY/TAG/CIRCLE/VA 四种字典类型
     */
    public void update(AsmrKeyType type){
        this.type.set(type);
        categoriesService.setAsmrKeyType(type);
        categoriesService.restart();
        loading.set(true);
    }



    /**
     * 删除当前选中条目对应的字典记录。
     * <p>
     * 条目下仍关联作品（{@code num > 0}）时只发警告消息，不弹确认框；
     * 确认后按当前类型分派到对应的删除方法并从列表移除该行。
     * 调用前必须已有选中项，否则取 {@code getSelectActor()} 会抛空指针。
     */
    public void delete(){
        String selectName = getSelectActor().getName();
        if (getSelectActor().getNum() > 0){
            EventBus.publish(NotificationKey.MESSAGE,new MessageInfo(MessageType.WARNING,"待删除的类别下存在作品，无法删除"));
        }else {
            Alerts.warning().message("确认从数据库中删除吗？").ok("确定", handle -> {
                if (AsmrKeyType.CATEGORY==getType()){
                    dictService.deleteCategoryByName(selectName);
                    items.remove(getSelectActor());
                    return;
                }
                if (AsmrKeyType.CIRCLE==getType()){
                    dictService.deleteCircleByName(selectName);
                    items.remove(getSelectActor());
                    return;
                }
                if (AsmrKeyType.VA == getType()){
                    dictService.deleteVaByName(selectName);
                    items.remove(getSelectActor());
                    return;
                }

                if (AsmrKeyType.TAG == getType()){
                    dictService.deleteTagByName(selectName);
                    items.remove(getSelectActor());
                }
            }).cancel().show();
        }


    }

    /**
     * 把当前选中条目固定到快捷入口（任意门）。
     * <p>
     * 未选中时直接返回；同类型同键已存在则跳过，写入失败（如存储异常）时发错误消息，
     * 不改动列表。
     */
    public void fixed(){
        if (getSelectActor() == null) return;
        String selectName = getSelectActor().getName();
        AnywhereDoorService service = DI.get(AnywhereDoorService.class);
        try {
            if (!service.contains(getType(), selectName)) service.add(getType(), selectName, null);
        } catch (RuntimeException error) {
            EventBus.message(new MessageInfo(MessageType.ERROR, "固定快捷入口失败：" + error.getMessage()));
        }
    }

    /**
     * 新建标签，成功后自动切回标签列表刷新一次。
     *
     * @param name        标签名称，调用方已去首尾空白；重名会失败
     * @param description 标签描述，可为空串
     * @return 写入是否成功，失败时由调用方在对话框内展示错误
     */
    public boolean createTag(String name, String description) {
        boolean status = dictService.addTag(name, description);
        if (status) {
            update(AsmrKeyType.TAG);
        }
        return status;
    }

    /**
     * 修改指定标签的描述，成功后刷新标签列表。
     *
     * @param id          标签主键，不存在时返回 false
     * @param description 新描述，调用方已去首尾空白
     * @return 写入是否成功
     */
    public boolean updateTagDescription(int id, String description) {
        boolean status = dictService.updateTagDescription(id, description);
        if (status) {
            update(AsmrKeyType.TAG);
        }
        return status;
    }



    public boolean isLoading() {
        return loading.get();
    }

    public SimpleBooleanProperty loadingProperty() {
        return loading;
    }

    public ObservableList<Actor> getItems() {
        return items;
    }

    public AsmrKeyType getType() {
        return type.get();
    }

    public SimpleObjectProperty<AsmrKeyType> typeProperty() {
        return type;
    }

    public void setType(AsmrKeyType type) {
        this.type.set(type);
    }

    public Actor getSelectActor() {
        return selectActor.get();
    }

    public SimpleObjectProperty<Actor> selectActorProperty() {
        return selectActor;
    }

    public void setSelectActor(Actor selectActor) {
        this.selectActor.set(selectActor);
    }
}
