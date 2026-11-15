package cn.tealc.asmr.model.galgame;

/**
 * Galgame 播放界面的一套皮肤描述。
 * <p>
 * 实例只由 {@link GalgameSkinRegistry} 的内置列表给定，{@code id}、{@code name}、{@code author}、
 * {@code desc} 是展示与比对用的元信息，{@code path}、{@code css}、{@code fxml} 是 classpath 资源路径：
 * 播放界面用 {@code getResource(...)} 按后两者加载布局与样式，资源缺失时直接抛出异常。
 * <p>
 * 当前选中的皮肤保存在 {@link cn.tealc.asmr.config.AppSettings#getGalgameSkin()}（该字段为
 * {@code static}，全应用共用一份），按 JavaBean 属性随设置文件读写，读回时经
 * {@link GalgameSkinRegistry#normalize(cn.tealc.asmr.model.galgame.GalgameSkin)} 归一回注册表中的实例，
 * 避免设置里存的残缺对象指向不存在的资源。
 * <p>
 * 无参构造器与 setter 供 Jackson 反序列化使用。
 *
 * @author Leck
 */
public class GalgameSkin {
    private int id;
    private String name;
    private String author;
    private String desc;
    private String path;
    private String css;
    private String fxml;

    public GalgameSkin() {
    }

    public GalgameSkin(int id, String name, String author, String desc, String path, String css, String fxml) {
        this.id = id;
        this.name = name;
        this.author = author;
        this.desc = desc;
        this.path = path;
        this.css = css;
        this.fxml = fxml;
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

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getCss() {
        return css;
    }

    public void setCss(String css) {
        this.css = css;
    }

    public String getFxml() {
        return fxml;
    }

    public void setFxml(String fxml) {
        this.fxml = fxml;
    }

    /**
     * 仅以皮肤编号判定是否同一套皮肤：内置皮肤编号固定，皮肤下拉框靠本方法定位当前选中项。
     * <p>
     * 入参为 {@code null} 或非 {@link GalgameSkin} 类型时分别返回 false、抛出
     * {@link ClassCastException}；未覆写 {@link Object#hashCode()}，故不要用作哈希容器的键。
     *
     * @param obj 待比较的对象
     * @return 同为 {@link GalgameSkin} 且 {@code id} 相同时返回 true
     */
    @Override
    public boolean equals(Object obj) {
        GalgameSkin temp= (GalgameSkin) obj;
        if (temp!=null){
            if (temp.getId() == this.id)
                return true;
        }
        return false;
    }
}