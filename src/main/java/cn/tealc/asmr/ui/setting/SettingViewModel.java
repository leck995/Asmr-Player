package cn.tealc.asmr.ui.setting;

import cn.tealc.asmr.eventbus.EventBus;

import cn.tealc.asmr.model.MediaLibrary;
import cn.tealc.asmr.model.galgame.GalgameSkin;
import cn.tealc.asmr.model.galgame.GalgameSkinRegistry;
import cn.tealc.asmr.config.AiTranslationSettings;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.config.AppSettings;
import cn.tealc.asmr.service.CacheService;
import cn.tealc.asmr.service.ExternalPlayerService;
import cn.tealc.asmr.di.DI;

import cn.tealc.asmr.utils.SecureUtils;
import cn.tealc.asmr.mvvm.ViewModel;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.model.message.MessageType;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.paint.Color;
import javafx.util.StringConverter;

import java.util.Arrays;
import java.util.List;

/**
 * 设置页的视图模型：把 {@link AppSettings} 的各项配置包装成 JavaFX 属性并与之双向绑定，
 * 界面改动即时反映到内存配置，只有点击“保存”才由 {@link #save()} 落盘。
 * <p>
 * 启动密码与 AI 翻译 API Key 是“只写”字段：界面不回填已保存的值，留空表示保持不变，
 * 清除需要显式请求（{@link #clearAiTranslationApiKey()}），本模型负责区分这两种意图。
 * 缓存占用统计与清空在虚拟线程执行，结果回填后交给 {@code cacheInfo} 属性展示。
 * <p>
 * 少数遗留字段（{@code closeType}、{@code deleteFileModel}、{@code viewModel}）未参与绑定，
 * 仅保留读取入口；Galgame 皮肤不是 JavaFX 属性，读写直接走 {@link AppSettings#getGalgameSkin()}。
 *
 * @author Leck
 */
public class SettingViewModel implements ViewModel {
   private SimpleStringProperty ignoreVersion=new SimpleStringProperty();//用户选择跳过更新的版本
   private SimpleBooleanProperty autoCheckVersion=new SimpleBooleanProperty();//检查更新
   private SimpleDoubleProperty stageWidth=new SimpleDoubleProperty();
   private SimpleDoubleProperty stageHeight=new SimpleDoubleProperty();

   private SimpleBooleanProperty pKeyModel=new SimpleBooleanProperty();//启动密码开关；保存时若关闭会清除已存密码
   private SimpleStringProperty startPassword=new SimpleStringProperty();
   private final SimpleBooleanProperty startPasswordSet = new SimpleBooleanProperty();

   private SimpleIntegerProperty gridListCount=new SimpleIntegerProperty();//网格显示每页显示的数量
   private int viewModel=1; //视图切换，0是表格，1是Grid
   private int closeType=0; //关闭按钮功能，最小化和关闭
   private int deleteFileModel=0; //删除文件设置，0是直接删除，1是转移到回收站

   private SimpleBooleanProperty mediaInnerInfoModel=new SimpleBooleanProperty();//优先使用音声内置信息
   private SimpleBooleanProperty textSmooth=new SimpleBooleanProperty();//设置字体平滑，可以减少字体发虚现象

   private SimpleBooleanProperty gridItemDetailModel=new SimpleBooleanProperty();//设置详情大图模式

   private SimpleIntegerProperty gridColumnsBig=new SimpleIntegerProperty();//大图模式每行数量

   private SimpleIntegerProperty gridColumnsSmall=new SimpleIntegerProperty();//小图模式每行数量

   private SimpleBooleanProperty gridItemDetailModelMaxTitle=new SimpleBooleanProperty(); //详情大图模式的标题是否限制最大高度

   private SimpleBooleanProperty stopPlayOnEnd=new SimpleBooleanProperty();
   private SimpleBooleanProperty mainSkinModel=new SimpleBooleanProperty(); //皮肤开关，未与配置绑定，仅保留读取入口


   private SimpleBooleanProperty userPlayingHistory=new SimpleBooleanProperty();

   private SimpleBooleanProperty sideButtonPagingReversed=new SimpleBooleanProperty();
    /*===================================媒体库===================================*/
   private ObservableList<MediaLibrary> mediaLibraries= FXCollections.observableArrayList();//媒体库

    /*===================================播放界面相关===================================*/
   private SimpleBooleanProperty detailAlbumEffectModel=new SimpleBooleanProperty();//设置播放界面封面阴影，默认开启
   private SimpleDoubleProperty detailAlbumEffectSize=new SimpleDoubleProperty();//播放界面封面阴影大小
   private SimpleBooleanProperty detailAlbumRadiusModel=new SimpleBooleanProperty();//播放界面封面圆角
   private SimpleDoubleProperty detailAlbumRadiusSize=new SimpleDoubleProperty();//播放界面封面圆角大小
   private SimpleBooleanProperty detailLrcAlignment=new SimpleBooleanProperty();//设置播放界面歌词对其方式，默认左对齐false,true为居中
   private SimpleStringProperty subtitleScriptMode=new SimpleStringProperty();//字幕显示文字体系


