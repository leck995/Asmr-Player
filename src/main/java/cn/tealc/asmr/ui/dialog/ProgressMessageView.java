package cn.tealc.asmr.ui.dialog;

import cn.tealc.asmr.mvvm.FXMLView;
import cn.tealc.asmr.mvvm.InjectViewModel;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * 进度弹窗的 FXML 视图，由 {@link cn.tealc.asmr.factory.ViewFactory#progressMessageView} 装载。
 * <p>
 * 只负责绑定，不含业务逻辑：标题、消息、进度条与百分比文本全部取自
 * {@link ProgressMessageViewModel} 的属性，确认按钮的禁用状态同样由 ViewModel 驱动。
 * 取消按钮始终可点击，便于用户中途放弃后台任务。
 *
 * @author Leck
 */
public class ProgressMessageView implements FXMLView<ProgressMessageViewModel>, Initializable {

    @InjectViewModel
    private ProgressMessageViewModel viewModel;
    @FXML
    private Button cancelBtn;

    @FXML
    private Label message;

    @FXML
    private Button okBtn;

    @FXML
    private ProgressBar progress;

    @FXML
    private Label progressText;

    @FXML
    private Label title;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        message.textProperty().bind(viewModel.messageProperty());
        title.textProperty().bind(viewModel.titleProperty());

        progress.progressProperty().bind(viewModel.progressProperty());
        progressText.textProperty().bind(viewModel.progressTextProperty());
        okBtn.setOnAction(viewModel.getOkEvent());
        okBtn.disableProperty().bind(viewModel.okDisableProperty());
        cancelBtn.setOnAction(viewModel.getCancelEvent());


    }
}