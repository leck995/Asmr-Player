package cn.tealc.asmr.model.dlsite;

/**
 * DLSite 作品的分类（标签）条目，对应 {@code product.json} 中 {@code genres} 数组的元素。
 * <p>
 * 字段名与 JSON 键一致，由 Jackson 直接映射。元数据同步只取 {@link #getName()}，
 * 用 {@code ,} 拼接后写入 {@link cn.tealc.asmr.model.AsmrWork} 的分类字段；
 * {@code id}、{@code search_val}、{@code name_base} 是接口附带的检索信息，本项目未使用。
 *
 * @author Leck
 */
public class DLGenre {
    private String id;
    private String name;
    private String search_val;
    private String name_base;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSearch_val() {
        return search_val;
    }

    public void setSearch_val(String search_val) {
        this.search_val = search_val;
    }

    public String getName_base() {
        return name_base;
    }

    public void setName_base(String name_base) {
        this.name_base = name_base;
    }
}
