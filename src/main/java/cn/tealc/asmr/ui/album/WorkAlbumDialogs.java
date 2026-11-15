package cn.tealc.asmr.ui.album;

import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.eventbus.EventBus;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.album.WorkAlbum;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.model.message.MessageType;
import cn.tealc.asmr.service.WorkAlbumService;
import cn.tealc.asmr.ui.dialog.DialogAction;
import cn.tealc.asmr.ui.dialog.DialogHandle;
import cn.tealc.asmr.ui.dialog.DialogSpec;
import cn.tealc.asmr.ui.dialog.Alerts;
import cn.tealc.asmr.ui.dialog.Dialogs;
import cn.tealc.asmr.utils.CssLoader;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 作品集弹窗的集中入口，基于 {@link Dialogs} / {@link DialogSpec} 构建，无实例状态。
 * <p>
 * 数据库操作一律交给 {@link AlbumTasks} 在后台线程执行，避免阻塞 JavaFX 应用线程；
 * 失败时不关闭弹窗，而是把原因写进内容区底部的校验标签，让用户可以就地修正后重试。
 * <p>
 * 所有公开方法都使用 {@code CssLoader.workAlbum} 样式表与 {@code album-dialog} 样式类，
 * 新增弹窗时应沿用同一套约定，不要在调用方重复拼样式。
 *
 * @author Leck
 */
public final class WorkAlbumDialogs {

    private WorkAlbumDialogs() { }

    /**
     * 创建或修改作品集。
     *
     * @param album  待编辑的作品集；为 {@code null} 表示新建，此时保存按钮显示为"创建"
     * @param saved  保存成功后的回调，参数为落库后的最新作品集
     * @return 弹窗句柄；名称为空时确认按钮保持禁用，直到用户输入
     */
    public static DialogHandle edit(WorkAlbum album, Consumer<WorkAlbum> saved) {
        WorkAlbumService service = DI.get(WorkAlbumService.class);
        TextField name = new TextField(album == null ? "" : album.name());
        name.setPromptText("作品集名称");
        TextArea description = new TextArea(album == null ? "" : album.description());
        description.setPromptText("描述（可选）");
        description.setWrapText(true);
        description.setPrefRowCount(4);
        Label validation = validation();
        VBox body = new VBox(8, new Label("名称"), name, new Label("描述"), description, validation);
        var blankName = Bindings.createBooleanBinding(() -> name.getText().isBlank(), name.textProperty());

        DialogAction save = DialogAction.of(album == null ? "创建" : "保存", "accent", handle -> {
            String title = name.getText();
            String text = description.getText();
            handle.setBusy(true);
            body.setDisable(true);
            AlbumTasks.run(() -> {
                if (album == null) return service.create(title, text);
                if (!service.update(album.id(), title, text)) throw new IllegalStateException("作品集已被删除");
                return new WorkAlbum(album.id(), title.trim(), text.trim(), album.createdAt(), album.lastAddedAt());
            }, result -> {
                handle.close();
                saved.accept(result);
            }, failure -> {
                body.setDisable(false);
                handle.setBusy(false);
                validation.setText(message(failure));
            });
        }).defaultButton().disabledWhen(blankName);

        return Dialogs.show(DialogSpec.builder()
                .title(album == null ? "创建作品集" : "修改作品集")
                .content(body)
                .width(560)
                .styleClass("album-dialog")
                .stylesheet(CssLoader.getCss(CssLoader.workAlbum))
                .action(save)
                .cancel()
                .build());
    }

    /**
     * 请求删除作品集。
     * <p>
     * 只解除集册本身，成员作品的记录与磁盘文件都不受影响。
     *
     * @param album 待删除的作品集
     * @return 弹窗句柄
     */
    public static DialogHandle delete(WorkAlbum album) {
        Label text = new Label("确认删除作品集「" + album.name() + "」？作品记录和文件将保留。");
        text.setWrapText(true);
        Label validation = validation();
        VBox body = new VBox(10, text, validation);

        return Dialogs.show(DialogSpec.builder()
                .title("删除作品集")
                .content(body)
                .width(480)
                .styleClass("album-dialog")
                .stylesheet(CssLoader.getCss(CssLoader.workAlbum))
                .action(DialogAction.of("删除", "danger", handle -> {
                    handle.setBusy(true);
                    AlbumTasks.run(() -> DI.get(WorkAlbumService.class).delete(album.id()),
                            result -> handle.close(),
                            failure -> {
                                handle.setBusy(false);
                                validation.setText(message(failure));
                            });
                }))
                .cancel()
                .build());
    }