    /*===================================Galgame模式相关===================================*/
   private SimpleBooleanProperty galgameModel=new SimpleBooleanProperty();//设置Galgame模式，默认开启
   private SimpleStringProperty galgameImage=new SimpleStringProperty();
   private SimpleDoubleProperty galgameMessageOpacity=new SimpleDoubleProperty();
   private SimpleBooleanProperty galgameBgModel=new SimpleBooleanProperty();//设置Galgame背景图片，默认true
   private SimpleStringProperty galgameBgSizeWidth=new SimpleStringProperty();
   private SimpleStringProperty galgameBgSizeHeight=new SimpleStringProperty();
   private SimpleBooleanProperty galgameAnimationModel=new SimpleBooleanProperty(true);//Galgame 动画效果开关，默认开启

   private GalgameSkin galgameSkin;

   /*===================================桌面歌词相关===================================*/
   private SimpleIntegerProperty desktopLRCStageWidth=new SimpleIntegerProperty();//桌面歌词窗口宽度
   private SimpleIntegerProperty desktopLRCFontSize=new SimpleIntegerProperty();//桌面歌词字体大小
   private SimpleStringProperty desktopLRCFontColor=new SimpleStringProperty();//桌面歌词字体颜色
   private SimpleBooleanProperty desktopLRCBorderModel=new SimpleBooleanProperty();//开启桌面歌词字体阴影（设置项名为 Border），默认开启
   private SimpleStringProperty desktopLRCBorderColor=new SimpleStringProperty();//桌面歌词字体阴影颜色，以 RRGGBBAA 十六进制文本保存

   private SimpleBooleanProperty desktopLRCStrokeModel=new SimpleBooleanProperty();//开启桌面歌词字体描边（Stroke），默认开启
   private SimpleStringProperty desktopLRCStrokeColor=new SimpleStringProperty();//桌面歌词字体描边颜色，以 RRGGBBAA 十六进制文本保存
   private SimpleBooleanProperty desktopLRCBoldModel=new SimpleBooleanProperty();//开启桌面歌词字体粗体,默认关闭
    /*==================================缓存相关=============================================*/
   private SimpleBooleanProperty cacheEnable=new SimpleBooleanProperty();//开启远程资源缓存
   private SimpleIntegerProperty cacheMaxSizeMB=new SimpleIntegerProperty();//缓存上限（MB）
   private SimpleStringProperty cacheInfo = new SimpleStringProperty();//缓存信息（大小、条目数），只读显示用



    /*==================================插件相关=============================================*/
   private SimpleBooleanProperty proxyModel=new SimpleBooleanProperty();//开启代理
   private SimpleStringProperty proxyHost=new SimpleStringProperty();//代理地址
   private SimpleStringProperty proxyPort=new SimpleStringProperty();//代理端口
   private SimpleStringProperty sourcesUrl=new SimpleStringProperty();  //同步网址
   private SimpleBooleanProperty syncImageReDownModel=new SimpleBooleanProperty(); //同步时重新下载封面

   private SimpleBooleanProperty saveCoverToAimFolderModel=new SimpleBooleanProperty(); //保存封面到目标文件夹

   private SimpleBooleanProperty syncSkipAlreadyModel=new SimpleBooleanProperty();//跳过已经同步的音声

   private SimpleBooleanProperty userChineseTitle= new SimpleBooleanProperty();

    /*==================================外部播放器相关=============================================*/
   private SimpleBooleanProperty externalPlayerEnabled=new SimpleBooleanProperty();//启用外部播放器播放视频
   private SimpleStringProperty externalPlayerPath=new SimpleStringProperty();//外部播放器路径
   private SimpleStringProperty externalPlayerArgs=new SimpleStringProperty();//启动参数模板
   private SimpleStringProperty externalPlayerKind=new SimpleStringProperty();//识别出的播放器类型，仅用于界面展示

    /*==================================退出时记录播放进度相关=============================================*/
   private SimpleBooleanProperty asmrHistoryModel=new SimpleBooleanProperty();//开启退出时记录播放
   private SimpleBooleanProperty asmrOnlineModel=new SimpleBooleanProperty();//开启退出时记录播放类型
   private SimpleStringProperty asmrPath=new SimpleStringProperty();//正在播放的该音声媒体
   private SimpleIntegerProperty asmrPlayingIndex=new SimpleIntegerProperty();//正在播放的歌曲索引
   private SimpleDoubleProperty asmrPlayingCurrentTime=new SimpleDoubleProperty();//正在播放的歌曲时间


   private SimpleStringProperty textBlacklistRow = new SimpleStringProperty();

   /*==================================AI 翻译相关=============================================*/
   private SimpleStringProperty aiTranslationEndpoint = new SimpleStringProperty();
   private SimpleStringProperty aiTranslationModel = new SimpleStringProperty();
   private SimpleStringProperty aiTranslationApiKey = new SimpleStringProperty();
   private final SimpleBooleanProperty aiTranslationApiKeySet = new SimpleBooleanProperty();
   private boolean clearAiTranslationApiKeyRequested;
   private SimpleStringProperty aiTranslationPromptPreset = new SimpleStringProperty();
   private SimpleStringProperty aiTranslationPrompt = new SimpleStringProperty();
   private SimpleIntegerProperty aiTranslationBatchSize = new SimpleIntegerProperty();
   private SimpleIntegerProperty aiTranslationMaxConcurrency = new SimpleIntegerProperty();
   private SimpleIntegerProperty aiTranslationTimeoutSeconds = new SimpleIntegerProperty();

