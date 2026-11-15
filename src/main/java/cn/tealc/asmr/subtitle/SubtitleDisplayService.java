package cn.tealc.asmr.subtitle;

import cn.tealc.asmr.config.Config;
import javafx.beans.property.StringProperty;

/**
 * 为字幕显示面提供统一的简繁转换入口。
 * <p>
 * 源字幕保持不变，转换仅发生在渲染前。服务只常驻加载词典索引，
 * 不缓存单条字幕的转换结果。
 * </p>
 */
public final class SubtitleDisplayService {

    private static final SubtitleChineseConverter CONVERTER = new SubtitleChineseConverter();

    private SubtitleDisplayService() {
    }

    /**
     * 按当前全局设置转换字幕文本。
     *
     * @param source 原始字幕文本
     * @return 用于显示的字幕文本
     */
    public static String convert(String source) {
        return CONVERTER.convert(source, getCurrentMode());
    }

    /**
     * 按指定模式转换字幕文本。
     *
     * @param source 原始字幕文本
     * @param mode   目标显示模式
     * @return 用于显示的字幕文本
     */
    public static String convert(String source, SubtitleScriptMode mode) {
        return CONVERTER.convert(source, mode);
    }

    /**
     * 获取当前全局字幕显示模式。
     *
     * @return 当前显示模式
     */
    public static SubtitleScriptMode getCurrentMode() {
        return SubtitleScriptMode.fromConfig(Config.setting.subtitleScriptMode.get());
    }

    /**
     * 获取可供界面监听的全局模式配置属性。
     *
     * @return 模式配置属性
     */
    public static StringProperty modeProperty() {
        return Config.setting.subtitleScriptMode;
    }
}
