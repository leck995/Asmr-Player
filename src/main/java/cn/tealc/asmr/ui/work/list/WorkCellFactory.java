package cn.tealc.asmr.ui.work.list;

import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.ui.work.cells.BigWorkCell;
import cn.tealc.asmr.ui.work.cells.SmallWorkCell;
import javafx.scene.Node;

/**
 * 根据列表配置创建作品卡片。
 *
 * @author Leck
 */
@FunctionalInterface
public interface WorkCellFactory {

    Node create(AsmrWork work, WorkCellContext context);

    /**
     * 跟随全局大图/小图设置。
     */
    static WorkCellFactory adaptive() {
        return (work, context) -> Config.setting.gridItemDetailModel.get()
                ? new BigWorkCell(work, context)
                : new SmallWorkCell(work, context);
    }

    /**
     * 固定使用小卡片。
     */
    static WorkCellFactory small() {
        return SmallWorkCell::new;
    }
}
