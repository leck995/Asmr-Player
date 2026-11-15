package cn.tealc.asmr.model.dlsite;

/**
 * DLSite 创作者/社团条目的最小信息，只保留编号与名称。
 * <p>
 * 对应 {@code product.json} 中 {@code creaters} 对象里各角色数组的元素，字段名与 JSON 键一致，
 * 由 Jackson 直接映射，角色分组关系见 {@link DLCreator}。元数据同步只取 {@link #getName()}
 * 拼接作品的声优等展示字段，{@code id} 未被读取。
 *
 * @author Leck
 */
public class DLActor {
    private String id;
    private String name;

    public DLActor() {
    }

    public DLActor(String id, String name) {
        this.id = id;
        this.name = name;
    }

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
}
