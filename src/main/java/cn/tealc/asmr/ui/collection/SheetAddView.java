package cn.tealc.asmr.ui.collection;

import cn.tealc.asmr.mvvm.FXMLView;
import cn.tealc.asmr.mvvm.InjectViewModel;

/**
 * 歌单新增页 {@code SheetAddView.fxml} 的控制器，目前只声明了视图模型注入，
 * {@link #initialize()} 为空实现。
 * <p>
 * 代码中没有任何地方加载该 FXML：创建/修改歌单实际由 {@link CollectionView} 内基于
 * {@link cn.tealc.asmr.ui.dialog.Dialogs} 的弹窗完成，本类属于尚未接入的遗留界面。
 *
 * @author Leck
 */
public class SheetAddView implements FXMLView<SheetAddViewModel> {

    @InjectViewModel
    private SheetAddViewModel viewModel;

    public void initialize() {

    }
}