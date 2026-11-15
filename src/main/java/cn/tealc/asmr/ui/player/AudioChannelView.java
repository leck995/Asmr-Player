package cn.tealc.asmr.ui.player;

import cn.tealc.asmr.player.AudioEffect;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.mvvm.FXMLView;
import cn.tealc.asmr.mvvm.InjectViewModel;
import cn.tealc.asmr.ui.component.PageHeader;
import cn.tealc.asmr.ui.dialog.DialogAction;
import cn.tealc.asmr.ui.dialog.DialogPage;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import uk.co.caprica.vlcj.player.base.AudioDevice;

import java.util.List;

/**
 * 音频设置弹窗页，提供播放声道、输出设备与均衡器预设三个下拉框。
 * <p>
 * 由 {@code DashboardViewModel} 和 {@code GalgameDetailView} 以 FXML 视图加载后
 * 放进弹窗展示，宽度固定 640，按钮区只有一个“关闭”。
 * <p>
 * 三个下拉框的选中索引单向绑定到 {@code Config.setting} 的
 * {@code audioChannel}/{@code audioOutputDevice}/{@code audioEffect} 属性上，
 * 索引变化时同步调用 {@link AudioChannelViewModel} 应用到 VLC 播放器。
 * 分段均衡器滑杆分组（{@code sliderGroup}）当前无任何逻辑接入，隐藏且不参与布局。
 *
 * @author Leck
 */
public class AudioChannelView implements FXMLView<AudioChannelViewModel>, DialogPage {
    @InjectViewModel
    private AudioChannelViewModel viewModel;
    @FXML
    private AnchorPane root;
    @FXML
    private ChoiceBox<String> channelComboBox;
    @FXML
    private ChoiceBox<String> outputDeviceComboBox;
    @FXML
    private ChoiceBox<AudioEffect> audioEffectChoiceBox;
    @FXML
    private VBox sliderGroup;

    private final PageHeader header = new PageHeader();
    private final List<DialogAction> actions = List.of(DialogAction.cancel("关闭"));

    /**
     * FXML 注入完成后装配弹窗。
     * <p>
     * 每个下拉框依次做三件事：按 {@code Config.setting} 中的索引回显选中项、
     * 把配置属性单向绑定到选中索引、再单独监听选中索引调用 ViewModel 生效。
     * 未选中时索引为 -1，监听器直接忽略，避免把无效值写进配置和播放器。
     */
    public void initialize() {
        header.setIcon(Material2OutlinedAL.EQUALIZER);
        header.setTitle("音频声道");
        header.setSubtitle("调整播放声道、输出设备与均衡器预设。");

        channelComboBox.getSelectionModel().select(Config.setting.getAudioChannel());
        Config.setting.audioChannelProperty().bind(channelComboBox.getSelectionModel().selectedIndexProperty());
        channelComboBox.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            int index = newValue.intValue();
            if (index >= 0){
                viewModel.setChannel(index);
            }
        });

        outputDeviceComboBox.setItems(viewModel.getAudioDeviceNames());
        outputDeviceComboBox.getSelectionModel().select(Config.setting.getAudioOutputDevice());
        Config.setting.audioOutputDeviceProperty().bind(outputDeviceComboBox.getSelectionModel().selectedIndexProperty());
        outputDeviceComboBox.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            int index = newValue.intValue();
            if (index >= 0){
                viewModel.setAudioDevice(index);
            }
        });

        audioEffectChoiceBox.setItems(viewModel.getAudioEffects());
        audioEffectChoiceBox.getSelectionModel().select(Config.setting.getAudioEffect());
        Config.setting.audioEffectProperty().bind(audioEffectChoiceBox.getSelectionModel().selectedIndexProperty());
        audioEffectChoiceBox.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            int index = newValue.intValue();
            if (index >= 0){
                viewModel.setAudioEffect(index);
            }
        });

        sliderGroup.setVisible(false);
        sliderGroup.setManaged(false);

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
        return 640;
    }
}