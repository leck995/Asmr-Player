package cn.tealc.asmr.ui.work;

import cn.tealc.asmr.Main;
import cn.tealc.asmr.mvvm.FXMLView;
import cn.tealc.asmr.mvvm.InjectViewModel;
import cn.tealc.asmr.ui.component.PageHeader;
import cn.tealc.asmr.ui.dialog.DialogAction;
import cn.tealc.asmr.ui.dialog.DialogPage;
import cn.tealc.asmr.utils.CssLoader;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;
import org.kordamp.ikonli.material2.Material2OutlinedAL;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * 作品资料编辑弹窗，把标题、编号、社团、发行日期、路径及分类/声优等字段与
 * {@link UpdateInfoViewModel} 双向绑定，保存后回写数据库。
 * <p>
 * 作为 {@link DialogPage} 由 {@code Dialogs} 展示：标题栏、正文与"保存/取消"按钮分别经
 * {@link #header()}、{@link #content()}、{@link #actions()} 提供。
 *
 * @author Leck
 */
public class UpdateInfoView extends StackPane implements FXMLView<UpdateInfoViewModel>, Initializable, DialogPage {

    @InjectViewModel
    private UpdateInfoViewModel viewModel;

    @FXML
    private StackPane root;
    @FXML
    private TextField circleField;
    @FXML
    private TextField filepathField;
    @FXML
    private Button importDirBtn;
    @FXML
    private CheckBox nsfwCheckBox;
    @FXML
    private TextField releaseField;
    @FXML
    private TextField workIdField;
    @FXML
    private CheckBox subtitleCheckBox;
    @FXML
    private TextField titleCNField;
    @FXML
    private TextField titleField;
    @FXML
    private TextArea vaArea;
    @FXML
    private TextArea categoryArea;

    private final PageHeader header = new PageHeader();
    private List<DialogAction> actions;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        root.getStylesheets().add(CssLoader.getCss(CssLoader.updateInfo));

        header.setIcon(Material2OutlinedAL.EDIT);
        header.setTitle("编辑作品");
        header.setSubtitle("维护作品资料、来源路径与检索信息。");

        workIdField.textProperty().bindBidirectional(viewModel.workIdProperty());
        nsfwCheckBox.selectedProperty().bindBidirectional(viewModel.nsfwProperty());
        subtitleCheckBox.selectedProperty().bindBidirectional(viewModel.subtextProperty());
        titleField.textProperty().bindBidirectional(viewModel.titleProperty());
        titleCNField.textProperty().bindBidirectional(viewModel.titleCNProperty());
        filepathField.textProperty().bindBidirectional(viewModel.filepathProperty());
        circleField.textProperty().bindBidirectional(viewModel.circleProperty());
        releaseField.textProperty().bindBidirectional(viewModel.releaseDateProperty());

        vaArea.textProperty().bindBidirectional(viewModel.vasTextProperty());
        categoryArea.textProperty().bindBidirectional(viewModel.categoryTextProperty());

        importDirBtn.setOnAction(actionEvent -> {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            // 设置初始地址

            if (Objects.nonNull(viewModel.getFilepath()) && viewModel.getFilepath().length() > 0) {
                Path parent = Paths.get(viewModel.getFilepath()).getParent();
                if (parent != null) {
                    File dir = new File(parent.toString());
                    if (dir.exists()) {
                        directoryChooser.setInitialDirectory(dir);
                    }
                }
            }

            File file = directoryChooser.showDialog(Main.window);
            if (file != null) {
                filepathField.setText(file.getPath());
            }
        });

        actions = List.of(
                DialogAction.of("保存修改", "default-button", handle -> {
                    viewModel.save();
                    handle.close();
                }).defaultButton(),
                DialogAction.cancel("取消"));
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
