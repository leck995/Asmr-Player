package cn.tealc.asmr.ui.component;

import cn.tealc.asmr.utils.CssLoader;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;

import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * 分页导航条控件。
 * <p>
 * 页码按钮式分页：上一页/下一页箭头 + 动态页码按钮 + 跳转输入框。
 * 总页数超过 7 时自动用省略号收缩，始终保留首页和末页。
 * 当前页高亮显示，点击页码按钮切换。
 *
 * @author Leck
 */
public class PaginationBar extends HBox {

    private final IntegerProperty currentPageIndex = new SimpleIntegerProperty(1);
    private final IntegerProperty pageCount = new SimpleIntegerProperty(0);
    private final SimpleObjectProperty<Consumer<Integer>> onPageChange = new SimpleObjectProperty<>();

    private final Button prevBtn;
    private final Button nextBtn;
    private final HBox pageNumbersBox;
    private final TextField jumpField;
    private final ToggleGroup pageToggleGroup = new ToggleGroup();

    public PaginationBar() {
        getStylesheets().add(CssLoader.getCommonCss("/control/pagination-bar.css"));
        getStyleClass().add("pagination-bar");
        setSpacing(4);
        setAlignment(Pos.CENTER);

        prevBtn = new Button();
        prevBtn.setGraphic(new FontIcon(MaterialDesignC.CHEVRON_LEFT));
        prevBtn.getStyleClass().addAll("page-nav-btn", "page-nav-prev");
        prevBtn.setOnAction(e -> {
            int cur = getCurrentPageIndex();
            if (cur > 1) {
                setCurrentPageIndex(cur - 1);
            }
        });

        pageNumbersBox = new HBox(4);
        pageNumbersBox.setAlignment(Pos.CENTER);

        nextBtn = new Button();
        nextBtn.setGraphic(new FontIcon(MaterialDesignC.CHEVRON_RIGHT));
        nextBtn.getStyleClass().addAll("page-nav-btn", "page-nav-next");
        nextBtn.setOnAction(e -> {
            int cur = getCurrentPageIndex();
            if (cur < getPageCount()) {
                setCurrentPageIndex(cur + 1);
            }
        });

        jumpField = new TextField();
        jumpField.setPromptText("跳转");
        jumpField.setPrefWidth(50);
        jumpField.getStyleClass().add("page-jump-field");
        jumpField.setTextFormatter(new TextFormatter<>(change ->
                Pattern.matches("\\d*", change.getText()) ? change : null));
        jumpField.setOnAction(e -> {
            try {
                int index = Integer.parseInt(jumpField.getText());
                if (index >= 1 && index <= getPageCount()) {
                    setCurrentPageIndex(index);
                }
            } catch (NumberFormatException ignored) {
            }
            jumpField.clear();
        });

        getChildren().addAll(prevBtn, pageNumbersBox, nextBtn, jumpField);

        // 监听页码和总页数变化，重新渲染页码按钮
        currentPageIndex.addListener((obs, old, val) -> {
            renderPageNumbers();
            updateNavButtons();
            notifyPageChange();
        });
        pageCount.addListener((obs, old, val) -> {
            renderPageNumbers();
            updateNavButtons();
        });

        renderPageNumbers();
        updateNavButtons();
    }

    /**
     * 根据当前页码和总页数，动态渲染页码按钮区域。
     * 总页数 ≤ 7 全部显示；超过 7 时用省略号收缩。
     */
    private void renderPageNumbers() {
        pageNumbersBox.getChildren().clear();
        int current = getCurrentPageIndex();
        int total = getPageCount();
        if (total <= 0) total = 1;

        ObservableList<Integer> pages = computeVisiblePages(current, total);
        int last = -1;
        for (int page : pages) {
            if (page - last > 1 && last != -1) {
                Label ellipsis = new Label("…");
                ellipsis.getStyleClass().add("page-ellipsis");
                pageNumbersBox.getChildren().add(ellipsis);
            }
            ToggleButton pageBtn = new ToggleButton(String.valueOf(page));
            pageBtn.setToggleGroup(pageToggleGroup);
            pageBtn.getStyleClass().add("page-nav-btn");
            if (page == current) {
                pageBtn.setSelected(true);
            }
            final int target = page;
            pageBtn.setOnAction(e -> {
                setCurrentPageIndex(target);
            });
            pageNumbersBox.getChildren().add(pageBtn);
            last = page;
        }
    }

    /**
     * 计算需要显示的页码列表（含首末页和当前页附近）。
     */
    private ObservableList<Integer> computeVisiblePages(int current, int total) {
        var list = new java.util.ArrayList<Integer>();
        if (total <= 7) {
            for (int i = 1; i <= total; i++) list.add(i);
            return FXCollections.observableList(list);
        }
        // 始终显示首页和末页，当前页附近最多 3 个
        list.add(1);
        int start = Math.max(2, current - 1);
        int end = Math.min(total - 1, current + 1);
        for (int i = start; i <= end; i++) list.add(i);
        list.add(total);
        return FXCollections.observableList(list);
    }

    /**
     * 更新上一页/下一页按钮的 disabled 状态。
     */
    private void updateNavButtons() {
        int current = getCurrentPageIndex();
        int total = getPageCount();
        prevBtn.setDisable(current <= 1);
        nextBtn.setDisable(current >= total);
    }

    /**
     * 通知外部页码已变化。
     */
    private void notifyPageChange() {
        Consumer<Integer> callback = onPageChange.get();
        if (callback != null) {
            callback.accept(getCurrentPageIndex());
        }
    }

    // ==================== 属性暴露 ====================

    public int getCurrentPageIndex() {
        return currentPageIndex.get();
    }

    public void setCurrentPageIndex(int value) {
        currentPageIndex.set(value);
    }

    public IntegerProperty currentPageIndexProperty() {
        return currentPageIndex;
    }

    public int getPageCount() {
        return pageCount.get();
    }

    public void setPageCount(int value) {
        pageCount.set(value);
    }

    public IntegerProperty pageCountProperty() {
        return pageCount;
    }

    public Consumer<Integer> getOnPageChange() {
        return onPageChange.get();
    }

    public void setOnPageChange(Consumer<Integer> callback) {
        onPageChange.set(callback);
    }

    public SimpleObjectProperty<Consumer<Integer>> onPageChangeProperty() {
        return onPageChange;
    }
}