    public SettingViewModel() {
       autoCheckVersion.bindBidirectional(Config.setting.autoCheckVersion);
       pKeyModel.bindBidirectional(Config.setting.pKeyModel);






       mediaInnerInfoModel.bindBidirectional(Config.setting.mediaInnerInfoModel);
       textSmooth.bindBidirectional(Config.setting.textSmooth);
       userPlayingHistory.bindBidirectional(Config.setting.userPlayingHistory);
       sideButtonPagingReversed.bindBidirectional(Config.setting.sideButtonPagingReversed);

       gridListCount.bindBidirectional(Config.setting.gridListCount);
       gridItemDetailModel.bindBidirectional(Config.setting.gridItemDetailModel);
       gridColumnsBig.bindBidirectional(Config.setting.gridColumnsBig);
       gridColumnsSmall.bindBidirectional(Config.setting.gridColumnsSmall);

       detailAlbumEffectModel.bindBidirectional(Config.setting.detailAlbumEffectModel);
       detailAlbumEffectSize.bindBidirectional(Config.setting.detailAlbumEffectSize);
       detailAlbumRadiusModel.bindBidirectional(Config.setting.detailAlbumRadiusModel);
       detailAlbumRadiusSize.bindBidirectional(Config.setting.detailAlbumRadiusSize);
       detailLrcAlignment.bindBidirectional(Config.setting.detailLrcAlignment);
       subtitleScriptMode.bindBidirectional(Config.setting.subtitleScriptMode);

       galgameModel.bindBidirectional(Config.setting.galgameModel);
       galgameImage.bindBidirectional(Config.setting.galgameImage);
       galgameBgModel.bindBidirectional(Config.setting.galgameBgModel);
       galgameBgSizeWidth.bindBidirectional(Config.setting.galgameBgSizeWidth);
       galgameBgSizeHeight.bindBidirectional(Config.setting.galgameBgSizeHeight);
       galgameAnimationModel.bindBidirectional(Config.setting.galgameAnimationModel);

       galgameSkin=Config.setting.galgameSkin;

       desktopLRCStageWidth.bindBidirectional(Config.setting.desktopLRCStageWidth);
       desktopLRCFontSize.bindBidirectional(Config.setting.desktopLRCFontSize);
       desktopLRCFontColor.bindBidirectional(Config.setting.desktopLRCFontColor);
       desktopLRCBorderModel.bindBidirectional(Config.setting.desktopLRCBorderModel);
       desktopLRCBorderColor.bindBidirectional(Config.setting.desktopLRCBorderColor);
       desktopLRCStrokeModel.bindBidirectional(Config.setting.desktopLRCStrokeModel);
       desktopLRCStrokeColor.bindBidirectional(Config.setting.desktopLRCStrokeColor);
       desktopLRCBoldModel.bindBidirectional(Config.setting.desktopLRCBoldModel);



       cacheEnable.bindBidirectional(Config.setting.cacheEnable);
       cacheMaxSizeMB.bindBidirectional(Config.setting.cacheMaxSizeMB);
       refreshCacheInfoAsync();

       proxyModel.bindBidirectional(Config.setting.proxyModel);
       proxyHost.bindBidirectional(Config.setting.proxyHost);
       proxyPort.bindBidirectional(Config.setting.proxyPort);
       sourcesUrl.bindBidirectional(Config.setting.sourcesUrl);
       syncImageReDownModel.bindBidirectional(Config.setting.syncImageReDownModel);
       saveCoverToAimFolderModel.bindBidirectional(Config.setting.saveCoverToAimFolderModel);
       syncSkipAlreadyModel.bindBidirectional(Config.setting.syncSkipAlreadyModel);
       userChineseTitle.bindBidirectional(Config.setting.userChineseTitle);

       externalPlayerEnabled.bindBidirectional(Config.setting.externalPlayerEnabled);
       externalPlayerPath.bindBidirectional(Config.setting.externalPlayerPath);
       externalPlayerArgs.bindBidirectional(Config.setting.externalPlayerArgs);
       externalPlayerPath.addListener((observable, oldValue, newValue) -> refreshExternalPlayerKind());
       refreshExternalPlayerKind();


       asmrHistoryModel.bindBidirectional(Config.setting.asmrHistoryModel);
       asmrOnlineModel.bindBidirectional(Config.setting.asmrOnlineModel);
       asmrPath.bindBidirectional(Config.setting.asmrPath);
       asmrPlayingIndex.bindBidirectional(Config.setting.asmrPlayingIndex);
       asmrPlayingCurrentTime.bindBidirectional(Config.setting.asmrPlayingCurrentTime);



       startPasswordSet.set(Config.setting.getStartPassword() != null
               && !Config.setting.getStartPassword().isEmpty());


       String blacklistRow = String.join("\n", Config.textBlacklist);
       textBlacklistRow.set(blacklistRow);

       aiTranslationEndpoint.set(Config.setting.aiTranslation.getEndpoint());
       aiTranslationModel.set(Config.setting.aiTranslation.getModel());
       aiTranslationApiKeySet.set(Config.setting.aiTranslation.getApiKey() != null
               && !Config.setting.aiTranslation.getApiKey().isBlank());
       aiTranslationPromptPreset.set(Config.setting.aiTranslation.getPromptPreset());
       aiTranslationPrompt.set(Config.setting.aiTranslation.getPrompt());
       aiTranslationBatchSize.set(Config.setting.aiTranslation.getBatchSize());
       aiTranslationMaxConcurrency.set(Config.setting.aiTranslation.getMaxConcurrency());
       aiTranslationTimeoutSeconds.set(Config.setting.aiTranslation.getTimeoutSeconds());

    }


