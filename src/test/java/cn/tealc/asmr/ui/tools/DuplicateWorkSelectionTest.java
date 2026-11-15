package cn.tealc.asmr.ui.tools;

import cn.tealc.asmr.model.AsmrWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DuplicateWorkSelectionTest {
    private final DuplicateWorkSelection selection = new DuplicateWorkSelection();
    private final List<AsmrWork> works = new ArrayList<>();

    @BeforeEach
    void setup() {
        for (int id = 1; id <= 6; id++) {
            AsmrWork work = new AsmrWork();
            work.setId(id);
            work.setWorkId("RJ100000");
            works.add(work);
        }
        selection.setItems(works);
    }

    @Test
    void singleClickReplacesSelectionAndCtrlTogglesSpecificVersion() {
        click(0, false, false);
        click(2, true, false);
        assertSelected(1, 3);
        click(0, true, false);
        assertSelected(3);
        click(5, false, false);
        assertSelected(6);
    }

    @Test
    void shiftSelectsAcrossGroupsAndKeepsAnchorWhenRangeShrinks() {
        click(1, false, false);
        click(5, false, true);
        assertSelected(2, 3, 4, 5, 6);
        click(3, false, true);
        assertSelected(2, 3, 4);
        click(0, false, true);
        assertSelected(1, 2);
    }

    @Test
    void ctrlShiftAddsRangeWithoutDroppingExistingSelection() {
        click(0, false, false);
        click(3, true, false);
        click(5, true, true);
        assertSelected(1, 4, 5, 6);
    }

    @Test
    void shiftWithoutAnchorSelectsOnlyTarget() {
        click(3, false, true);
        assertSelected(4);
    }

    @Test
    void selectionSurvivesSortingAndShiftUsesCurrentVisibleOrder() {
        click(1, false, false);
        Collections.reverse(works);
        selection.setItems(works);
        click(0, false, true);
        assertSelected(6, 5, 4, 3, 2);
    }

    @Test
    void removingRowsPrunesSelectionAndInvalidatesMissingAnchor() {
        click(0, false, false);
        click(3, true, false);
        selection.setItems(works.subList(0, 3));
        assertSelected(1);
        click(2, false, true);
        assertSelected(3);
    }

    @Test
    void clearAndSelectAllUpdateCountAndResetAnchor() {
        selection.selectAll();
        assertSelected(1, 2, 3, 4, 5, 6);
        selection.clear();
        assertSelected();
        click(4, false, true);
        assertSelected(5);
    }

    private void click(int index, boolean ctrl, boolean shift) {
        selection.select(works.get(index), ctrl, shift);
    }

    private void assertSelected(Integer... ids) {
        assertEquals(List.of(ids), selection.getSelectedWorks().stream().map(AsmrWork::getId).toList());
        assertEquals(ids.length, selection.selectedCountProperty().get());
    }
}
