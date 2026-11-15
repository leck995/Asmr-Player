package cn.tealc.asmr.ui.work;

import cn.tealc.asmr.eventbus.EventBus;

import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.Actor;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.model.message.MessageType;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.service.DictionaryService;
import cn.tealc.asmr.service.WorkService;
import cn.tealc.asmr.mvvm.ViewModel;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Pair;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link UpdateTagView} 的视图模型，维护"全部标签 + 选中状态"列表并负责保存与新增标签。
 * <p>
 * 构造时把作品已有的标签标记为选中并置于列表前部，其余字典标签作为未选候选追加其后。
 *
 * @author Leck
 */
public class UpdateTagViewModel implements ViewModel {
    private final WorkService workService = DI.get(WorkService.class);
    private final DictionaryService dictService = DI.get(DictionaryService.class);
    private final ObservableList<Pair<String,Boolean>> tagList = FXCollections.observableArrayList();
    private AsmrWork work;

    /**
     * 加载字典里的全部标签与作品已有标签，构建"标签名-是否选中"列表；已选标签在前。
     *
     * @param work 正在编辑标签的作品，按其主键重新读取标签字段
     */
    public UpdateTagViewModel(AsmrWork work) {
        this.work = work;
        List<Actor> list = dictService.getAllTags();
        AsmrWork workDb = workService.getById(work.getId());
        if (workDb.getTags() != null && !workDb.getTags().isEmpty()){
            String[] split = workDb.getTags().split(",");
            for (String tag : split) {
                tagList.add(new Pair<>(tag, true));
            }
            Iterator<Actor> iterator=list.iterator();
            while (iterator.hasNext()) {
                Actor actor = iterator.next();
                for (String tag : split) {
                    if (tag.equals(actor.getName())) {
                        iterator.remove();
                    }
                }
            }
        }



        list.stream()
                .map(a->new Pair<>(a.getName(),false))
                .forEach(tagList::add);







    }


    /**
     * 把选中的标签逐个登记进字典，并以逗号串更新作品的标签字段。
     *
     * @param tags 选中的标签名列表，用于登记到字典
     * @param tag  拼接好的标签字符串，直接写入作品；为 {@code null} 或空白时按 {@code null} 存库
     * @return 数据库更新是否成功；成功时同步内存对象并广播刷新事件
     */
    public boolean save(List<String> tags,String tag){
        for (String array : tags) {
            dictService.addTag(array);
        }
        boolean updated = workService.updateWorkTag(work.getId(),tag);
        if (updated){
            // 同步内存对象并通知列表刷新，否则卡片会一直显示旧标签
            work.setTags(tag == null || tag.isBlank() ? null : tag);
            EventBus.publish(NotificationKey.MAIN_GRID_WORK_UPDATE, work);
        }
        return updated;
    }

    public boolean cancel(){
        return true;
    }

    /**
     * 新建标签：写入字典成功后追加到候选列表并置为选中，重复或失败时弹出警告。
     *
     * @param tagName 新标签名
     * @return 创建是否成功
     */
    public boolean addTag(String tagName){
        boolean status = dictService.addTag(tagName);
        if (status){
            tagList.add(new Pair<>(tagName, true));
        }else {
            EventBus.publish(NotificationKey.MESSAGE,new MessageInfo(MessageType.WARNING,"无法创建标签，可能已存在"));
        }
        return status;
    }

    public ObservableList<Pair<String, Boolean>> getTagList() {
        return tagList;
    }
}