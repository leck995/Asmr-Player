package cn.tealc.asmr.ui.work.list;

/**
 * 支持自适应宽度的作品卡片。
 * <p>
 * 列表按容器宽度算出每行可放的数量后，把剩余空间平均分配给每张卡片，
 * 通过 {@link #setCardWidth(double)} 通知卡片调整自身及内部元素尺寸。
 * <p>
 * 实现类需要保证宽度变化后重新计算依赖宽度的内部元素（封面尺寸、标题截断等）。
 *
 * @author Leck
 */
public interface AdaptiveWidthCell {

    /**
     * 设置卡片实际宽度。
     *
     * @param width 卡片宽度，由列表根据容器宽度计算得出
     */
    void setCardWidth(double width);
}
