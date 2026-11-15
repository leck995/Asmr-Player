package cn.tealc.asmr.ui.collection;

import cn.tealc.asmr.model.collection.SheetData;
import cn.tealc.asmr.mvvm.FXMLView;
import cn.tealc.asmr.mvvm.InjectViewModel;
import cn.tealc.asmr.ui.component.PageHeader;
import cn.tealc.asmr.ui.dialog.DialogAction;
import cn.tealc.asmr.ui.dialog.DialogPage;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.util.Pair;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * “添加到歌单”弹窗页面：列出全部歌单，勾选后把播放列表中的单个文件收录进选中的歌单。
 * <p>
 * 页面本身实现 {@link DialogPage}，由调用方经 {@link cn.tealc.asmr.ui.dialog.Dialogs#show(DialogPage)}
 * 展示，标题栏、正文与按钮分别交给 {@link #header()}、{@link #content()}、{@link #actions()} 三个插槽。
 * 按钮区是“保存 + 取消”两个 {@link DialogAction}，保存不会自动关闭弹窗，需要显式
 * {@link cn.tealc.asmr.ui.dialog.DialogHandle#close()}。
 * <p>
 * 歌单按钮与 {@code viewModel.getSheetList()} 按下标一一对应，保存时靠该顺序回查歌单，
 * 因此列表变化必须整体重建按钮（见 {@link #refreshSheetButtons()}）。
 *
 * @author Leck
 */
public class AddToSheetView implements FXMLView<AddToSheetViewModel>, Initializable, DialogPage {
    @InjectViewModel
    private AddToSheetViewModel viewModel;
    @FXML
    private BorderPane root;
    @FXML
    private FlowPane sheetFlowPane;
    @FXML
    private TextField textfield;
    @FXML
    private Button createSheetBtn;

    private final List<ToggleButton> sheetButtons = new ArrayList<>();
    private final PageHeader header = new PageHeader();
    private List<DialogAction> actions;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        header.setIcon(Material2OutlinedMZ.PLAYLIST_ADD);
        header.setTitle("添加到歌单");
        header.setSubtitle("选择要收录当前文件的歌单，可同时加入多个歌单。");

        refreshSheetButtons();

        viewModel.getSheetList().addListener((javafx.collections.ListChangeListener<? super Pair<SheetData, Boolean>>) change -> refreshSheetButtons());

        createSheetBtn.disableProperty().bind(textfield.textProperty().isEmpty());

        actions = List.of(
                DialogAction.of("保存", "default-button", handle -> {
                    save();
                    handle.close();
                }).defaultButton(),
                DialogAction.cancel("取消"));
    }

    /**
     * 按当前歌单列表重建一个可选中的 {@code ToggleButton}，选中态取列表里的 Boolean 标记。
     */
    private void createButton(Pair<SheetData, Boolean> pair) {
        ToggleButton button = new ToggleButton(pair.getKey().getSheetName());
        button.setSelected(pair.getValue());
        sheetButtons.add(button);
        sheetFlowPane.getChildren().add(button);
    }

    /**
     * 清空并重建全部歌单按钮，保持按钮顺序与 {@code viewModel.getSheetList()} 一致。
     */
    private void refreshSheetButtons() {
        sheetFlowPane.getChildren().clear();
        sheetButtons.clear();
        viewModel.getSheetList().forEach(this::createButton);
    }

    /**
     * 用输入框内容新建歌单，成功后列表监听会自动重建按钮；空白输入直接忽略。
     */
    @FXML
    void createSheet(ActionEvent event) {
        String sheetName = textfield.getText().trim();
        if (sheetName.isEmpty()) {
            return;
        }
        viewModel.createSheet(sheetName);
        textfield.clear();
    }

    /**
     * 收集处于选中态的歌单并提交。
     * <p>
     * 依赖 {@code sheetButtons} 与 {@code viewModel.getSheetList()} 的下标对齐关系取歌单对象，
     * 未勾选的歌单会在视图模型的差量同步中被移除。
     */
    private void save() {
        List<SheetData> sheets = new ArrayList<>();
        for (int i = 0; i < sheetButtons.size(); i++) {
            if (sheetButtons.get(i).isSelected()) {
                sheets.add(viewModel.getSheetList().get(i).getKey());
            }
        }
        viewModel.save(sheets);
    }

    @Override
    public PageHeader header() {
        return header;
    }

    @Override
    public Node content() {
        return root;
    }

    @Override
    public List<DialogAction> actions() {
        return actions;
    }

    @Override
    public double width() {
        return 760;
    }
}
