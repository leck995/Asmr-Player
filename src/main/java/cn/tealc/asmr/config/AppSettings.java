package cn.tealc.asmr.config;

import cn.tealc.asmr.model.galgame.GalgameSkin;
import cn.tealc.asmr.model.galgame.GalgameSkinRegistry;
import cn.tealc.asmr.utils.SecureUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

/**
 * 应用全部用户设置的载体，每个设置对应一个 JavaFX 属性字段。
 * <p>
 * 设置以 {@code Simple*Property} 公共字段声明，字段初始值即默认值，并为每项生成
 * getter / setter / {@code *Property()} 三件套；设置页等 UI 通过属性双向绑定直接
 * 观察和修改，无需再手动通知。
 * <p>
 * 持久化整对象走一份 JSON 文件：{@link Config} 的静态块在类加载时用 Jackson 从
 * {@code AppPaths.settingsFile()} 读入（未知字段忽略，见 {@code @JsonIgnoreProperties}），
 * 退出时经 {@link Config#saveProperties(boolean)} 写回。标注 {@code @JsonIgnore} 的
 * 运行时属性（如音频设备选择）不参与序列化；静态字段（如 {@code galgameSkin}）为
 * 运行时状态，Jackson 默认不序列化静态字段，因此同样不落盘。
 *
 * @author Leck
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppSettings {
    public SimpleStringProperty ignoreVersion=new SimpleStringProperty(); //跳过指定版本更新
    public SimpleBooleanProperty autoCheckVersion=new SimpleBooleanProperty(true);//检查更新

    /*===================================基本设置===================================*/
    public SimpleDoubleProperty stageWidth=new SimpleDoubleProperty(1200.0); //窗口大小
    public SimpleDoubleProperty stageHeight=new SimpleDoubleProperty(800.0); //窗口大小

    public SimpleBooleanProperty pKeyModel=new SimpleBooleanProperty(false);//是否启用启动密码，true 为开启
    public SimpleStringProperty startPassword=new SimpleStringProperty(); //启动密码

    public SimpleBooleanProperty mediaInnerInfoModel=new SimpleBooleanProperty(true);//优先使用音声内置信息
    public SimpleBooleanProperty textSmooth=new SimpleBooleanProperty(true);//设置字体平滑，可以减少字体发虚现象
    public SimpleBooleanProperty stopPlayOnEnd=new SimpleBooleanProperty(false); //作品播放完毕自动暂停
    public SimpleBooleanProperty themeModel=new SimpleBooleanProperty(true); //主题设置，true为白天主题，false为夜间主题

    public SimpleIntegerProperty closeType=new SimpleIntegerProperty(0); //设置关闭按钮功能，最小化和关闭，暂未启用
    public SimpleIntegerProperty deleteFileModel=new SimpleIntegerProperty(0); //删除文件设置，0是直接删除，1是转移到回收站，暂未启用


    public SimpleBooleanProperty userPlayingHistory=new SimpleBooleanProperty(false); //保存历史纪录

    /*===================================AI 翻译===================================*/
    public AiTranslationSettings aiTranslation = new AiTranslationSettings();

    /*===================================主界面设置===================================*/
    public SimpleIntegerProperty gridListCount=new SimpleIntegerProperty(30);//网格显示每页显示的数量
    public SimpleBooleanProperty gridItemDetailModel=new SimpleBooleanProperty(false);//设置详情大图模式
    /** 大图模式每行显示的卡片数量，卡片宽度由容器宽度与列数算出，窗口缩放时列数不变。 */
    public SimpleIntegerProperty gridColumnsBig=new SimpleIntegerProperty(4);
    /** 小图模式每行显示的卡片数量，与大图模式分开存储，两者互不影响。 */
    public SimpleIntegerProperty gridColumnsSmall=new SimpleIntegerProperty(6);
    public SimpleBooleanProperty gridItemDetailModelMaxTitle=new SimpleBooleanProperty(true); ////设置详情大图模式的标题是否有最大高度限制
    /**
     * 反转鼠标侧键翻页方向。
     * <p>
     * 默认关闭时，侧键「后退」翻到上一页、侧键「前进」翻到下一页；
     * 开启后两者对调，以适配不同鼠标驱动对侧键的映射习惯。
     */
    public SimpleBooleanProperty sideButtonPagingReversed=new SimpleBooleanProperty(false);


    /*===================================播放界面相关===================================*/
    public SimpleBooleanProperty detailAlbumEffectModel=new SimpleBooleanProperty(true);//设置播放界面封面阴影，默认开启
    public SimpleDoubleProperty detailAlbumEffectSize=new SimpleDoubleProperty(2);//播放界面封面阴影大小
    public SimpleBooleanProperty detailAlbumRadiusModel=new SimpleBooleanProperty(true);//播放界面封面圆角
    public SimpleDoubleProperty detailAlbumRadiusSize=new SimpleDoubleProperty(30);//播放界面封面圆角大小
    public SimpleBooleanProperty detailLrcAlignment=new SimpleBooleanProperty(false);//设置播放界面歌词对其方式，默认左对齐false,true为居中
    public SimpleStringProperty subtitleScriptMode=new SimpleStringProperty("ORIGINAL");//字幕显示文字体系
    /** 字幕同步偏移（毫秒）：正数延后、负数提前，叠加在同步器内置提前量之上。 */
    public SimpleIntegerProperty subtitleDelayMs=new SimpleIntegerProperty(0);


    /*===================================Galgame模式相关===================================*/
    public SimpleBooleanProperty galgameModel=new SimpleBooleanProperty(true);//设置Galgame模式，默认开启
    public SimpleStringProperty galgameImage=new SimpleStringProperty("默认");
    public SimpleDoubleProperty galgameMessageOpacity=new SimpleDoubleProperty(0.5);
    public SimpleBooleanProperty galgameBgModel=new SimpleBooleanProperty(true);//设置Galgame背景图片，默认true
    public SimpleStringProperty galgameBgSizeWidth=new SimpleStringProperty("1920");
    public SimpleStringProperty galgameBgSizeHeight=new SimpleStringProperty("1080");
    public SimpleBooleanProperty galgameAnimationModel=new SimpleBooleanProperty(true);//Galgame 动画效果开关，默认开启

    public static GalgameSkin galgameSkin = GalgameSkinRegistry.defaultSkin();

    /*===================================桌面歌词相关===================================*/
    public SimpleIntegerProperty desktopLRCStageWidth=new SimpleIntegerProperty(1000);//桌面歌词窗口宽度
    public SimpleIntegerProperty desktopLRCFontSize=new SimpleIntegerProperty(30);//桌面歌词字体大小
    public SimpleStringProperty desktopLRCFontColor=new SimpleStringProperty("#FFFFFF");//桌面歌词字体颜色
    public SimpleBooleanProperty desktopLRCBorderModel=new SimpleBooleanProperty(true);//桌面歌词阴影开关，默认开启；配置名沿用 Border，实际驱动 DropShadow
    public SimpleStringProperty desktopLRCBorderColor=new SimpleStringProperty("#00b8d4");//桌面歌词阴影（外发光）颜色，非描边

    public SimpleBooleanProperty desktopLRCStrokeModel=new SimpleBooleanProperty(true);//开启桌面歌词字体描边，默认开启
    public SimpleStringProperty desktopLRCStrokeColor=new SimpleStringProperty("#00b8d4");//桌面歌词字体描边颜色
    public SimpleBooleanProperty desktopLRCBoldModel=new SimpleBooleanProperty(false);//开启桌面歌词字体粗体,默认关闭
    /*==================================网盘相关=============================================*/


    /*==================================缓存相关=============================================*/
    public SimpleBooleanProperty cacheEnable=new SimpleBooleanProperty(true);//开启远程资源缓存，默认开启
    public SimpleIntegerProperty cacheMaxSizeMB=new SimpleIntegerProperty(500);//缓存上限（MB），默认 500MB



    /*==================================插件相关=============================================*/
    public SimpleBooleanProperty proxyModel=new SimpleBooleanProperty(false);//开启代理
    public SimpleStringProperty proxyHost=new SimpleStringProperty();//代理地址
    public SimpleStringProperty proxyPort=new SimpleStringProperty();//代理端口
    public SimpleStringProperty sourcesUrl=new SimpleStringProperty();  //同步网址
    public SimpleBooleanProperty syncImageReDownModel=new SimpleBooleanProperty(false); //同步时重新下载封面

    public SimpleBooleanProperty saveCoverToAimFolderModel=new SimpleBooleanProperty(false); //保存封面到目标文件夹

    public SimpleBooleanProperty syncSkipAlreadyModel=new SimpleBooleanProperty(true);//跳过已经同步的音声

    public SimpleBooleanProperty userChineseTitle=new SimpleBooleanProperty(true); //当作品有中文，优先使用中文


    /*==================================外部播放器相关=============================================*/
    public SimpleBooleanProperty externalPlayerEnabled=new SimpleBooleanProperty(false); //启用外部播放器播放视频
    public SimpleStringProperty externalPlayerPath=new SimpleStringProperty(""); //外部播放器可执行文件路径
    public SimpleStringProperty externalPlayerArgs=new SimpleStringProperty(""); //启动参数模板，留空按播放器类型自动识别


    /*==================================退出时记录播放进度相关=============================================*/
    public SimpleBooleanProperty asmrHistoryModel=new SimpleBooleanProperty(true);//开启退出时记录播放
    public SimpleBooleanProperty asmrOnlineModel=new SimpleBooleanProperty(true);//开启退出时记录播放类型
    public SimpleStringProperty asmrPath=new SimpleStringProperty();//正在播放的该音声媒体
    public SimpleIntegerProperty asmrPlayingIndex=new SimpleIntegerProperty();//正在播放的歌曲索引
    public SimpleDoubleProperty asmrPlayingCurrentTime=new SimpleDoubleProperty();//正在播放的歌曲时间




    /*==================================其他相关=============================================*/
    public SimpleBooleanProperty tempNodesSlider=new SimpleBooleanProperty(false);//设置播放界面NodesSlider的展开，默认关闭,找不到实现方法，暂未使用;
    public SimpleDoubleProperty volume=new SimpleDoubleProperty(1.0);

    @JsonIgnore
    public SimpleIntegerProperty audioChannel = new SimpleIntegerProperty(0);
    @JsonIgnore
    public SimpleIntegerProperty audioOutputDevice = new SimpleIntegerProperty(0);
    @JsonIgnore
    public SimpleIntegerProperty audioEffect = new SimpleIntegerProperty(0);


    public String getIgnoreVersion() {
        return ignoreVersion.get();
    }

    public SimpleStringProperty ignoreVersionProperty() {
        return ignoreVersion;
    }

    public void setIgnoreVersion(String ignoreVersion) {
        this.ignoreVersion.set(ignoreVersion);
    }

    public boolean isAutoCheckVersion() {
        return autoCheckVersion.get();
    }

    public SimpleBooleanProperty autoCheckVersionProperty() {
        return autoCheckVersion;
    }

    public void setAutoCheckVersion(boolean autoCheckVersion) {
        this.autoCheckVersion.set(autoCheckVersion);
    }

    public double getStageWidth() {
        return stageWidth.get();
    }

    public SimpleDoubleProperty stageWidthProperty() {
        return stageWidth;
    }

    public void setStageWidth(double stageWidth) {
        this.stageWidth.set(stageWidth);
    }

    public double getStageHeight() {
        return stageHeight.get();
    }

    public SimpleDoubleProperty stageHeightProperty() {
        return stageHeight;
    }

    public void setStageHeight(double stageHeight) {
        this.stageHeight.set(stageHeight);
    }

    public boolean ispKeyModel() {
        return pKeyModel.get();
    }

    public SimpleBooleanProperty pKeyModelProperty() {
        return pKeyModel;
    }

    public void setpKeyModel(boolean pKeyModel) {
        this.pKeyModel.set(pKeyModel);
    }

    public String getStartPassword() {

        return startPassword.get();
    }

    public SimpleStringProperty startPasswordProperty() {
        return startPassword;
    }

    public void setStartPassword(String startPassword) {
        this.startPassword.set(startPassword);
    }

    public int getGridListCount() {
        return gridListCount.get();
    }

    public SimpleIntegerProperty gridListCountProperty() {
        return gridListCount;
    }

    public void setGridListCount(int gridListCount) {
        this.gridListCount.set(gridListCount);
    }



    public int getCloseType() {
        return closeType.get();
    }

    public SimpleIntegerProperty closeTypeProperty() {
        return closeType;
    }

    public void setCloseType(int closeType) {
        this.closeType.set(closeType);
    }

    public int getDeleteFileModel() {
        return deleteFileModel.get();
    }

    public SimpleIntegerProperty deleteFileModelProperty() {
        return deleteFileModel;
    }

    public void setDeleteFileModel(int deleteFileModel) {
        this.deleteFileModel.set(deleteFileModel);
    }

    public boolean isMediaInnerInfoModel() {
        return mediaInnerInfoModel.get();
    }

    public SimpleBooleanProperty mediaInnerInfoModelProperty() {
        return mediaInnerInfoModel;
    }

    public void setMediaInnerInfoModel(boolean mediaInnerInfoModel) {
        this.mediaInnerInfoModel.set(mediaInnerInfoModel);
    }

    public boolean isTextSmooth() {
        return textSmooth.get();
    }

    public SimpleBooleanProperty textSmoothProperty() {
        return textSmooth;
    }

    public void setTextSmooth(boolean textSmooth) {
        this.textSmooth.set(textSmooth);
    }

    public boolean isGridItemDetailModel() {
        return gridItemDetailModel.get();
    }

    public SimpleBooleanProperty gridItemDetailModelProperty() {
        return gridItemDetailModel;
    }

    public void setGridItemDetailModel(boolean gridItemDetailModel) {
        this.gridItemDetailModel.set(gridItemDetailModel);
    }

    public int getGridColumns() {
        return resolveGridColumns().get();
    }

    /**
     * 按当前显示模式返回对应的列数属性。
     * <p>
     * 大图与小图分开存储，切换显示模式时各自保留自己的设置。
     */
    public SimpleIntegerProperty resolveGridColumns() {
        return gridItemDetailModel.get() ? gridColumnsBig : gridColumnsSmall;
    }

    public int getGridColumnsBig() {
        return gridColumnsBig.get();
    }

    public SimpleIntegerProperty gridColumnsBigProperty() {
        return gridColumnsBig;
    }

    public void setGridColumnsBig(int gridColumnsBig) {
        this.gridColumnsBig.set(gridColumnsBig);
    }

    public int getGridColumnsSmall() {
        return gridColumnsSmall.get();
    }

    public SimpleIntegerProperty gridColumnsSmallProperty() {
        return gridColumnsSmall;
    }

    public void setGridColumnsSmall(int gridColumnsSmall) {
        this.gridColumnsSmall.set(gridColumnsSmall);
    }

    public boolean isSideButtonPagingReversed() {
        return sideButtonPagingReversed.get();
    }

    public SimpleBooleanProperty sideButtonPagingReversedProperty() {
        return sideButtonPagingReversed;
    }

    public void setSideButtonPagingReversed(boolean sideButtonPagingReversed) {
        this.sideButtonPagingReversed.set(sideButtonPagingReversed);
    }

    public boolean isGridItemDetailModelMaxTitle() {
        return gridItemDetailModelMaxTitle.get();
    }

    public SimpleBooleanProperty gridItemDetailModelMaxTitleProperty() {
        return gridItemDetailModelMaxTitle;
    }

    public void setGridItemDetailModelMaxTitle(boolean gridItemDetailModelMaxTitle) {
        this.gridItemDetailModelMaxTitle.set(gridItemDetailModelMaxTitle);
    }

    public boolean isStopPlayOnEnd() {
        return stopPlayOnEnd.get();
    }

    public SimpleBooleanProperty stopPlayOnEndProperty() {
        return stopPlayOnEnd;
    }

    public void setStopPlayOnEnd(boolean stopPlayOnEnd) {
        this.stopPlayOnEnd.set(stopPlayOnEnd);
    }


    public boolean isDetailAlbumEffectModel() {
        return detailAlbumEffectModel.get();
    }

    public SimpleBooleanProperty detailAlbumEffectModelProperty() {
        return detailAlbumEffectModel;
    }

    public void setDetailAlbumEffectModel(boolean detailAlbumEffectModel) {
        this.detailAlbumEffectModel.set(detailAlbumEffectModel);
    }

    public double getDetailAlbumEffectSize() {
        return detailAlbumEffectSize.get();
    }

    public SimpleDoubleProperty detailAlbumEffectSizeProperty() {
        return detailAlbumEffectSize;
    }

    public void setDetailAlbumEffectSize(double detailAlbumEffectSize) {
        this.detailAlbumEffectSize.set(detailAlbumEffectSize);
    }

    public boolean isDetailAlbumRadiusModel() {
        return detailAlbumRadiusModel.get();
    }

    public SimpleBooleanProperty detailAlbumRadiusModelProperty() {
        return detailAlbumRadiusModel;
    }

    public void setDetailAlbumRadiusModel(boolean detailAlbumRadiusModel) {
        this.detailAlbumRadiusModel.set(detailAlbumRadiusModel);
    }

    public double getDetailAlbumRadiusSize() {
        return detailAlbumRadiusSize.get();
    }

    public SimpleDoubleProperty detailAlbumRadiusSizeProperty() {
        return detailAlbumRadiusSize;
    }

    public void setDetailAlbumRadiusSize(double detailAlbumRadiusSize) {
        this.detailAlbumRadiusSize.set(detailAlbumRadiusSize);
    }

    public boolean isDetailLrcAlignment() {
        return detailLrcAlignment.get();
    }

    public SimpleBooleanProperty detailLrcAlignmentProperty() {
        return detailLrcAlignment;
    }

    public void setDetailLrcAlignment(boolean detailLrcAlignment) {
        this.detailLrcAlignment.set(detailLrcAlignment);
    }

    public String getSubtitleScriptMode() {
        return subtitleScriptMode.get();
    }

    public SimpleStringProperty subtitleScriptModeProperty() {
        return subtitleScriptMode;
    }

    public void setSubtitleScriptMode(String subtitleScriptMode) {
        this.subtitleScriptMode.set(subtitleScriptMode);
    }

    public int getSubtitleDelayMs() {
        return subtitleDelayMs.get();
    }

    public SimpleIntegerProperty subtitleDelayMsProperty() {
        return subtitleDelayMs;
    }

    public void setSubtitleDelayMs(int subtitleDelayMs) {
        this.subtitleDelayMs.set(subtitleDelayMs);
    }

    public boolean isGalgameModel() {
        return galgameModel.get();
    }

    public SimpleBooleanProperty galgameModelProperty() {
        return galgameModel;
    }

    public void setGalgameModel(boolean galgameModel) {
        this.galgameModel.set(galgameModel);
    }

    public String getGalgameImage() {
        return galgameImage.get();
    }

    public SimpleStringProperty galgameImageProperty() {
        return galgameImage;
    }

    public void setGalgameImage(String galgameImage) {
        this.galgameImage.set(galgameImage);
    }

    public double getGalgameMessageOpacity() {
        return galgameMessageOpacity.get();
    }

    public SimpleDoubleProperty galgameMessageOpacityProperty() {
        return galgameMessageOpacity;
    }

    public void setGalgameMessageOpacity(double galgameMessageOpacity) {
        this.galgameMessageOpacity.set(galgameMessageOpacity);
    }

    public boolean isGalgameBgModel() {
        return galgameBgModel.get();
    }

    public SimpleBooleanProperty galgameBgModelProperty() {
        return galgameBgModel;
    }

    public void setGalgameBgModel(boolean galgameBgModel) {
        this.galgameBgModel.set(galgameBgModel);
    }

    public String getGalgameBgSizeWidth() {
        return galgameBgSizeWidth.get();
    }

    public SimpleStringProperty galgameBgSizeWidthProperty() {
        return galgameBgSizeWidth;
    }

    public void setGalgameBgSizeWidth(String galgameBgSizeWidth) {
        this.galgameBgSizeWidth.set(galgameBgSizeWidth);
    }

    public String getGalgameBgSizeHeight() {
        return galgameBgSizeHeight.get();
    }

    public SimpleStringProperty galgameBgSizeHeightProperty() {
        return galgameBgSizeHeight;
    }

    public void setGalgameBgSizeHeight(String galgameBgSizeHeight) {
        this.galgameBgSizeHeight.set(galgameBgSizeHeight);
    }

    public boolean isGalgameAnimationModel() {
        return galgameAnimationModel.get();
    }

    public SimpleBooleanProperty galgameAnimationModelProperty() {
        return galgameAnimationModel;
    }

    public void setGalgameAnimationModel(boolean galgameAnimationModel) {
        this.galgameAnimationModel.set(galgameAnimationModel);
    }

    public GalgameSkin getGalgameSkin() {
        return galgameSkin;
    }

    public void setGalgameSkin(GalgameSkin galgameSkin) {
        this.galgameSkin = GalgameSkinRegistry.normalize(galgameSkin);
    }

    public void normalizeGalgameSkin() {
        galgameSkin = GalgameSkinRegistry.normalize(galgameSkin);
    }

    public int getDesktopLRCStageWidth() {
        return desktopLRCStageWidth.get();
    }

    public SimpleIntegerProperty desktopLRCStageWidthProperty() {
        return desktopLRCStageWidth;
    }

    public void setDesktopLRCStageWidth(int desktopLRCStageWidth) {
        this.desktopLRCStageWidth.set(desktopLRCStageWidth);
    }

    public int getDesktopLRCFontSize() {
        return desktopLRCFontSize.get();
    }

    public SimpleIntegerProperty desktopLRCFontSizeProperty() {
        return desktopLRCFontSize;
    }

    public void setDesktopLRCFontSize(int desktopLRCFontSize) {
        this.desktopLRCFontSize.set(desktopLRCFontSize);
    }

    public String getDesktopLRCFontColor() {
        return desktopLRCFontColor.get();
    }

    public SimpleStringProperty desktopLRCFontColorProperty() {
        return desktopLRCFontColor;
    }

    public void setDesktopLRCFontColor(String desktopLRCFontColor) {
        this.desktopLRCFontColor.set(desktopLRCFontColor);
    }

    public boolean isDesktopLRCBorderModel() {
        return desktopLRCBorderModel.get();
    }

    public SimpleBooleanProperty desktopLRCBorderModelProperty() {
        return desktopLRCBorderModel;
    }

    public void setDesktopLRCBorderModel(boolean desktopLRCBorderModel) {
        this.desktopLRCBorderModel.set(desktopLRCBorderModel);
    }

    public String getDesktopLRCBorderColor() {
        return desktopLRCBorderColor.get();
    }

    public SimpleStringProperty desktopLRCBorderColorProperty() {
        return desktopLRCBorderColor;
    }

    public void setDesktopLRCBorderColor(String desktopLRCBorderColor) {
        this.desktopLRCBorderColor.set(desktopLRCBorderColor);
    }

    public boolean isDesktopLRCStrokeModel() {
        return desktopLRCStrokeModel.get();
    }

    public SimpleBooleanProperty desktopLRCStrokeModelProperty() {
        return desktopLRCStrokeModel;
    }

    public void setDesktopLRCStrokeModel(boolean desktopLRCStrokeModel) {
        this.desktopLRCStrokeModel.set(desktopLRCStrokeModel);
    }

    public String getDesktopLRCStrokeColor() {
        return desktopLRCStrokeColor.get();
    }

    public SimpleStringProperty desktopLRCStrokeColorProperty() {
        return desktopLRCStrokeColor;
    }

    public void setDesktopLRCStrokeColor(String desktopLRCStrokeColor) {
        this.desktopLRCStrokeColor.set(desktopLRCStrokeColor);
    }

    public boolean isDesktopLRCBoldModel() {
        return desktopLRCBoldModel.get();
    }

    public SimpleBooleanProperty desktopLRCBoldModelProperty() {
        return desktopLRCBoldModel;
    }

    public void setDesktopLRCBoldModel(boolean desktopLRCBoldModel) {
        this.desktopLRCBoldModel.set(desktopLRCBoldModel);
    }

    public boolean isCacheEnable() {
        return cacheEnable.get();
    }

    public SimpleBooleanProperty cacheEnableProperty() {
        return cacheEnable;
    }

    public void setCacheEnable(boolean cacheEnable) {
        this.cacheEnable.set(cacheEnable);
    }

    public int getCacheMaxSizeMB() {
        return cacheMaxSizeMB.get();
    }

    public SimpleIntegerProperty cacheMaxSizeMBProperty() {
        return cacheMaxSizeMB;
    }

    public void setCacheMaxSizeMB(int cacheMaxSizeMB) {
        this.cacheMaxSizeMB.set(cacheMaxSizeMB);
    }

    public boolean isProxyModel() {
        return proxyModel.get();
    }

    public SimpleBooleanProperty proxyModelProperty() {
        return proxyModel;
    }

    public void setProxyModel(boolean proxyModel) {
        this.proxyModel.set(proxyModel);
    }

    public String getProxyHost() {
        return proxyHost.get();
    }

    public SimpleStringProperty proxyHostProperty() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost.set(proxyHost);
    }

    public String getProxyPort() {
        return proxyPort.get();
    }

    public SimpleStringProperty proxyPortProperty() {
        return proxyPort;
    }

    public void setProxyPort(String proxyPort) {
        this.proxyPort.set(proxyPort);
    }

    public String getSourcesUrl() {
        return sourcesUrl.get();
    }

    public SimpleStringProperty sourcesUrlProperty() {
        return sourcesUrl;
    }

    public void setSourcesUrl(String sourcesUrl) {
        this.sourcesUrl.set(sourcesUrl);
    }

    public boolean isSyncImageReDownModel() {
        return syncImageReDownModel.get();
    }

    public SimpleBooleanProperty syncImageReDownModelProperty() {
        return syncImageReDownModel;
    }

    public void setSyncImageReDownModel(boolean syncImageReDownModel) {
        this.syncImageReDownModel.set(syncImageReDownModel);
    }

    public boolean isSaveCoverToAimFolderModel() {
        return saveCoverToAimFolderModel.get();
    }

    public SimpleBooleanProperty saveCoverToAimFolderModelProperty() {
        return saveCoverToAimFolderModel;
    }

    public void setSaveCoverToAimFolderModel(boolean saveCoverToAimFolderModel) {
        this.saveCoverToAimFolderModel.set(saveCoverToAimFolderModel);
    }

    public boolean isSyncSkipAlreadyModel() {
        return syncSkipAlreadyModel.get();
    }

    public SimpleBooleanProperty syncSkipAlreadyModelProperty() {
        return syncSkipAlreadyModel;
    }

    public void setSyncSkipAlreadyModel(boolean syncSkipAlreadyModel) {
        this.syncSkipAlreadyModel.set(syncSkipAlreadyModel);
    }

    public boolean isAsmrHistoryModel() {
        return asmrHistoryModel.get();
    }

    public SimpleBooleanProperty asmrHistoryModelProperty() {
        return asmrHistoryModel;
    }

    public void setAsmrHistoryModel(boolean asmrHistoryModel) {
        this.asmrHistoryModel.set(asmrHistoryModel);
    }

    public boolean isAsmrOnlineModel() {
        return asmrOnlineModel.get();
    }

    public SimpleBooleanProperty asmrOnlineModelProperty() {
        return asmrOnlineModel;
    }

    public void setAsmrOnlineModel(boolean asmrOnlineModel) {
        this.asmrOnlineModel.set(asmrOnlineModel);
    }

    public String getAsmrPath() {
        return asmrPath.get();
    }

    public SimpleStringProperty asmrPathProperty() {
        return asmrPath;
    }

    public void setAsmrPath(String asmrPath) {
        this.asmrPath.set(asmrPath);
    }

    public int getAsmrPlayingIndex() {
        return asmrPlayingIndex.get();
    }

    public SimpleIntegerProperty asmrPlayingIndexProperty() {
        return asmrPlayingIndex;
    }

    public void setAsmrPlayingIndex(int asmrPlayingIndex) {
        this.asmrPlayingIndex.set(asmrPlayingIndex);
    }

    public double getAsmrPlayingCurrentTime() {
        return asmrPlayingCurrentTime.get();
    }

    public SimpleDoubleProperty asmrPlayingCurrentTimeProperty() {
        return asmrPlayingCurrentTime;
    }

    public void setAsmrPlayingCurrentTime(double asmrPlayingCurrentTime) {
        this.asmrPlayingCurrentTime.set(asmrPlayingCurrentTime);
    }

    public boolean isTempNodesSlider() {
        return tempNodesSlider.get();
    }

    public SimpleBooleanProperty tempNodesSliderProperty() {
        return tempNodesSlider;
    }

    public void setTempNodesSlider(boolean tempNodesSlider) {
        this.tempNodesSlider.set(tempNodesSlider);
    }

    public double getVolume() {
        return volume.get();
    }

    public SimpleDoubleProperty volumeProperty() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume.set(volume);
    }

    public boolean isThemeModel() {
        return themeModel.get();
    }

    public SimpleBooleanProperty themeModelProperty() {
        return themeModel;
    }

    public void setThemeModel(boolean themeModel) {
        this.themeModel.set(themeModel);
    }


    public boolean isUserChineseTitle() {
        return userChineseTitle.get();
    }

    public SimpleBooleanProperty userChineseTitleProperty() {
        return userChineseTitle;
    }

    public void setUserChineseTitle(boolean userChineseTitle) {
        this.userChineseTitle.set(userChineseTitle);
    }

    public boolean isUserPlayingHistory() {
        return userPlayingHistory.get();
    }

    public SimpleBooleanProperty userPlayingHistoryProperty() {
        return userPlayingHistory;
    }

    public void setUserPlayingHistory(boolean userPlayingHistory) {
        this.userPlayingHistory.set(userPlayingHistory);
    }

    public boolean isExternalPlayerEnabled() {
        return externalPlayerEnabled.get();
    }

    public SimpleBooleanProperty externalPlayerEnabledProperty() {
        return externalPlayerEnabled;
    }

    public void setExternalPlayerEnabled(boolean externalPlayerEnabled) {
        this.externalPlayerEnabled.set(externalPlayerEnabled);
    }

    public String getExternalPlayerPath() {
        return externalPlayerPath.get();
    }

    public SimpleStringProperty externalPlayerPathProperty() {
        return externalPlayerPath;
    }

    public void setExternalPlayerPath(String externalPlayerPath) {
        this.externalPlayerPath.set(externalPlayerPath);
    }

    public String getExternalPlayerArgs() {
        return externalPlayerArgs.get();
    }

    public SimpleStringProperty externalPlayerArgsProperty() {
        return externalPlayerArgs;
    }

    public void setExternalPlayerArgs(String externalPlayerArgs) {
        this.externalPlayerArgs.set(externalPlayerArgs);
    }

    public int getAudioChannel() {
        return audioChannel.get();
    }

    public SimpleIntegerProperty audioChannelProperty() {
        return audioChannel;
    }

    public void setAudioChannel(int audioChannel) {
        this.audioChannel.set(audioChannel);
    }

    public int getAudioOutputDevice() {
        return audioOutputDevice.get();
    }

    public SimpleIntegerProperty audioOutputDeviceProperty() {
        return audioOutputDevice;
    }

    public void setAudioOutputDevice(int audioOutputDevice) {
        this.audioOutputDevice.set(audioOutputDevice);
    }

    public int getAudioEffect() {
        return audioEffect.get();
    }

    public SimpleIntegerProperty audioEffectProperty() {
        return audioEffect;
    }

    public void setAudioEffect(int audioEffect) {
        this.audioEffect.set(audioEffect);
    }
}
