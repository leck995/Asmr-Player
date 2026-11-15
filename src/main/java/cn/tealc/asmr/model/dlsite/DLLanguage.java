package cn.tealc.asmr.model.dlsite;

import java.util.List;

/**
 * 作品的语言版本条目，用于识别同一作品的简体/繁体中文版并建立版本间的关联。
 * <p>
 * 该对象不是由 Jackson 自动绑定的：{@link DLSiteWork} 上的 {@code language_editions}
 * 字段标了 {@code @JsonIgnore}，实际内容由
 * {@link cn.tealc.asmr.task.dlsite.DLSiteThread#parse(String)} 逐条读取 JSON 手工构造，
 * 只填入 {@code workno}、{@code label}、{@code display_order}；
 * {@code edition_id}、{@code edition_type} 因不走自动绑定而始终为空，
 * {@code children} 由同步逻辑按翻译父子关系回填。
 * <p>
 * 简繁判定依赖 {@link #getLabel()} 中的汉字，见 {@link #isSimple()} / {@link #isTradition()}。
 *
 * @author Leck
 */
public class DLLanguage {
    private String workno;
    private String edition_id;
    private String edition_type;
    /** 接口给出的版本排序值，同步时原样读入但未被使用：简繁优先顺序由 {@link DLSiteWork#getCN()} 按标签判定 */
    private Integer display_order;
    /** 接口返回的版本名称，形如「簡体中文」「繁体中文」，同时作为语言标签写入 {@code related_work_ids} */
    private String label;

    /** 同一作品的关联版本编号，多个用 {@code ,} 分隔；非接口字段，由同步逻辑按翻译父子关系回填 */
    private String children;

    /**
     * 是否为简体中文版本。
     * <p>
     * 依据 DLSite 标签用字：其简体中文标签写作「簡体中文」（繁体字形），故匹配「簡」字。
     *
     * @return 是简体中文版本返回 true
     */
    public boolean isSimple(){
        return label.contains("簡");
    }

    /**
     * 是否为繁体中文版本，判定方式同样是匹配 {@code label} 中的「繁」字。
     *
     * @return 是繁体中文版本返回 true
     */
    public boolean isTradition(){
        return label.contains("繁");
    }


    /** {@code related_work_ids} 单条片段的格式：带关联版本 / 不带关联版本 */
    private static final String SAVE_TEMPLATE1="%s:%s#%s";
    private static final String SAVE_TEMPLATE2="%s#%s";

    /**
     * 序列化为存入作品 {@code related_work_ids} 的单条片段。
     * <p>
     * 有 {@code children} 时形如 {@code 编号:关联编号#语言标签}，否则为 {@code 编号#语言标签}；
     * 多个版本由调用方用 {@code *} 连接，读取侧（如作品卡片）按同样的分隔规则反解出语言标签。
     *
     * @return 供持久化的片段文本
     */
    public String getSaveText(){
        if (children != null){
            return String.format(SAVE_TEMPLATE1,workno,children,label);
        }else {
            return String.format(SAVE_TEMPLATE2,workno,label);
        }
    }



    public String getWorkno() {
        return workno;
    }

    public void setWorkno(String workno) {
        this.workno = workno;
    }

    public String getEdition_id() {
        return edition_id;
    }

    public void setEdition_id(String edition_id) {
        this.edition_id = edition_id;
    }

    public String getEdition_type() {
        return edition_type;
    }

    public void setEdition_type(String edition_type) {
        this.edition_type = edition_type;
    }

    public Integer getDisplay_order() {
        return display_order;
    }

    public void setDisplay_order(Integer display_order) {
        this.display_order = display_order;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getChildren() {
        return children;
    }

    public void setChildren(String children) {
        this.children = children;
    }
}