    /**
     * 通知主界面按新的显示模式重建作品网格，切换每页数量或大图/小图后调用。
     */
    public void changeGridItemModel(){
       EventBus.publish(NotificationKey.MAIN_GRID_REFRESH);
    }


    /**
     * 写入桌面歌词的字体颜色：直接落到配置项，双向绑定会让本类的对应属性同步更新。
     *
     * @param value 颜色选择器的取值
     */
    public void updateLrcFontColor(Color value){
       Config.setting.desktopLRCFontColor.set(colorToString(value));
    }


    /**
     * 写入桌面歌词的阴影颜色。
     * <p>
     * 配置项名为 Border，但桌面歌词把它用作 DropShadow 效果的着色，实际呈现是外发光阴影
     * 而非描边；真正的描边由 {@link #updateLrcStrokeColor(Color)} 负责。
     *
     * @param value 颜色选择器的取值
     */
   public void updateLrcBorderColor(Color value){
      Config.setting.desktopLRCBorderColor.set(colorToString(value));
   }

   /**
    * 写入桌面歌词的字体描边颜色。
    * <p>
    * 对应 JavaFX Text 的 stroke 属性，与 {@link #updateLrcBorderColor(Color)} 的阴影效果互不相干，
    * 两者分别由 {@code desktopLRCStrokeModel} 和 {@code desktopLRCBorderModel} 两个开关控制。
    *
    * @param value 颜色选择器的取值
    */
   public void updateLrcStrokeColor(Color value){
      Config.setting.desktopLRCStrokeColor.set(colorToString(value));
   }


   /**
    * 把 JavaFX 颜色序列化成配置里保存的 web color 字符串。
    *
    * @param value 待转换的颜色
    * @return 8 位十六进制 {@code RRGGBBAA}，透明度取颜色的 opacity 分量；不带前导 {@code #}，
    *         而配置项的默认值是带 {@code #} 的写法，读取时依赖 {@code Color.web} 兼容两种形式
    */
   private String colorToString(Color value){
      int var1 = (int)Math.round(value.getRed() * 255.0);
      int var2 = (int)Math.round(value.getGreen() * 255.0);
      int var3 = (int)Math.round(value.getBlue() * 255.0);
      int var4 = (int)Math.round(value.getOpacity() * 255.0);
      return String.format("%02x%02x%02x%02x", var1, var2, var3, var4);
   }

   /**
    * 列出可供选择的 Galgame 皮肤。
    *
    * @return 取自 {@link GalgameSkinRegistry} 的皮肤列表；每次调用都新建一个可观察列表，
    *         因此不要把它当作稳定的模型来源长期持有
    */
   public ObservableList<GalgameSkin> getGalgameSkins(){
      return FXCollections.observableArrayList(GalgameSkinRegistry.all());
   }

   /**
    * 切换当前 Galgame 皮肤。
    * <p>
    * 同时更新本模型的普通字段与 {@code Config.setting}：皮肤不是 JavaFX 属性，
    * 界面取值统一通过 {@link #getGalgameSkin()} 读取。
    *
    * @param galgameSkin 选中的皮肤
    */
   public void updateGalgameSkin(GalgameSkin galgameSkin){
      this.galgameSkin = galgameSkin;
      Config.setting.setGalgameSkin(galgameSkin);
   }


   /**
    * 把设置页的文本内容写入字幕黑名单。
    * <p>
    * 直接落库并同步内存缓存，失败时提示用户，界面上的文本保持不变以便重试。
    */
   public void updateTextBlacklist(){
      String row = textBlacklistRow.get();
      List<String> rows = (row == null || row.isEmpty())
              ? List.of()
              : Arrays.asList(row.split("\n"));
      try {
         Config.updateTextBlacklist(rows);
      } catch (RuntimeException e) {
         EventBus.publish(NotificationKey.MESSAGE,
                 new MessageInfo(MessageType.WARNING, "保存字幕黑名单失败：" + e.getMessage()));
      }
   }



