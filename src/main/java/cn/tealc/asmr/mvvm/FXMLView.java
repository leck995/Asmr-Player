package cn.tealc.asmr.mvvm;

/**
 * FXML View 标记接口。
 * <p>
 * 实现此接口的类作为 FXML 文件的 controller，布局定义在同包同名的 .fxml 文件中。
 * 加载时由 {@link FluentViewLoader} 解析 FXML、创建 controller 实例、注入
 * {@link InjectViewModel} 字段和 {@code @FXML} 字段，然后调用 initialize 方法。
 *
 * @param <V> 该 View 绑定的 ViewModel 类型
 * @author Leck
 */
public interface FXMLView<V extends ViewModel> {
}
