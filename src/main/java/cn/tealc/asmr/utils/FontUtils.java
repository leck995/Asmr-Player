package cn.tealc.asmr.utils;

import javafx.scene.Node;
import javafx.scene.text.Font;

/**
 * 轻量字体工具：统一决定应用使用的字体族，并负责把字体应用到节点。
 * 替代参考项目 WutheringWavesTool 的 FontManager，避免引入其包名冲突。
 * 内置回退字体的加载由 {@code Main.initFont()} 完成，本类仅负责字体族选择与节点应用。
 */
public final class FontUtils {
    /** 系统默认字体：优先微软雅黑 */
    private static final String DEFAULT_FONT_FAMILY = "Microsoft YaHei";
    /** 无微软雅黑时的内置回退字体 */
    private static final String FALLBACK_FONT_FAMILY = "HarmonyOS Sans SC";

    private FontUtils() {
    }

    /**
     * 返回当前实际生效的字体族：系统存在微软雅黑时用之，否则使用内置回退字体。
     *
     * @return 字体族名称
     */
    public static String getFontFamily() {
        return Font.getFamilies().contains(DEFAULT_FONT_FAMILY)
                ? DEFAULT_FONT_FAMILY
                : FALLBACK_FONT_FAMILY;
    }

    /**
     * 将当前字体应用到节点，字体族沿节点向下继承。
     *
     * @param node 待应用字体的节点
     */
    public static void applyTo(Node node) {
        node.setStyle("-fx-font-family: \"" + getFontFamily() + "\";");
    }
}
