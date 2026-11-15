package cn.tealc.asmr.model;

/**
 * 分类 / 标签 / 社团 / 声优四类字典条目的通用展示模型。
 * <p>
 * 由 {@code DictionaryService} 与 {@code CategoriesService} 返回，供分类管理页展示；
 * {@code num} 大于 0 表示该条目下仍有作品，不允许删除。
 *
 * @author Leck
 */
public class Actor {
    private int id;
    private String name;
    /** 该条目关联的作品数，大于 0 时禁止删除 */
    private int num;
    private String description;
    private String createDateTime;
    private String updateDateTime;


    public Actor() {
    }


    public Actor(String name) {
        this.name = name;
    }

    public Actor(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public Actor(int id, String name, int num) {
        this.id = id;
        this.name = name;
        this.num = num;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getNum() {
        return num;
    }

    public void setNum(int num) {
        this.num = num;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCreateDateTime() {
        return createDateTime;
    }

    public void setCreateDateTime(String createDateTime) {
        this.createDateTime = createDateTime;
    }

    public String getUpdateDateTime() {
        return updateDateTime;
    }

    public void setUpdateDateTime(String updateDateTime) {
        this.updateDateTime = updateDateTime;
    }
}