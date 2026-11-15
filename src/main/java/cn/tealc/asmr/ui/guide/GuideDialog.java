package cn.tealc.asmr.ui.guide;

import cn.tealc.asmr.ui.component.EmptyState;
import cn.tealc.asmr.ui.component.PageHeader;
import cn.tealc.asmr.ui.dialog.DialogAction;
import cn.tealc.asmr.ui.dialog.DialogPage;
import cn.tealc.asmr.utils.CssLoader;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 使用教程弹窗：左侧为问题列表，右侧显示所选问答的正文、步骤与截图。
 * <p>
 * 内容来自 classpath 下的 {@code guide.json}；图片相对 {@code /cn/tealc/asmr/guide/} 解析。
 * 详情节点按需创建并缓存，切换问题时不重复构建。
 *
 * @author Leck
 */
public final class GuideDialog implements DialogPage {

    private static final Logger LOG = LoggerFactory.getLogger(GuideDialog.class);
    private static final String RESOURCE_ROOT = "/cn/tealc/asmr/guide/";
    private static final double MAX_IMAGE_WIDTH = 1000;

    private final PageHeader header;
    private final Node content;
    private final List<DialogAction> actions = List.of(DialogAction.close("关闭"));

    private final List<Entry> entries = new ArrayList<>();
    private final Map<Integer, Node> detailCache = new HashMap<>();
    private final StackPane detailHost = new StackPane();
    private final ScrollPane detailScroll = new ScrollPane();
    private int currentIndex = -1;

    public GuideDialog() {
        GuideContent guide = null;
        try {
            guide = GuideContentLoader.load();
        } catch (RuntimeException e) {
            LOG.error("加载教程内容失败", e);
        }

        header = new PageHeader();
        header.setIcon(Material2OutlinedAL.HELP_OUTLINE);
        header.setTitle(guide == null || guide.title() == null || guide.title().isBlank()
                ? "使用教程"
                : guide.title());
        header.setSubtitle(guide == null ? "教程内容加载失败" : guide.subtitle());

        content = guide == null ? errorContent() : buildContent(guide);
    }

    @Override
    public PageHeader header() {
        return header;
    }

    @Override
    public Node content() {
        return content;
    }

    @Override
    public List<DialogAction> actions() {
        return actions;
    }

    @Override
    public double width() {
        return 900;
    }

    @Override
    public List<String> stylesheets() {
        return List.of(CssLoader.getCss(CssLoader.guide));
    }

    private Node buildContent(GuideContent guide) {
        for (GuideSection section : guide.sections()) {
            for (GuideItem item : section.items()) {
                entries.add(new Entry(section, item));
            }
        }
        if (entries.isEmpty()) {
            return emptyContent();
        }

        VBox list = new VBox();
        list.getStyleClass().add("guide-list");
        ToggleGroup group = new ToggleGroup();
        List<ToggleButton> buttons = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            ToggleButton button = new ToggleButton(entries.get(i).item().question());
            button.getStyleClass().add("guide-list-item");
            button.setWrapText(true);
            button.setTextAlignment(TextAlignment.LEFT);
            button.setAlignment(Pos.CENTER_LEFT);
            button.setMinWidth(0);
            button.setMaxWidth(Double.MAX_VALUE);
            button.prefWidthProperty().bind(Bindings.createDoubleBinding(
                    () -> Math.max(0, list.getWidth() - 16), list.widthProperty()));
            button.setToggleGroup(group);
            button.setUserData(i);
            list.getChildren().add(button);
            buttons.add(button);
        }

        ScrollPane listScroll = new ScrollPane(list);
        listScroll.getStyleClass().add("guide-list-scroll");
        listScroll.setFitToWidth(true);
        listScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        listScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        listScroll.setMinWidth(250);
        listScroll.setPrefWidth(250);
        listScroll.setMaxWidth(250);
        listScroll.setMaxHeight(Double.MAX_VALUE);
        HBox.setHgrow(listScroll, Priority.NEVER);

        detailHost.getStyleClass().add("guide-detail-host");
        detailHost.setAlignment(Pos.TOP_LEFT);
        detailScroll.getStyleClass().add("guide-detail-scroll");
        detailScroll.setContent(detailHost);
        detailScroll.setFitToWidth(true);
        detailScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        detailScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        detailScroll.setMinWidth(0);
        detailScroll.setPrefWidth(0);
        detailScroll.setMaxHeight(Double.MAX_VALUE);
        HBox.setHgrow(detailScroll, Priority.ALWAYS);

