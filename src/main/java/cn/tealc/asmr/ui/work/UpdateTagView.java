package cn.tealc.asmr.ui.work;

import cn.tealc.asmr.mvvm.FXMLView;
import cn.tealc.asmr.mvvm.InjectViewModel;
import cn.tealc.asmr.ui.component.PageHeader;
import cn.tealc.asmr.ui.dialog.DialogAction;
import cn.tealc.asmr.ui.dialog.DialogPage;
import cn.tealc.asmr.utils.CssLoader;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.util.Pair;
import org.kordamp.ikonli.material2.Material2OutlinedAL;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * 作品标签编辑弹窗，以 {@link ToggleButton} 呈现全部标签供多选，支持现场新增标签后保存到当前作品。
 * <p>
 * 标签数据来自 {@link UpdateTagViewModel#getTagList()}（{@code <标签名, 是否已选>} 列表），并监听其
 * 变化实时补上新增按钮；保存时收集选中的标签名拼接为逗号分隔串写回作品。
 *
 * @author Leck
 */
public class UpdateTagView implements FXMLView<UpdateTagViewModel>, Initializable, DialogPage {
    @InjectViewModel
    private UpdateTagViewModel viewModel;
    @FXML
    private BorderPane root;
    @FXML
    private FlowPane tagFlowPane;
    @FXML
    private TextField textfield;

    private final List<ToggleButton> tagButtons = new ArrayList<>();
    private final PageHeader header = new PageHeader();
    private List<DialogAction> actions;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        root.getStylesheets().add(CssLoader.getCss(CssLoader.updateTag));

        header.setIcon(Material2OutlinedAL.LOYALTY);
        header.setTitle("编辑作品标签");
        header.setSubtitle("选择已有标签，或添加新标签后保存到当前作品。");

        for (Pair<String, Boolean> pair : viewModel.getTagList()) {
            ToggleButton button = new ToggleButton(pair.getKey());
            button.setSelected(pair.getValue());
            tagButtons.add(button);
            tagFlowPane.getChildren().add(button);
        }

        viewModel.getTagList().addListener((ListChangeListener<? super Pair<String, Boolean>>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    change.getAddedSubList().forEach(pair -> {
                        ToggleButton button = new ToggleButton(pair.getKey());
                        button.setSelected(pair.getValue());
                        tagButtons.add(button);
                        tagFlowPane.getChildren().add(button);
                    });
                }
            }
        });

        actions = List.of(
                DialogAction.of("保存标签", "default-button", handle -> {
                    save();
                    handle.close();
                }).defaultButton(),
                DialogAction.cancel("取消"));
    }

    /**
     * 把输入框中的标签名交给视图模型新增，成功后清空输入框。
     *
     * @param event FXML 按钮事件，未在方法体中使用
     */
    @FXML
    void addTag(ActionEvent event) {
        String name = textfield.getText().trim();
        if (!name.isEmpty()) {
            if (viewModel.addTag(name)) {
                textfield.clear();
            }
        }
    }

    /**
     * 收集被选中的标签按钮文本，拼接为逗号分隔串后连同列表交给 {@link UpdateTagViewModel#save(java.util.List, String)} 落库。
     */
    private void save() {
        List<String> tags = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (ToggleButton tagButton : tagButtons) {
            if (tagButton.isSelected()) {
                sb.append(tagButton.getText()).append(",");
                tags.add(tagButton.getText());
            }
        }

        String string = sb.toString();
        if (string.endsWith(","))
            string = string.substring(0, string.length() - 1);

        viewModel.save(tags, string);
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