    /** 从作品入口只新增勾选关联，不隐式移出其他作品集。 */
    public static DialogHandle chooseAlbums(AsmrWork work) {
        if (work == null || work.getId() <= 0) {
            error(new IllegalArgumentException("请先将作品导入媒体库"));
            return null;
        }
        WorkAlbumService service = DI.get(WorkAlbumService.class);
        VBox choices = new VBox(10);
        choices.setPadding(new Insets(8));
        ScrollPane scroll = new ScrollPane(choices);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(240);
        scroll.getStyleClass().add("album-scroll");
        Label validation = validation();
        Button create = new Button("创建作品集", new FontIcon(Material2OutlinedAL.ADD));
        create.getStyleClass().add("flat");
        VBox body = new VBox(10, scroll, create, validation);
        SimpleBooleanProperty addEnabled = new SimpleBooleanProperty(false);
        List<CheckBox> boxes = new ArrayList<>();
        Runnable refresh = () -> {
            addEnabled.set(false);
            create.setDisable(true);
            choices.getChildren().setAll(new Label("加载中..."));
            AlbumTasks.run(() -> new AlbumChoices(service.summaries(), service.albumsForWork(work.getId())), data -> {
                choices.getChildren().clear();
                boxes.clear();
                for (var summary : data.albums()) {
                    CheckBox box = new CheckBox(summary.album().name() + " · " + summary.workCount() + " 个作品");
                    box.setWrapText(true);
                    box.setMaxWidth(Double.MAX_VALUE);
                    box.setUserData(summary.album().id());
                    if (data.existing().contains(summary.album().id())) {
                        box.setText(box.getText() + " · 已加入");
                        box.setSelected(true);
                        box.setDisable(true);
                    }
                    box.selectedProperty().addListener((observable, previous, selected) ->
                            addEnabled.set(boxes.stream().anyMatch(item -> item.isSelected() && !item.isDisabled())));
                    boxes.add(box);
                    choices.getChildren().add(box);
                }
                if (boxes.isEmpty()) choices.getChildren().add(new Label("暂无作品集"));
                create.setDisable(false);
                validation.setText("");
            }, failure -> {
                choices.getChildren().clear();
                validation.setText(message(failure));
                create.setDisable(false);
            });
        };
        create.setOnAction(event -> edit(null, ignored -> refresh.run()));

        DialogAction add = DialogAction.of("加入", "accent", handle -> {
            List<Integer> ids = boxes.stream().filter(box -> box.isSelected() && !box.isDisabled())
                    .map(box -> (Integer) box.getUserData()).toList();
            handle.setBusy(true);
            create.setDisable(true);
            choices.setDisable(true);
            AlbumTasks.run(() -> service.addWorks(ids, List.of(work.getId())), count -> {
                handle.close();
                EventBus.message(new MessageInfo(MessageType.SUCCESS, "已新增 " + count + " 条作品集关联"));
            }, failure -> {
                handle.setBusy(false);
                create.setDisable(false);
                choices.setDisable(false);
                validation.setText(message(failure));
            });
        }).disabledWhen(addEnabled.not());

        DialogHandle handle = Dialogs.show(DialogSpec.builder()
                .title("加入作品集")
                .content(body)
                .width(560)
                .styleClass("album-dialog")
                .stylesheet(CssLoader.getCss(CssLoader.workAlbum))
                .action(add)
                .cancel()
                .build());
        refresh.run();
        return handle;
    }

    /**
     * "移出本集"的二次确认，供嵌在作品集里渲染的作品 Cell 调用
     * （见 {@link AlbumWorkCellContext}）。
     * <p>
     * 只移除这一条关联关系，不删除作品记录或文件。与 {@link #delete(WorkAlbum)} 不同，
     * 这里用轻量 {@link Alerts} 即可，因为确认后没有需要就地展示的失败输入。
     *
     * @param work   待移出的作品，标题优先取中文名
     * @param remove 确认后执行的实际移除动作
     */
    static void confirmRemove(AsmrWork work, Runnable remove) {
        String name = work.getTitleCN() == null || work.getTitleCN().isBlank() ? work.getTitle() : work.getTitleCN();
        Alerts.basic().title("移出本集")
                .message("将「" + name + "」移出本集？作品记录和文件将保留。")
                .ok("移出", handle -> remove.run()).cancel().show();
    }

    static Label validation() {
        Label label = new Label();
        label.setWrapText(true);
        label.getStyleClass().add("album-error");
        label.managedProperty().bind(label.textProperty().isNotEmpty());
        label.visibleProperty().bind(label.managedProperty());
        return label;
    }

    static void error(Throwable failure) {
        EventBus.message(new MessageInfo(MessageType.ERROR, message(failure)));
    }

    private static String message(Throwable failure) {
        return failure.getMessage() == null ? "集册操作失败，请重试" : failure.getMessage();
    }

    private record AlbumChoices(List<cn.tealc.asmr.model.album.WorkAlbumSummary> albums, Set<Integer> existing) { }
}
