package cn.tealc.asmr.ui;

/**
 * 换肤接口：约定视图重新加载自身业务样式表的动作。
 * <p>
 * 没有集中派发方，各实现类自行决定调用时机：{@code WorkDetailView} 在
 * {@code initialize} 中调用 {@link #changeSkin()} 并在其中订阅
 * {@code CHANGE_SKIN} 事件；{@code CategoriesView}、{@code WorkGridView}
 * 随 scene 的挂载/卸载订阅 {@code CHANGE_SKIN}，回调直接走内部的
 * {@code applyStyleSheet()}，{@code changeSkin()} 只转发同一逻辑。
 *
 * @author Leck
 */
public interface TeaSkin {
    public void changeSkin();
}
