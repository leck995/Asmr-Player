package cn.tealc.asmr.ui.player;

import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.subtitle.SubtitleDisplayService;
import cn.tealc.asmr.subtitle.SubtitleScriptMode;
import cn.tealc.asmr.subtitle.SubtitleTiming;
import cn.tealc.asmr.ui.dialog.DialogAction;
import cn.tealc.asmr.ui.dialog.DialogHandle;
import cn.tealc.asmr.ui.dialog.DialogSpec;
import cn.tealc.asmr.ui.dialog.Dialogs;
import cn.tealc.asmr.utils.CssLoader;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Locale;

/**
 * 播放中的字幕设置弹窗。
 * <p>
 * 以 {@link Dialogs} 覆盖层展示，修改实时写入 {@code Config}，不提供保存/取消。
 * 延迟值通过 {@link SubtitleTiming} 统一参与同步与字幕跳转计算。
 * 弹窗为单例，重复点击入口只保留一个实例。
 * </p>
 */
public final class SubtitleSyncDialog {

    private static DialogHandle openHandle;

    private SubtitleSyncDialog() {
    }

    /**
     * 打开字幕设置弹窗，必须在 JavaFX 线程调用。
     */
    public static void show() {
        if (openHandle != null && openHandle.isShowing()) {
            return;
        }
        Config.setting.subtitleDelayMs.set(
                SubtitleTiming.clampDelay(Config.setting.subtitleDelayMs.get()));
        openHandle = Dialogs.show(DialogSpec.builder()
                .title("字幕设置")
                .content(createContent())
                .width(420)
                .styleClass("subtitle-sync-dialog")
                .stylesheet(CssLoader.getCss(CssLoader.subtitleSync))
                .action(DialogAction.close("完成").defaultButton())
                .onClosed(handle -> openHandle = null)
                .build());
    }

    private static VBox createContent() {
        VBox content = new VBox(14);
        content.getStyleClass().add("subtitle-sync-content");

        Label delayValue = new Label();
        delayValue.getStyleClass().add("subtitle-sync-value");
        delayValue.setAlignment(Pos.CENTER);
        delayValue.setMaxWidth(Double.MAX_VALUE);
        delayValue.textProperty().bind(Bindings.createStringBinding(
                () -> formatDelay(Config.setting.subtitleDelayMs.get()),
                Config.setting.subtitleDelayMs));

        Slider delaySlider = createDelaySlider();

        HBox stepButtons = new HBox(6,
                createStepButton("-500", -500),
                createStepButton("-100", -100),
                createStepButton("-50", -50),
                createResetButton(),
                createStepButton("+50", 50),
                createStepButton("+100", 100),
                createStepButton("+500", 500));
        stepButtons.getStyleClass().add("subtitle-sync-steps");
        stepButtons.setAlignment(Pos.CENTER);

        Label hint = new Label("正数 = 字幕延后，负数 = 字幕提前");
        hint.getStyleClass().add("subtitle-sync-hint");
        hint.setMaxWidth(Double.MAX_VALUE);
        hint.setAlignment(Pos.CENTER);

        VBox syncSection = new VBox(10, delayValue, delaySlider, stepButtons, hint);
        syncSection.getStyleClass().add("subtitle-sync-section");

        VBox displaySection = new VBox(10);
        displaySection.getStyleClass().add("subtitle-sync-section");
        Label displayTitle = new Label("显示");
        displayTitle.getStyleClass().add("subtitle-sync-section-title");
        displaySection.getChildren().addAll(displayTitle, createScriptModeRow(), createAlignmentRow());

        content.getChildren().addAll(syncSection, new Separator(), displaySection);
        return content;
    }

    private static Slider createDelaySlider() {
        Slider slider = new Slider(SubtitleTiming.MIN_DELAY_MS, SubtitleTiming.MAX_DELAY_MS,
                SubtitleTiming.clampDelay(Config.setting.subtitleDelayMs.get()));
        slider.getStyleClass().add("subtitle-sync-slider");
        slider.setMajorTickUnit(1000);
        slider.setMinorTickCount(19);
        slider.setSnapToTicks(true);
        slider.setShowTickMarks(false);
        slider.setShowTickLabels(false);
        slider.setBlockIncrement(100);

        slider.valueProperty().addListener((observable, oldValue, newValue) ->
                Config.setting.subtitleDelayMs.set(
                        SubtitleTiming.clampDelay((int) Math.round(newValue.doubleValue()))));

        ChangeListener<Number> delayListener = (observable, oldValue, newValue) -> {
            int delay = newValue.intValue();
            if (Math.round(slider.getValue()) != delay) {
                slider.setValue(delay);
            }
        };
        Config.setting.subtitleDelayMs.addListener(new WeakChangeListener<>(delayListener));
        return slider;
    }

    private static HBox createScriptModeRow() {
        Label label = new Label("文字体系");
        label.getStyleClass().add("form-field-label");

        ComboBox<SubtitleScriptMode> comboBox = new ComboBox<>(
                FXCollections.observableArrayList(SubtitleScriptMode.values()));
        comboBox.getStyleClass().add("subtitle-sync-script-mode");
        comboBox.setMaxWidth(Double.MAX_VALUE);
        comboBox.setValue(SubtitleDisplayService.getCurrentMode());
        comboBox.valueProperty().addListener((observable, oldValue, mode) -> {
            if (mode != null && mode != SubtitleDisplayService.getCurrentMode()) {
                SubtitleDisplayService.modeProperty().set(mode.name());
            }
        });

        ChangeListener<String> modeListener = (observable, oldValue, value) -> {
            SubtitleScriptMode mode = SubtitleScriptMode.fromConfig(value);
            if (comboBox.getValue() != mode) {
                comboBox.setValue(mode);
            }
        };
        SubtitleDisplayService.modeProperty().addListener(new WeakChangeListener<>(modeListener));

        HBox row = new HBox(12, label, comboBox);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(comboBox, Priority.ALWAYS);
        return row;
    }

    private static HBox createAlignmentRow() {
        Label label = new Label("对齐");
        label.getStyleClass().add("form-field-label");

        CheckBox centerAlign = new CheckBox("歌词居中");
        centerAlign.setSelected(Config.setting.detailLrcAlignment.get());
        centerAlign.selectedProperty().addListener((observable, oldValue, selected) ->
                Config.setting.detailLrcAlignment.set(selected));

        ChangeListener<Boolean> alignmentListener = (observable, oldValue, selected) ->
                centerAlign.setSelected(selected);
        Config.setting.detailLrcAlignment.addListener(new WeakChangeListener<>(alignmentListener));

        HBox row = new HBox(12, label, centerAlign);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Button createStepButton(String text, int deltaMs) {
        Button button = new Button(text);
        button.getStyleClass().add("subtitle-sync-step-button");
        button.setOnAction(event -> adjustDelay(deltaMs));
        return button;
    }

    private static Button createResetButton() {
        Button button = new Button("重置");
        button.getStyleClass().addAll("subtitle-sync-step-button", "subtitle-sync-reset-button");
        button.setOnAction(event -> Config.setting.subtitleDelayMs.set(0));
        return button;
    }

    private static void adjustDelay(int deltaMs) {
        Config.setting.subtitleDelayMs.set(SubtitleTiming.clampDelay(
                Config.setting.subtitleDelayMs.get() + deltaMs));
    }

    private static String formatDelay(int delayMs) {
        if (delayMs == 0) {
            return "0 ms";
        }
        return String.format(Locale.ROOT, "%+d ms", delayMs);
    }
}
