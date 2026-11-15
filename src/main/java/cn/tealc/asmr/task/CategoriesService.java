package cn.tealc.asmr.task;

import cn.tealc.asmr.model.AsmrKeyType;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.Actor;
import cn.tealc.asmr.service.DictionaryService;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;

import java.util.List;

/**
 * 定时刷新字典列表的后台服务，在任务线程中查询数据库，避免阻塞 JavaFX UI 线程。
 * <p>
 * 按当前设置的 {@link AsmrKeyType} 分派查询：CATEGORY（分类）/ VA（声优）/
 * TAG（标签）/ CIRCLE（社团），其余取值不查询。
 *
 * @author Leck
 */
public class CategoriesService extends ScheduledService<List<Actor>> {
    private AsmrKeyType asmrKeyType;

    public void setAsmrKeyType(AsmrKeyType asmrKeyType) {
        this.asmrKeyType = asmrKeyType;
    }

    /**
     * 创建单次查询任务；{@code asmrKeyType} 不属于四种字典类型时返回 {@code null} 列表。
     */
    @Override
    protected Task<List<Actor>> createTask() {
        Task<List<Actor>> task=new Task<List<Actor>>() {

            @Override
            protected List<Actor> call() throws Exception {
                DictionaryService dictService = DI.get(DictionaryService.class);
                List<Actor> list=null;
                switch (asmrKeyType){
                    case CATEGORY -> list = dictService.getAllCategories();
                    case VA -> list = dictService.getAllVas();
                    case TAG -> list = dictService.getAllTags();
                    case CIRCLE -> list = dictService.getAllCircles();
                }
                return list;
            }
        };
        return task;
    }
}