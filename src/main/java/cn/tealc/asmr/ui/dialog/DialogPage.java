package cn.tealc.asmr.ui.dialog;

import cn.tealc.asmr.ui.component.PageHeader;
import javafx.scene.Node;

import java.util.List;

/**
 * 弹窗页面：界面分别声明标题、正文与动作，由宿主放入弹窗的对应插槽。
 * <p>
 * 适用于自身已是完整界面的视图（作品移动、后台任务等）：{@link #header()} 进入
 * 弹窗标题区，{@link #content()} 进入正文区，{@link #actions()} 进入按钮区，
 * 三段不会被塞进同一个 content 节点。
 * <p>
 * 标题统一使用 {@link PageHeader}；弹窗会隐藏其自带分隔线，并套用弹窗标题区的
 * 内边距与字号，保证与其他弹窗一致。
 * <p>
 * 动作按钮不会自动关闭弹窗，需要关闭时在处理器中调用 {@link DialogHandle#close()}。
 *
 * @author Leck
 */
public interface DialogPage {

    /**
     * 弹窗标题区。
     */
    PageHeader header();

    /**
     * 弹窗正文区。
     */
    Node content();

    /**
     * 弹窗按钮区，按顺序排列。
     */
    List<DialogAction> actions();

    /**
     * 弹窗宽度，0 表示由内容决定。
     */
    default double width() {
        return 0;
    }

    /**
     * 该界面需要的业务样式表；通用弹窗样式由宿主自动加载。
     */
    default List<String> stylesheets() {
        return List.of();
    }

    default boolean overlayClickCloses() {
        return true;
    }

    default boolean escapeCloses() {
        return true;
    }

    /**
     * 弹窗创建完成后、展示前回调，可用于保存句柄或绑定界面内的关闭动作。
     */
    default void onDialogAttached(DialogHandle handle) {
    }

    /**
     * 弹窗展示完成后回调，用于聚焦、滚动定位等。
     */
    default void onDialogShown() {
    }

    /**
     * 弹窗关闭后回调，用于释放订阅、停止任务等。
     */
    default void onDialogClosed() {
    }
}
