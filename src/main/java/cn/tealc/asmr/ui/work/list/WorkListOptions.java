package cn.tealc.asmr.ui.work.list;

import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * 通用作品列表的布局与行为配置。
 *
 * @param cellFactory      卡片工厂
 * @param cellContext      卡片操作回调
 * @param sideButtonPaging 是否响应鼠标侧键翻页
 * @param hgap             卡片水平间距
 * @param vgap             卡片垂直间距
 * @param wheelFactor      滚轮滚动倍率
 * @param emptyText        空列表标题
 * @param emptyDescription 空列表说明
 * @author Leck
 */
public record WorkListOptions(
        WorkCellFactory cellFactory,
        WorkCellContext cellContext,
        boolean sideButtonPaging,
        double hgap,
        double vgap,
        DoubleSupplier wheelFactor,
        String emptyText,
        String emptyDescription) {

    public WorkListOptions {
        Objects.requireNonNull(cellFactory, "cellFactory");
        Objects.requireNonNull(cellContext, "cellContext");
        wheelFactor = wheelFactor == null ? () -> 2.0 : wheelFactor;
        emptyText = emptyText == null || emptyText.isBlank() ? "暂无作品" : emptyText;
        emptyDescription = emptyDescription == null ? "" : emptyDescription;
    }

    /**
     * 主网格样式：分页浮层在滚动到底部时出现。
     */
    public static WorkListOptions grid(WorkCellFactory cellFactory, WorkCellContext cellContext,
                                       DoubleSupplier wheelFactor) {
        return new WorkListOptions(cellFactory, cellContext, true, 20, 15, wheelFactor,
                "暂无作品", "导入媒体库，或调整筛选条件后再试。");
    }

    /**
     * 作品集详情样式：分页浮层在滚动到底部时出现。
     */
    public static WorkListOptions album(WorkCellFactory cellFactory, WorkCellContext cellContext) {
        return new WorkListOptions(cellFactory, cellContext, false, 18, 18, () -> 2.0,
                "暂无作品", "把作品加入本集册后，会显示在这里。");
    }
}