   /**
    * 将设置页可编辑的选项恢复为默认值并立即保存。
    */
   public void resetToDefaults(){
      AppSettings defaults = new AppSettings();

      autoCheckVersion.set(defaults.autoCheckVersion.get());
      pKeyModel.set(defaults.pKeyModel.get());
      startPassword.set(null);
      Config.setting.startPassword.set(null);
      startPasswordSet.set(false);
      mediaInnerInfoModel.set(defaults.mediaInnerInfoModel.get());
      textSmooth.set(defaults.textSmooth.get());
      userPlayingHistory.set(defaults.userPlayingHistory.get());
      sideButtonPagingReversed.set(defaults.sideButtonPagingReversed.get());

      gridListCount.set(defaults.gridListCount.get());
      gridItemDetailModel.set(defaults.gridItemDetailModel.get());
      gridColumnsBig.set(defaults.gridColumnsBig.get());
      gridColumnsSmall.set(defaults.gridColumnsSmall.get());

      detailAlbumEffectModel.set(defaults.detailAlbumEffectModel.get());
      detailAlbumEffectSize.set(defaults.detailAlbumEffectSize.get());
      detailAlbumRadiusModel.set(defaults.detailAlbumRadiusModel.get());
      detailAlbumRadiusSize.set(defaults.detailAlbumRadiusSize.get());
      detailLrcAlignment.set(defaults.detailLrcAlignment.get());
      subtitleScriptMode.set(defaults.subtitleScriptMode.get());

      galgameModel.set(defaults.galgameModel.get());
      galgameImage.set(defaults.galgameImage.get());
      galgameBgModel.set(defaults.galgameBgModel.get());
      galgameBgSizeWidth.set(defaults.galgameBgSizeWidth.get());
      galgameBgSizeHeight.set(defaults.galgameBgSizeHeight.get());
      galgameAnimationModel.set(defaults.galgameAnimationModel.get());
      updateGalgameSkin(GalgameSkinRegistry.defaultSkin());

      desktopLRCFontSize.set(defaults.desktopLRCFontSize.get());
      desktopLRCFontColor.set(defaults.desktopLRCFontColor.get());
      desktopLRCBorderModel.set(defaults.desktopLRCBorderModel.get());
      desktopLRCBorderColor.set(defaults.desktopLRCBorderColor.get());
      desktopLRCStrokeModel.set(defaults.desktopLRCStrokeModel.get());
      desktopLRCStrokeColor.set(defaults.desktopLRCStrokeColor.get());
      desktopLRCBoldModel.set(defaults.desktopLRCBoldModel.get());

      cacheEnable.set(defaults.cacheEnable.get());
      cacheMaxSizeMB.set(defaults.cacheMaxSizeMB.get());

      proxyModel.set(defaults.proxyModel.get());
      proxyHost.set(defaults.proxyHost.get());
      proxyPort.set(defaults.proxyPort.get());
      sourcesUrl.set(defaults.sourcesUrl.get());
      syncImageReDownModel.set(defaults.syncImageReDownModel.get());
      saveCoverToAimFolderModel.set(defaults.saveCoverToAimFolderModel.get());
      syncSkipAlreadyModel.set(defaults.syncSkipAlreadyModel.get());
      userChineseTitle.set(defaults.userChineseTitle.get());

      externalPlayerEnabled.set(defaults.externalPlayerEnabled.get());
      externalPlayerPath.set(defaults.externalPlayerPath.get());
      externalPlayerArgs.set(defaults.externalPlayerArgs.get());

      // 经统一入口清空，确保数据库与内存缓存同步，避免重启后旧内容复活
      textBlacklistRow.set("");
      Config.updateTextBlacklist(List.of());

      AiTranslationSettings ai = new AiTranslationSettings();
      aiTranslationEndpoint.set(ai.getEndpoint());
      aiTranslationModel.set(ai.getModel());
      aiTranslationApiKey.set(ai.getApiKey());
      aiTranslationApiKeySet.set(false);
      clearAiTranslationApiKeyRequested = true;
      aiTranslationPromptPreset.set(ai.getPromptPreset());
      aiTranslationPrompt.set(ai.getPrompt());
      aiTranslationBatchSize.set(ai.getBatchSize());
      aiTranslationMaxConcurrency.set(ai.getMaxConcurrency());
      aiTranslationTimeoutSeconds.set(ai.getTimeoutSeconds());

      save();
   }

   public void save(){
      // 密码框只承载新密码：留空表示保持已存密码，关闭开关时清除
      String newPassword = getStartPassword();
      if (newPassword != null && !newPassword.isBlank()) {
         Config.setting.setStartPassword(SecureUtils.md5(newPassword));
         startPasswordSet.set(true);
         startPassword.set(null);
      } else if (!pKeyModel.get()) {
         Config.setting.setStartPassword(null);
         startPasswordSet.set(false);
      }
      updateTextBlacklist();
      Config.setting.aiTranslation.setEndpoint(aiTranslationEndpoint.get());
      Config.setting.aiTranslation.setModel(aiTranslationModel.get());
      String newApiKey = aiTranslationApiKey.get();
      if (newApiKey != null && !newApiKey.isBlank()) {
         Config.setting.aiTranslation.setApiKey(newApiKey.trim());
         aiTranslationApiKeySet.set(true);
         clearAiTranslationApiKeyRequested = false;
         aiTranslationApiKey.set(null);
      } else if (clearAiTranslationApiKeyRequested) {
         Config.setting.aiTranslation.setApiKey("");
         aiTranslationApiKeySet.set(false);
         clearAiTranslationApiKeyRequested = false;
      }
      Config.setting.aiTranslation.setPromptPreset(aiTranslationPromptPreset.get());
      Config.setting.aiTranslation.setPrompt(aiTranslationPrompt.get());
      Config.setting.aiTranslation.setBatchSize(aiTranslationBatchSize.get());
      Config.setting.aiTranslation.setMaxConcurrency(aiTranslationMaxConcurrency.get());
      Config.setting.aiTranslation.setTimeoutSeconds(aiTranslationTimeoutSeconds.get());
      Config.saveProperties(false);

   }

