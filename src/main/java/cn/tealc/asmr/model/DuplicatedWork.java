package cn.tealc.asmr.model;

import javafx.beans.property.SimpleObjectProperty;

import java.util.List;

/**
 * 一组重复作品的展示模型，供重复作品清理工具使用。
 * <p>
 * 由 {@code ToolViewModel.groupDuplicates} 按原始作品编号分组生成：{@code origin}
 * 为组内第一个作品（对照基准），{@code duplicatedList} 为整组作品（含 {@code origin}）。
 *
 * @author Leck
 */
public class DuplicatedWork {
    private SimpleObjectProperty<AsmrWork> origin;
    private SimpleObjectProperty<List<AsmrWork>> duplicatedList;

    public DuplicatedWork(AsmrWork origin, List<AsmrWork> duplicatedList) {
        this.origin=new SimpleObjectProperty<>(origin);
        this.duplicatedList=new SimpleObjectProperty<>(duplicatedList);
    }

    public AsmrWork getOrigin() {
        return origin.get();
    }

    public SimpleObjectProperty<AsmrWork> originProperty() {
        return origin;
    }

    public void setOrigin(AsmrWork origin) {
        this.origin.set(origin);
    }

    public List<AsmrWork> getDuplicatedList() {
        return duplicatedList.get();
    }

    public SimpleObjectProperty<List<AsmrWork>> duplicatedListProperty() {
        return duplicatedList;
    }

    public void setDuplicatedList(List<AsmrWork> duplicatedList) {
        this.duplicatedList.set(duplicatedList);
    }
}