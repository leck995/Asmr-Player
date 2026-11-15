package cn.tealc.asmr.model.dlsite;

import java.util.List;

/**
 * 作品的翻译版本关系信息，对应 {@code product.json} 中的 {@code translation_info} 对象。
 * <p>
 * 字段名与 JSON 键完全一致，保留了接口的 {@code is_} 前缀，因此布尔读取方法是
 * {@code isIsXxx()} 形式（{@code isIs_original()} 读的是 {@code is_original}），
 * 名字看着重复但这是 Jackson 按名绑定的结果，改动会破坏同步。
 * <p>
 * 同步逻辑只使用原稿/父作品两个标记和三个作品编号字段，用于回填
 * {@link DLLanguage#getChildren()} 与作品的原文编号；其余标记与 {@code lang} 仅随接口数据保留，
 * 本项目未解读其含义。
 *
 * @author Leck
 */
public class DLTranslationInfo {
    private boolean is_translation_agree;
    private boolean is_volunteer;
    /** 本作品即原文（非译作） */
    private boolean is_original;
    /** 本作品是若干子作品的父作品 */
    private boolean is_parent;
    private boolean is_child;
    private boolean is_translation_bonus_child;
    /** 原文作品编号；作品非原稿时，同步逻辑用它填充作品的原文编号字段 */
    private String original_workno;
    /** 父作品编号；本作品既非原稿也非父作品时，同步逻辑在该版本的关联信息中登记当前编号 */
    private String parent_workno;
    /** 子作品编号列表；本作品为父作品时，同步逻辑将其以 {@code ,} 连接后写入对应语言版本 */
    private List<String> child_worknos;
    private String lang;

    public boolean isIs_translation_agree() {
        return is_translation_agree;
    }

    public void setIs_translation_agree(boolean is_translation_agree) {
        this.is_translation_agree = is_translation_agree;
    }

    public boolean isIs_volunteer() {
        return is_volunteer;
    }

    public void setIs_volunteer(boolean is_volunteer) {
        this.is_volunteer = is_volunteer;
    }

    public boolean isIs_original() {
        return is_original;
    }

    public void setIs_original(boolean is_original) {
        this.is_original = is_original;
    }

    public boolean isIs_parent() {
        return is_parent;
    }

    public void setIs_parent(boolean is_parent) {
        this.is_parent = is_parent;
    }

    public boolean isIs_child() {
        return is_child;
    }

    public void setIs_child(boolean is_child) {
        this.is_child = is_child;
    }

    public boolean isIs_translation_bonus_child() {
        return is_translation_bonus_child;
    }

    public void setIs_translation_bonus_child(boolean is_translation_bonus_child) {
        this.is_translation_bonus_child = is_translation_bonus_child;
    }

    public String getOriginal_workno() {
        return original_workno;
    }

    public void setOriginal_workno(String original_workno) {
        this.original_workno = original_workno;
    }

    public String getParent_workno() {
        return parent_workno;
    }

    public void setParent_workno(String parent_workno) {
        this.parent_workno = parent_workno;
    }

    public List<String> getChild_worknos() {
        return child_worknos;
    }

    public void setChild_worknos(List<String> child_worknos) {
        this.child_worknos = child_worknos;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }
}