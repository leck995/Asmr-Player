package cn.tealc.asmr.utils;

import javafx.scene.Node;
import javafx.scene.layout.AnchorPane;

/**
 * {@link AnchorPane} 锚点布局的便捷封装。
 *
 * @author Leck
 */
public class AnchorPaneUtils {
    /**
     * 一次性设置节点在 {@link AnchorPane} 中的上下左右四个锚点约束。
     *
     * @param pane   待定位的节点
     * @param top    上锚点像素值，传 {@code null} 表示不约束该边
     * @param right  右锚点像素值，传 {@code null} 表示不约束该边
     * @param bottom 下锚点像素值，传 {@code null} 表示不约束该边
     * @param left   左锚点像素值，传 {@code null} 表示不约束该边
     */
    public static void setPosition(Node pane, Double top, Double right, Double bottom, Double left){
        AnchorPane.setTopAnchor(pane,top);
        AnchorPane.setRightAnchor(pane,right);
        AnchorPane.setBottomAnchor(pane,bottom);
        AnchorPane.setLeftAnchor(pane,left);
    }
}