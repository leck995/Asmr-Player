package cn.tealc.asmr.mvvm;

/**
 * Java View 标记接口。
 * <p>
 * 实现此接口的类直接继承 JavaFX Pane（如 AnchorPane、StackPane），
 * 在构造器或 initialize 方法中用纯 Java 代码构建 UI，无对应 FXML 文件。
 * 加载时由 {@link FluentViewLoader} 反射实例化、注入 {@link InjectViewModel} 字段，
 * 然后调用 initialize 方法。
 *
 * @param <V> 该 View 绑定的 ViewModel 类型
 * @author Leck
 */
public interface JavaView<V extends ViewModel> {
}