        group.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (newToggle == null) {
                // ToggleButton 允许再次点击取消选中，这里保持始终有一项被选中。
                if (oldToggle != null) {
                    oldToggle.setSelected(true);
                }
                return;
            }
            showDetail((Integer) newToggle.getUserData());
        });
        group.selectToggle(buttons.getFirst());

        HBox body = new HBox();
        body.getStyleClass().add("guide-dialog-body");
        body.setPrefSize(860, 470);
        body.setMinHeight(470);
        body.setMaxSize(860, 470);
        body.getChildren().addAll(listScroll, detailScroll);
        return body;
    }

    private void showDetail(int index) {
        if (index < 0 || index >= entries.size() || index == currentIndex) {
            return;
        }
        currentIndex = index;
        detailHost.getChildren().setAll(
                detailCache.computeIfAbsent(index, i -> detailNode(entries.get(i))));
        detailScroll.setVvalue(0);
    }

    private Node detailNode(Entry entry) {
        VBox box = new VBox();
        box.getStyleClass().add("guide-detail");
        box.setMaxWidth(Double.MAX_VALUE);

        if (entry.section().title() != null && !entry.section().title().isBlank()) {
            Label section = new Label(entry.section().title());
            section.getStyleClass().add("guide-detail-section");
            box.getChildren().add(section);
        }

        box.getChildren().add(wrapped(entry.item().question(), "guide-detail-question"));

        if (!entry.item().answer().isEmpty()) {
            VBox answers = new VBox();
            answers.getStyleClass().add("guide-detail-answers");
            for (String paragraph : entry.item().answer()) {
                answers.getChildren().add(wrapped(paragraph, "guide-detail-answer"));
            }
            box.getChildren().add(answers);
        }

        if (!entry.item().steps().isEmpty()) {
            VBox steps = new VBox();
            steps.getStyleClass().add("guide-detail-steps");
            for (int i = 0; i < entry.item().steps().size(); i++) {
                steps.getChildren().add(stepNode(i + 1, entry.item().steps().get(i)));
            }
            box.getChildren().add(steps);
        }

        Node image = imageNode(entry.item());
        if (image != null) {
            box.getChildren().add(image);
        }
        return box;
    }

    private static Node stepNode(int number, String text) {
        Label index = new Label(String.valueOf(number));
        index.getStyleClass().add("guide-step-index");
        Label label = wrapped(text, "guide-step-text");
        HBox.setHgrow(label, Priority.ALWAYS);
        HBox row = new HBox(8, index, label);
        row.setAlignment(Pos.TOP_LEFT);
        return row;
    }

    private Node imageNode(GuideItem item) {
        if (item.image() == null || item.image().isBlank()) {
            return null;
        }
        URL url = GuideDialog.class.getResource(RESOURCE_ROOT + item.image());
        if (url == null) {
            LOG.warn("教程图片缺失，已跳过：{}{}", RESOURCE_ROOT, item.image());
            return null;
        }
        Image image = new Image(url.toExternalForm(), MAX_IMAGE_WIDTH, 0, true, true, false);
        if (image.isError()) {
            LOG.warn("教程图片加载失败，已跳过：{}", url, image.getException());
            return null;
        }

        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        view.fitWidthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(0, Math.min(image.getWidth(), detailHost.getWidth() - 12)),
                detailHost.widthProperty()));

        StackPane frame = new StackPane(view);
        frame.getStyleClass().add("guide-detail-image-frame");

        VBox box = new VBox();
        box.getStyleClass().add("guide-detail-figure");
        box.getChildren().add(frame);
        if (item.caption() != null && !item.caption().isBlank()) {
            box.getChildren().add(wrapped(item.caption(), "guide-detail-caption"));
        }
        return box;
    }

    private static Label wrapped(String text, String styleClass) {
        Label label = new Label(text == null ? "" : text);
        label.getStyleClass().add(styleClass);
        label.setWrapText(true);
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private Node errorContent() {
        EmptyState empty = EmptyState.of(Material2OutlinedAL.HELP_OUTLINE,
                "教程内容加载失败",
                "请检查 resources/cn/tealc/asmr/guide/guide.json 是否存在且格式正确。");
        StackPane pane = new StackPane(empty);
        pane.setPrefHeight(320);
        return pane;
    }

    private Node emptyContent() {
        EmptyState empty = EmptyState.of(Material2OutlinedAL.HELP_OUTLINE,
                "暂无教程内容",
                "在 guide.json 中添加问答后会显示在这里。");
        StackPane pane = new StackPane(empty);
        pane.setPrefHeight(320);
        return pane;
    }

    private record Entry(GuideSection section, GuideItem item) {
    }
}
