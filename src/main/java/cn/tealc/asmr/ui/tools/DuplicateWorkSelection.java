package cn.tealc.asmr.ui.tools;

import cn.tealc.asmr.model.AsmrWork;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.SimpleBooleanProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 按界面顺序选择具体作品，数据库主键用于区分不同存储源中的相同编号。 */
public final class DuplicateWorkSelection {
    private List<AsmrWork> items = List.of();
    private final Map<Integer, BooleanProperty> states = new LinkedHashMap<>();
    private final ReadOnlyIntegerWrapper selectedCount = new ReadOnlyIntegerWrapper();
    private Integer anchor;

    public void setItems(List<AsmrWork> items) {
        this.items = List.copyOf(items);
        states.keySet().retainAll(items.stream().map(AsmrWork::getId).toList());
        for (AsmrWork work : items) {
            states.computeIfAbsent(work.getId(), ignored -> new SimpleBooleanProperty());
        }
        if (!states.containsKey(anchor)) anchor = null;
        updateCount();
    }

    public BooleanProperty selectedProperty(AsmrWork work) {
        return states.get(work.getId());
    }

    public void select(AsmrWork work, boolean shortcut, boolean shift) {
        if (!states.containsKey(work.getId())) return;
        int target = indexOf(work.getId());
        int start = anchor == null ? -1 : indexOf(anchor);
        if (shift && start >= 0) {
            if (!shortcut) clearStates();
            for (int i = Math.min(start, target); i <= Math.max(start, target); i++) {
                states.get(items.get(i).getId()).set(true);
            }
        } else {
            boolean next = !shortcut || !selectedProperty(work).get();
            if (!shortcut) clearStates();
            selectedProperty(work).set(next);
            anchor = work.getId();
        }
        updateCount();
    }

    public List<AsmrWork> getSelectedWorks() {
        return items.stream().filter(work -> selectedProperty(work).get()).toList();
    }

    public void clear() {
        clearStates();
        anchor = null;
        updateCount();
    }

    public void selectAll() {
        states.values().forEach(state -> state.set(true));
        if (!items.isEmpty()) anchor = items.getFirst().getId();
        updateCount();
    }

    public ReadOnlyIntegerProperty selectedCountProperty() {
        return selectedCount.getReadOnlyProperty();
    }

    private void clearStates() {
        states.values().forEach(state -> state.set(false));
    }

    private int indexOf(int id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getId() == id) return i;
        }
        return -1;
    }

    private void updateCount() {
        selectedCount.set((int) states.values().stream().filter(BooleanProperty::get).count());
    }
}
