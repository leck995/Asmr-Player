package cn.tealc.asmr.ui.work.list;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.geometry.Orientation;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;

/**
 * 作品列表排序与筛选图标按钮。
 *
 * @author Leck
 */
public final class WorkSortFilterButton extends Button {

    public WorkSortFilterButton(IntegerProperty sortType, BooleanProperty order, BooleanProperty subtext,
                                BooleanProperty nsfw, BooleanProperty noMate, BooleanProperty showLocal,
                                BooleanProperty showNetwork) {
        setGraphic(new FontIcon(MaterialDesignS.SORT));
        setTooltip(new Tooltip("排序与筛选"));
        getStyleClass().addAll("tool-btn", "tool-sort-btn");

        ContextMenu menu = new ContextMenu();
        ToggleGroup group = new ToggleGroup();
        CustomMenuItem separator = new CustomMenuItem(new Separator(Orientation.HORIZONTAL), false);
        separator.setHideOnClick(false);
        separator.setDisable(true);

        menu.getItems().addAll(
                sortItem("添加时间", 1, group, sortType),
                sortItem("作品编号", 2, group, sortType),
                sortItem("发行时间", 3, group, sortType),
                sortItem("评分", 4, group, sortType),
                sortItem("随机", 5, group, sortType),
                separator,
                filterItem("倒序", order),
                filterItem("NSFW", nsfw),
                filterItem("字幕", subtext),
                filterItem("未同步", noMate),
                filterItem("本地", showLocal),
                filterItem("网络", showNetwork)
        );
        setOnAction(event -> menu.show(this, Side.BOTTOM, 0, 0));
    }

    private static CustomMenuItem sortItem(String text, int value, ToggleGroup group, IntegerProperty sortType) {
        RadioButton radio = new RadioButton(text);
        radio.setToggleGroup(group);
        radio.setSelected(sortType.get() == value);
        radio.setOnAction(event -> sortType.set(value));
        CustomMenuItem item = new CustomMenuItem(radio);
        item.setHideOnClick(false);
        return item;
    }

    private static CustomMenuItem filterItem(String text, BooleanProperty property) {
        CheckBox checkBox = new CheckBox(text);
        checkBox.selectedProperty().bindBidirectional(property);
        CustomMenuItem item = new CustomMenuItem(checkBox);
        item.setHideOnClick(false);
        return item;
    }
}