   /**
    * 后台刷新缓存信息（当前大小 + 条目数），更新到 cacheInfo 属性供 UI 显示。
    */
   public void refreshCacheInfoAsync(){
      Thread.ofVirtual().start(() -> {
         long bytes = DI.get(CacheService.class).getCacheSize();
         String text = formatSize(bytes);
         Platform.runLater(() -> cacheInfo.set(text));
      });
   }

   /**
    * 根据当前播放器路径刷新识别结果，供设置页展示，便于用户确认参数拼装是否正确。
    */
   public void refreshExternalPlayerKind(){
      String path = externalPlayerPath.get();
      if (path == null || path.isBlank()){
         externalPlayerKind.set("未选择播放器");
         return;
      }
      if (!new java.io.File(path).isFile()){
         externalPlayerKind.set("路径无效");
         return;
      }
      externalPlayerKind.set("已识别：" + ExternalPlayerService.detectPlayerKind(path).getDisplayName());
   }

   /**
    * 清空全部缓存，并在完成后刷新缓存信息显示。
    */
   public void clearCache(){
      Thread.ofVirtual().start(() -> {
         CacheService cacheService = DI.get(CacheService.class);
         cacheService.clearAll();
         long bytes = cacheService.getCacheSize();
         Platform.runLater(() -> {
            cacheInfo.set(formatSize(bytes));
            EventBus.publish(NotificationKey.MESSAGE,
                  new MessageInfo(MessageType.SUCCESS,"缓存已清空"));
         });
      });
   }

   /**
    * 将字节数格式化为人类可读的大小字符串。
    *
    * @param bytes 字节数
    * @return 如 "12.3 MB" 或 "456 KB"
    */
   private String formatSize(long bytes){
      if (bytes < 1024) return bytes + " B";
      if (bytes < 1024L * 1024L) return String.format("%.1f KB", bytes / 1024.0);
      if (bytes < 1024L * 1024L * 1024L) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
      return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
   }




   public String getIgnoreVersion() {
      return ignoreVersion.get();
   }

   public SimpleStringProperty ignoreVersionProperty() {
      return ignoreVersion;
   }

   public boolean isAutoCheckVersion() {
      return autoCheckVersion.get();
   }

   public SimpleBooleanProperty autoCheckVersionProperty() {
      return autoCheckVersion;
   }

   public double getStageWidth() {
      return stageWidth.get();
   }

   public SimpleDoubleProperty stageWidthProperty() {
      return stageWidth;
   }

   public double getStageHeight() {
      return stageHeight.get();
   }

   public SimpleDoubleProperty stageHeightProperty() {
      return stageHeight;
   }

   public boolean ispKeyModel() {
      return pKeyModel.get();
   }

   public SimpleBooleanProperty pKeyModelProperty() {
      return pKeyModel;
   }

   public String getStartPassword() {
      return startPassword.get();
   }

   public boolean hasStartPassword() {
      return startPasswordSet.get();
   }

   public SimpleBooleanProperty startPasswordSetProperty() {
      return startPasswordSet;
   }

   public SimpleStringProperty startPasswordProperty() {
      return startPassword;
   }

   public int getGridListCount() {
      return gridListCount.get();
   }

   public SimpleIntegerProperty gridListCountProperty() {
      return gridListCount;
   }

   public int getViewModel() {
      return viewModel;
   }

   public int getCloseType() {
      return closeType;
   }

   public int getDeleteFileModel() {
      return deleteFileModel;
   }

   public boolean isMediaInnerInfoModel() {
      return mediaInnerInfoModel.get();
   }

   public SimpleBooleanProperty mediaInnerInfoModelProperty() {
      return mediaInnerInfoModel;
   }

   public boolean isTextSmooth() {
      return textSmooth.get();
   }

   public SimpleBooleanProperty textSmoothProperty() {
      return textSmooth;
   }

   public boolean isGridItemDetailModel() {
      return gridItemDetailModel.get();
   }

   public SimpleBooleanProperty gridItemDetailModelProperty() {
      return gridItemDetailModel;
   }

   public int getGridColumnsBig() {
      return gridColumnsBig.get();
   }

   public SimpleIntegerProperty gridColumnsBigProperty() {
      return gridColumnsBig;
   }

