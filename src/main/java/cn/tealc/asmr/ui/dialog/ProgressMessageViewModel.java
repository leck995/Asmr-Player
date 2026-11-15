package cn.tealc.asmr.ui.dialog;

import cn.tealc.asmr.mvvm.FXMLView;
import cn.tealc.asmr.mvvm.ViewModel;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.Initializable;

import java.util.concurrent.Callable;

/**
 * 进度弹窗的 ViewModel，承载标题、消息、进度值与确认/取消回调。
 * <p>
 * 百分比文本由 {@code progress} 派生（大于 0 时显示为整数百分比，否则留空），
 * 所以调用方只需要推进度数值，不必单独维护文本。进度到达 1.0 时自动放开确认按钮，
 * 构造时该按钮处于禁用态，用于避免用户在后台任务结束前提前关闭弹窗。
 * <p>
 * 标题默认为"请稍后"，进度初值取 0.01 而非 0，以免弹出瞬间出现完全空白的进度条。
 *
 * @author Leck
 */
public class ProgressMessageViewModel implements ViewModel {

    private SimpleStringProperty title;
    private SimpleStringProperty message;
    private SimpleStringProperty progressText;
    private SimpleDoubleProperty progress;
    private EventHandler<ActionEvent> okEvent;

    private EventHandler<ActionEvent> cancelEvent;

    /** 确认按钮是否禁用，随进度到达 1.0 自动置为 false */
    private SimpleBooleanProperty okDisable;

    /**
     * 创建一个进度弹窗的 ViewModel。
     *
     * @param okEvent     点击确认时执行的动作，可为 null（此时确认按钮无响应）
     * @param cancelEvent 点击取消时执行的动作，用于中断正在进行的后台任务
     */
    public ProgressMessageViewModel(EventHandler<ActionEvent> okEvent, EventHandler<ActionEvent> cancelEvent) {
        this.okEvent=okEvent;
        this.cancelEvent=cancelEvent;
        title=new SimpleStringProperty("请稍后");
        message=new SimpleStringProperty();
        progressText=new SimpleStringProperty();
        progress=new SimpleDoubleProperty(0.01);
        okDisable=new SimpleBooleanProperty(true);
        progressText.bind(Bindings.createStringBinding(() -> {
            if (getProgress() > 0){
                return String.format("%.0f%%",getProgress() * 100);
            }else
                return null;
        },progress));
        progress.addListener((observableValue, number, t1) -> {
            if (t1.doubleValue() == 1){
                okDisable.set(false);
            }
            System.out.println(t1.doubleValue());
        });
    }

    public String getTitle() {
        return title.get();
    }

    public SimpleStringProperty titleProperty() {
        return title;
    }

    public void setTitle(String title) {
        this.title.set(title);
    }

    public String getMessage() {
        return message.get();
    }

    public SimpleStringProperty messageProperty() {
        return message;
    }

    public void setMessage(String message) {
        this.message.set(message);
    }

    public String getProgressText() {
        return progressText.get();
    }

    public SimpleStringProperty progressTextProperty() {
        return progressText;
    }

    public void setProgressText(String progressText) {
        this.progressText.set(progressText);
    }

    public double getProgress() {
        return progress.get();
    }

    public SimpleDoubleProperty progressProperty() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress.set(progress);
    }

    public EventHandler<ActionEvent> getOkEvent() {
        return okEvent;
    }

    public void setOkEvent(EventHandler<ActionEvent> okEvent) {
        this.okEvent = okEvent;
    }

    public EventHandler<ActionEvent> getCancelEvent() {
        return cancelEvent;
    }

    public void setCancelEvent(EventHandler<ActionEvent> cancelEvent) {
        this.cancelEvent = cancelEvent;
    }

    public boolean isOkDisable() {
        return okDisable.get();
    }

    public SimpleBooleanProperty okDisableProperty() {
        return okDisable;
    }

    public void setOkDisable(boolean okDisable) {
        this.okDisable.set(okDisable);
    }
}