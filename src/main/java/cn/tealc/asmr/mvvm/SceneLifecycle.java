package cn.tealc.asmr.mvvm;

/**
 * 界面生命周期接口。
 * <p>
 * ViewModel 实现此接口后，当关联的 View 根节点加入或移出 Scene Graph 时，
 * 会自动触发 {@link #onViewAdded()} 或 {@link #onViewRemoved()} 回调。
 * <p>
 * 触发机制：{@link FluentViewLoader} 在加载 View 时监听根节点的
 * {@code sceneProperty()}。节点进入 Scene Graph 时 sceneProperty 从 null
 * 变为 Scene 对象，移出时变回 null。
 * <p>
 * 在 StackPane 叠加场景下同样有效：
 * <ul>
 *   <li>add 到父容器 → onViewAdded</li>
 *   <li>remove 出父容器 → onViewRemoved</li>
 * </ul>
 * <p>
 * 典型用途：在 onViewRemoved 中取消事件订阅、解除双向绑定、释放资源。
 *
 * @author Leck
 */
public interface SceneLifecycle {

    /**
     * View 根节点加入 Scene Graph 时调用。
     */
    void onViewAdded();

    /**
     * View 根节点移出 Scene Graph 时调用。
     */
    void onViewRemoved();
}