   public int getGridColumnsSmall() {
      return gridColumnsSmall.get();
   }

   public SimpleIntegerProperty gridColumnsSmallProperty() {
      return gridColumnsSmall;
   }

   public boolean isGridItemDetailModelMaxTitle() {
      return gridItemDetailModelMaxTitle.get();
   }

   public SimpleBooleanProperty gridItemDetailModelMaxTitleProperty() {
      return gridItemDetailModelMaxTitle;
   }

   public boolean isStopPlayOnEnd() {
      return stopPlayOnEnd.get();
   }

   public SimpleBooleanProperty stopPlayOnEndProperty() {
      return stopPlayOnEnd;
   }

   public boolean isMainSkinModel() {
      return mainSkinModel.get();
   }

   public SimpleBooleanProperty mainSkinModelProperty() {
      return mainSkinModel;
   }

   public ObservableList<MediaLibrary> getMediaLibraries() {
      return mediaLibraries;
   }

   public boolean isDetailAlbumEffectModel() {
      return detailAlbumEffectModel.get();
   }

   public SimpleBooleanProperty detailAlbumEffectModelProperty() {
      return detailAlbumEffectModel;
   }

   public double getDetailAlbumEffectSize() {
      return detailAlbumEffectSize.get();
   }

   public SimpleDoubleProperty detailAlbumEffectSizeProperty() {
      return detailAlbumEffectSize;
   }

   public boolean isDetailAlbumRadiusModel() {
      return detailAlbumRadiusModel.get();
   }

   public SimpleBooleanProperty detailAlbumRadiusModelProperty() {
      return detailAlbumRadiusModel;
   }

   public double getDetailAlbumRadiusSize() {
      return detailAlbumRadiusSize.get();
   }

   public SimpleDoubleProperty detailAlbumRadiusSizeProperty() {
      return detailAlbumRadiusSize;
   }

   public boolean isDetailLrcAlignment() {
      return detailLrcAlignment.get();
   }

   public SimpleBooleanProperty detailLrcAlignmentProperty() {
      return detailLrcAlignment;
   }

   public SimpleStringProperty subtitleScriptModeProperty() {
      return subtitleScriptMode;
   }

   public boolean isGalgameModel() {
      return galgameModel.get();
   }

   public SimpleBooleanProperty galgameModelProperty() {
      return galgameModel;
   }

   public String getGalgameImage() {
      return galgameImage.get();
   }

   public SimpleStringProperty galgameImageProperty() {
      return galgameImage;
   }

   public double getGalgameMessageOpacity() {
      return galgameMessageOpacity.get();
   }

   public SimpleDoubleProperty galgameMessageOpacityProperty() {
      return galgameMessageOpacity;
   }

   public boolean isGalgameBgModel() {
      return galgameBgModel.get();
   }

   public SimpleBooleanProperty galgameBgModelProperty() {
      return galgameBgModel;
   }

   public String getGalgameBgSizeWidth() {
      return galgameBgSizeWidth.get();
   }

   public SimpleStringProperty galgameBgSizeWidthProperty() {
      return galgameBgSizeWidth;
   }

   public String getGalgameBgSizeHeight() {
      return galgameBgSizeHeight.get();
   }

   public SimpleStringProperty galgameBgSizeHeightProperty() {
      return galgameBgSizeHeight;
   }

   public boolean isGalgameAnimationModel() {
      return galgameAnimationModel.get();
   }

   public SimpleBooleanProperty galgameAnimationModelProperty() {
      return galgameAnimationModel;
   }

   public GalgameSkin getGalgameSkin() {
      return galgameSkin;
   }

   public int getDesktopLRCStageWidth() {
      return desktopLRCStageWidth.get();
   }

   public SimpleIntegerProperty desktopLRCStageWidthProperty() {
      return desktopLRCStageWidth;
   }

   public int getDesktopLRCFontSize() {
      return desktopLRCFontSize.get();
   }

   public SimpleIntegerProperty desktopLRCFontSizeProperty() {
      return desktopLRCFontSize;
   }

   public String getDesktopLRCFontColor() {
      return desktopLRCFontColor.get();
   }

   public SimpleStringProperty desktopLRCFontColorProperty() {
      return desktopLRCFontColor;
   }

   public boolean isDesktopLRCBorderModel() {
      return desktopLRCBorderModel.get();
   }

   public SimpleBooleanProperty desktopLRCBorderModelProperty() {
      return desktopLRCBorderModel;
   }

   public String getDesktopLRCBorderColor() {
      return desktopLRCBorderColor.get();
   }

   public SimpleStringProperty desktopLRCBorderColorProperty() {
      return desktopLRCBorderColor;
   }

   public boolean isDesktopLRCStrokeModel() {
      return desktopLRCStrokeModel.get();
   }

   public SimpleBooleanProperty desktopLRCStrokeModelProperty() {
      return desktopLRCStrokeModel;
   }

   public String getDesktopLRCStrokeColor() {
      return desktopLRCStrokeColor.get();
   }

   public SimpleStringProperty desktopLRCStrokeColorProperty() {
      return desktopLRCStrokeColor;
   }

