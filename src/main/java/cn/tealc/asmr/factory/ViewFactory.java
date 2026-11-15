package cn.tealc.asmr.factory;

import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.ui.galgame.GalgameLoadView;
import cn.tealc.asmr.ui.galgame.GalgameLoadViewModel;
import cn.tealc.asmr.ui.work.*;
import cn.tealc.asmr.ui.dialog.*;
import cn.tealc.asmr.ui.viewer.*;
import cn.tealc.asmr.ui.library.MediaLibraryView;
import cn.tealc.asmr.ui.library.MediaLibraryViewModel;
import cn.tealc.asmr.ui.player.PlayerDetailView;
import cn.tealc.asmr.ui.player.PlayerDetailViewModel;
import cn.tealc.asmr.ui.tools.SubtitleRenameView;
import cn.tealc.asmr.ui.tools.SubtitleRenameViewModel;
import cn.tealc.asmr.ui.tools.ToolView;
import cn.tealc.asmr.ui.tools.ToolViewModel;
import cn.tealc.asmr.mvvm.FluentViewLoader;
import cn.tealc.asmr.mvvm.ViewTuple;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;

/**
 * 视图工厂，集中构建「View + ViewModel」配对并返回已加载的 {@link ViewTuple}。
 * <p>
 * 调用方只需提供页面所需数据（作品、字幕文件、按钮事件回调等），由本类通过
 * {@link FluentViewLoader} 实例化对应 ViewModel 并加载视图（FXML 或 Java View）。
 *
 * @author Leck
 */
public class ViewFactory {
    public static ViewTuple<UpdateInfoView, UpdateInfoViewModel> updateInfoView(AsmrWork work){
        return FluentViewLoader.fxmlView(UpdateInfoView.class).viewModel(new UpdateInfoViewModel(work)).load();
    }

    public static ViewTuple<UpdateTagView, UpdateTagViewModel> updateTagView(AsmrWork work){
        return FluentViewLoader.fxmlView(UpdateTagView.class).viewModel(new UpdateTagViewModel(work)).load();
    }

    public static ViewTuple<ProgressMessageView, ProgressMessageViewModel> progressMessageView(EventHandler<ActionEvent> okEvent,EventHandler<ActionEvent> cancelEvent){
        return FluentViewLoader.fxmlView(ProgressMessageView.class).viewModel(new ProgressMessageViewModel(okEvent,cancelEvent)).load();
    }

    public static ViewTuple<PlayerDetailView, PlayerDetailViewModel> playerDetailView(){
        return FluentViewLoader.fxmlView(PlayerDetailView.class).load();
    }

    public static ViewTuple<GalgameLoadView, GalgameLoadViewModel> galgameLoadView(){
        return FluentViewLoader.fxmlView(GalgameLoadView.class).load();
    }

    public static ViewTuple<SubtitleRenameView, SubtitleRenameViewModel> subtitleRenameView(AsmrWork work, String filepath){
        return FluentViewLoader.javaView(SubtitleRenameView.class).viewModel(new SubtitleRenameViewModel(work, filepath)).load();
    }


    public static ViewTuple<WorkDetailView, WorkDetailViewModel> workDetailView(AsmrWork work){
        return FluentViewLoader.javaView(WorkDetailView.class).viewModel(new WorkDetailViewModel(work)).load();

    }

    public static ViewTuple<MediaLibraryView, MediaLibraryViewModel> mediaLibraryView(){
        return FluentViewLoader.fxmlView(MediaLibraryView.class).load();
    }

    public static ViewTuple<ToolView, ToolViewModel> toolView(){
        return FluentViewLoader.fxmlView(ToolView.class).load();
    }

    /**
     * 创建字幕查看器对话框。
     *
     * @param subtitleFile 用户选中的字幕文件
     * @param work         当前作品（用于获取 MediaSource）
     * @return 字幕查看器实例
     */
    public static SubtitleViewerView subtitleViewerView(AsmrFile subtitleFile, AsmrWork work) {
        return new SubtitleViewerView(subtitleFile, work);
    }

    /**
     * 创建图片查看器对话框。
     *
     * @param imageFile 用户选中的图片文件
     * @param work     当前作品（用于获取 MediaSource）
     * @return 图片查看器实例
     */
    public static ImageViewerView imageViewerView(AsmrFile imageFile, AsmrWork work) {
        return new ImageViewerView(imageFile, work);
    }
}
