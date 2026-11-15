package cn.tealc.asmr.mvvm;

import javafx.scene.Parent;

/**
 * View 加载结果容器。
 * <p>
 * 由 {@link FluentViewLoader} 创建，持有已加载的 View 根节点和对应的 ViewModel 实例。
 *
 * @param <V>  View 类型
 * @param <VM> ViewModel 类型
 * @author Leck
 */
public class ViewTuple<V, VM> {
    private final V view;
    private final Parent viewRoot;
    private final VM viewModel;

    /**
     * @param view      View 实例（controller 或 JavaView 节点本身）
     * @param viewRoot  View 的根节点（FXMLLoader.getRoot() 或 JavaView 本身）
     * @param viewModel 绑定的 ViewModel 实例
     */
    public ViewTuple(V view, Parent viewRoot, VM viewModel) {
        this.view = view;
        this.viewRoot = viewRoot;
        this.viewModel = viewModel;
    }

    /**
     * 获取 View 根节点，用于加入场景树。
     */
    public Parent getView() {
        return viewRoot;
    }

    /**
     * 获取 View 实例（controller/code-behind）。
     */
    public V getCodeBehind() {
        return view;
    }

    /**
     * 获取 ViewModel 实例。
     */
    public VM getViewModel() {
        return viewModel;
    }
}