   public boolean isDesktopLRCBoldModel() {
      return desktopLRCBoldModel.get();
   }

   public SimpleBooleanProperty desktopLRCBoldModelProperty() {
      return desktopLRCBoldModel;
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

   public String getCacheInfo() {
      return cacheInfo.get();
   }

   public SimpleStringProperty cacheInfoProperty() {
      return cacheInfo;
   }

   public boolean isProxyModel() {
      return proxyModel.get();
   }

   public SimpleBooleanProperty proxyModelProperty() {
      return proxyModel;
   }

   public String getProxyHost() {
      return proxyHost.get();
   }

   public SimpleStringProperty proxyHostProperty() {
      return proxyHost;
   }

   public String getProxyPort() {
      return proxyPort.get();
   }

   public SimpleStringProperty proxyPortProperty() {
      return proxyPort;
   }

   public String getSourcesUrl() {
      return sourcesUrl.get();
   }

   public SimpleStringProperty sourcesUrlProperty() {
      return sourcesUrl;
   }

   public boolean isSyncImageReDownModel() {
      return syncImageReDownModel.get();
   }

   public SimpleBooleanProperty syncImageReDownModelProperty() {
      return syncImageReDownModel;
   }

   public boolean isSaveCoverToAimFolderModel() {
      return saveCoverToAimFolderModel.get();
   }

   public SimpleBooleanProperty saveCoverToAimFolderModelProperty() {
      return saveCoverToAimFolderModel;
   }

   public boolean isSyncSkipAlreadyModel() {
      return syncSkipAlreadyModel.get();
   }

   public SimpleBooleanProperty syncSkipAlreadyModelProperty() {
      return syncSkipAlreadyModel;
   }

   public boolean isAsmrHistoryModel() {
      return asmrHistoryModel.get();
   }

   public SimpleBooleanProperty asmrHistoryModelProperty() {
      return asmrHistoryModel;
   }

   public boolean isAsmrOnlineModel() {
      return asmrOnlineModel.get();
   }

   public SimpleBooleanProperty asmrOnlineModelProperty() {
      return asmrOnlineModel;
   }

   public String getAsmrPath() {
      return asmrPath.get();
   }

   public SimpleStringProperty asmrPathProperty() {
      return asmrPath;
   }

   public int getAsmrPlayingIndex() {
      return asmrPlayingIndex.get();
   }

   public SimpleIntegerProperty asmrPlayingIndexProperty() {
      return asmrPlayingIndex;
   }

   public double getAsmrPlayingCurrentTime() {
      return asmrPlayingCurrentTime.get();
   }

   public SimpleDoubleProperty asmrPlayingCurrentTimeProperty() {
      return asmrPlayingCurrentTime;
   }

   public void setSaveCoverToAimFolderModel(boolean saveCoverToAimFolderModel) {
      this.saveCoverToAimFolderModel.set(saveCoverToAimFolderModel);
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

   public boolean isExternalPlayerEnabled() {
      return externalPlayerEnabled.get();
   }

   public SimpleBooleanProperty externalPlayerEnabledProperty() {
      return externalPlayerEnabled;
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

   public StringProperty externalPlayerKindProperty() {
      return externalPlayerKind;
   }

   public boolean isUserPlayingHistory() {
      return userPlayingHistory.get();
   }

   public SimpleBooleanProperty userPlayingHistoryProperty() {
      return userPlayingHistory;
   }

   public boolean isSideButtonPagingReversed() {
      return sideButtonPagingReversed.get();
   }

   public SimpleBooleanProperty sideButtonPagingReversedProperty() {
      return sideButtonPagingReversed;
   }

   public String getTextBlacklistRow() {
      return textBlacklistRow.get();
   }

   public SimpleStringProperty textBlacklistRowProperty() {
      return textBlacklistRow;
   }

   public SimpleStringProperty aiTranslationEndpointProperty() {
      return aiTranslationEndpoint;
   }

   public SimpleStringProperty aiTranslationModelProperty() {
      return aiTranslationModel;
   }

   public SimpleStringProperty aiTranslationApiKeyProperty() {
      return aiTranslationApiKey;
   }

   public SimpleBooleanProperty aiTranslationApiKeySetProperty() {
      return aiTranslationApiKeySet;
   }

   /**
    * 标记清除已保存的 API Key；点击“保存”后才会真正写入配置。
    */
   public void clearAiTranslationApiKey() {
      clearAiTranslationApiKeyRequested = true;
      aiTranslationApiKey.set(null);
   }

   public SimpleStringProperty aiTranslationPromptPresetProperty() {
      return aiTranslationPromptPreset;
   }

   public SimpleStringProperty aiTranslationPromptProperty() {
      return aiTranslationPrompt;
   }

   public SimpleIntegerProperty aiTranslationBatchSizeProperty() {
      return aiTranslationBatchSize;
   }

   public SimpleIntegerProperty aiTranslationMaxConcurrencyProperty() {
      return aiTranslationMaxConcurrency;
   }

   public SimpleIntegerProperty aiTranslationTimeoutSecondsProperty() {
      return aiTranslationTimeoutSeconds;
   }
}
