package cn.tealc.asmr.utils;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import cn.tealc.asmr.Main;
import cn.tealc.asmr.config.Config;
import javafx.application.Application;

/**
 * JavaFX 样式表资源的定位与应用入口。
 * <p>
 * 集中声明各功能模块的 CSS 文件名常量，并按当前主题（{@code Config.setting.themeModel}）
 * 解析出 classpath 下对应的 URL。以 {@code /skin/} 开头的样式已合并为单一文件，不随主题切换。
 *
 * @author Leck
 */
public class CssLoader {
    public static final String popupVolume="/skin/item/popup-volume.css";
    public static final String songsList="/skin/item/songs-list.css";
    public static final String alert="/skin/alert.css";
    public static final String baseUI="/skin/BaseUI.css";
    /** 通用弹窗样式：Dialogs、Alerts 及加了 dialog-surface 的旧式弹窗共用。 */
    public static final String dialogCommon="/skin/dialog-common.css";
    /** 赞助弹窗样式。 */
    public static final String sponsorDialog="/skin/sponsor-dialog.css";
    public static final String categories="/skin/categories.css";
    public static final String dashboard="/skin/dashboard.css";
    public static final String galgame="/skin/galgame.css";
    public static final String main="/skin/main.css";
    public static final String atlantafx="/atlantafx.css";
    public static final String workGrid="/skin/main-grid.css";
    public static final String mediaLibrary="/skin/media-library.css";
    public static final String playerDetail="/skin/player-detail.css";
    public static final String setting="/skin/setting.css";
    public static final String svg="/SVGS.css";
    public static final String asmrLrcRename="/skin/asmr-lrc-rename.css";
    public static final String moveAsmr="/skin/move-asmr.css";
    public static final String tools="/skin/tools.css";
    public static final String guide="/skin/guide.css";
    public static final String lrcStage="/skin/item/lrc-stage.css";
    public static final String workDetail="/skin/work-detail.css";
    public static final String subtitleViewer="/subtitle-viewer.css";
    public static final String subtitleSync="/skin/subtitle-sync.css";
    public static final String imageViewer="/image-viewer.css";

    public static final String playlist="/skin/item/play-list.css";
    public static final String updateInfo="/skin/item/update-info.css";
    public static final String updateTag="/skin/item/update-tag.css";

    public static final String collection="/skin/collection/collection.css";
    public static final String workAlbum="/skin/work-album.css";
    public static final String openListAccount="/skin/openlist-account.css";
    public static final String openListLibrary="/skin/openlist-library.css";
    /**
     * 按主题解析样式表的 classpath URL。
     * <p>
     * 以 {@code /skin/} 开头的名称解析到合并后的单份样式（不分主题）；其余名称在浅色主题下走
     * {@code white-skin} 目录、深色主题下走 {@code black-skin} 目录。资源缺失时 {@code getResource}
     * 返回 null，将抛出 {@link NullPointerException}。
     *
     * @param cssName 以 {@code /} 开头的样式名
     * @return 可交给 JavaFX 的外部表单 URL
     */
    public static String getCss(String cssName){
        // 合并到 skin 目录的单份 CSS（全 atlantafx 变量），不随主题切换
        if (cssName.startsWith("/skin/")){
            return Main.class.getResource("/cn/tealc/asmr/css" + cssName).toExternalForm();
        }
        if (Config.setting.themeModel.get()){
            return Main.class.getResource("/cn/tealc/asmr/css/white-skin"+cssName).toExternalForm();
        }else{
            return Main.class.getResource("/cn/tealc/asmr/css/black-skin"+cssName).toExternalForm();
        }
    }


    /**
     * 根据当前主题将全局用户代理样式表设置为 PrimerLight 或 PrimerDark。
     */
    public static void setTheme(){
        if (Config.setting.themeModel.get()){
            Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        }else{
            Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
        }
    }

    /**
     * 解析 {@code /cn/tealc/asmr/css} 根目录下的共享样式表 URL，不随主题切换。
     *
     * @param cssName 以 {@code /} 开头的样式名
     * @return 外部表单 URL，资源缺失时抛出 {@link NullPointerException}
     */
    public static String getCommonCss(String cssName){
        return Main.class.getResource("/cn/tealc/asmr/css"+cssName).toExternalForm();
    }
}
