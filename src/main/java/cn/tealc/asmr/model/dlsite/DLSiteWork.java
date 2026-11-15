package cn.tealc.asmr.model.dlsite;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Comparator;
import java.util.List;

/**
 * DLSite maniax 接口的单条作品信息，对应 {@code product.json} 返回数组中的一个元素。
 * <p>
 * 字段名与 JSON 键保持一致（不做重命名映射），由 Jackson 反序列化；解析时关闭了未知属性报错，
 * 接口新增字段不影响同步。两处例外：{@code language_editions} 标了 {@link JsonIgnore}，
 * 改由 {@link cn.tealc.asmr.task.dlsite.DLSiteThread#parse(String)} 逐条手工构造；
 * {@code titleCN} 不是接口字段，由同步任务确认存在简体中文版后回填。
 * <p>
 * 图片地址不含协议，读取时统一补 {@link DLImage#HEAD}；接口在没有任何创作者时会把
 * {@code creaters} 返回成空数组，由 {@link cn.tealc.asmr.task.dlsite.DLSiteThread} 在解析前替换为
 * {@code {}}，否则无法绑定为本对象。
 * <p>
 * 仅在刮削流程中作为临时载体，最终由同步任务映射为
 * {@link cn.tealc.asmr.model.AsmrWork} 落库。
 *
 * @author Leck
 */
public class DLSiteWork {
    private String workno;
    private String work_name;
    private String maker_id;
    private String maker_name;
    private DLImage image_main;
    private DLImage image_thum;
    private DLImage image_thum_mini;
    /** 缩略图地址字符串；注意与上面的 {@code image_thum} 对象不是同一个 JSON 键，读取时补协议前缀 */
    private String image_thumb;
    private DLCreator creaters;
    private List<DLGenre> genres;
    @JsonIgnore
    private List<DLLanguage> language_editions;

    /** 年龄分级编号，仅 {@code "1"}（全年齢）被视为非 NSFW */
    private String age_category;
    private String age_category_string;
    /** 接口原样返回的登记日期时间串，{@link #getRegist_date()} 只截取空格之前的日期部分 */
    private String regist_date;

    /** 作品类型标记，随接口反序列化填充，本项目未使用 */
    private String type;

    /** 中文标题，非接口字段：由同步任务二次请求译作版本后写入 */
    private String titleCN;
    private DLTranslationInfo translation_info;

    public void setType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    /**
     * 判断作品是否为成人向。
     * <p>
     * 只有 {@code age_category} 等于 {@code "1"}（全年齢）才算非 NSFW，其余取值一律按 NSFW 处理；
     * 该字段为 {@code null} 时本方法会抛 {@link NullPointerException}。
     *
     * @return 含 NSFW 返回 true
     */
    public Boolean isNsfw(){
        if (age_category.equals("1")){
            return false;
        }else {
            return true;
        }
    }



    /**
     * 取本作品的中文语言版本，简体中文优先于繁体中文。
     * <p>
     * 仅依据 {@link DLLanguage#isSimple()} / {@link DLLanguage#isTradition()} 过滤和排序，
     * 不使用接口给出的 {@code display_order}；{@code language_editions} 由
     * {@link cn.tealc.asmr.task.dlsite.DLSiteThread} 手工填充，未填充时同样返回 {@code null}。
     *
     * @return 中文版本条目，没有中文版本时返回 null
     */
    public DLLanguage getCN(){
        if (language_editions != null){
            List<DLLanguage> list = language_editions.stream()
                    .filter(dlLanguage -> dlLanguage.isSimple() || dlLanguage.isTradition())
                    .sorted((o1, o2) -> {
                        if (o1.isSimple() && o2.isTradition()){
                            return -1;
                        } else if (o1.isTradition() && o2.isSimple()){
                            return 1;
                        }else {
                            return 0;
                        }
                    })
                    .toList();
            if (!list.isEmpty()) {
                return list.get(0);
            }
        }
        return null;
    }





    public String getWorkno() {
        return workno;
    }

    public void setWorkno(String workno) {
        this.workno = workno;
    }

    public String getWork_name() {
        return work_name;
    }

    public void setWork_name(String work_name) {
        this.work_name = work_name;
    }

    public String getMaker_id() {
        return maker_id;
    }

    public void setMaker_id(String maker_id) {
        this.maker_id = maker_id;
    }

    public String getMaker_name() {
        return maker_name;
    }

    public void setMaker_name(String maker_name) {
        this.maker_name = maker_name;
    }

    public DLImage getImage_main() {
        return image_main;
    }

    public void setImage_main(DLImage image_main) {
        this.image_main = image_main;
    }

    public DLImage getImage_thum() {
        return image_thum;
    }

    public void setImage_thum(DLImage image_thum) {
        this.image_thum = image_thum;
    }

    public DLImage getImage_thum_mini() {
        return image_thum_mini;
    }

    public void setImage_thum_mini(DLImage image_thum_mini) {
        this.image_thum_mini = image_thum_mini;
    }

    public DLCreator getCreaters() {
        return creaters;
    }

    public void setCreaters(DLCreator creaters) {
        this.creaters = creaters;
    }

    public List<DLGenre> getGenres() {
        return genres;
    }

    public void setGenres(List<DLGenre> genres) {
        this.genres = genres;
    }

    public List<DLLanguage> getLanguage_editions() {
        return language_editions;
    }

    public void setLanguage_editions(List<DLLanguage> language_editions) {
        this.language_editions = language_editions;
    }


    public String getAge_category() {
        return age_category;
    }

    public void setAge_category(String age_category) {
        this.age_category = age_category;
    }

    public String getAge_category_string() {
        return age_category_string;
    }

    public void setAge_category_string(String age_category_string) {
        this.age_category_string = age_category_string;
    }

    public DLTranslationInfo getTranslation_info() {
        return translation_info;
    }

    public void setTranslation_info(DLTranslationInfo translation_info) {
        this.translation_info = translation_info;
    }

    public String getRegist_date() {
        int i = regist_date.indexOf(" ");
        if (i !=-1){
            return regist_date.substring(0,i);
        }else {
            return regist_date;
        }

    }

    public void setRegist_date(String regist_date) {
        this.regist_date = regist_date;
    }

    public String getImage_thumb() {
        return DLImage.HEAD+image_thumb;
    }

    public void setImage_thumb(String image_thumb) {
        this.image_thumb = image_thumb;
    }

    public String getTitleCN() {
        return titleCN;
    }

    public void setTitleCN(String titleCN) {
        this.titleCN = titleCN;
    }
}
