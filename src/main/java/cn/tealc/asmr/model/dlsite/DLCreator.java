package cn.tealc.asmr.model.dlsite;

import java.util.List;

/**
 * DLSite 作品的创作者分组，对应 {@code product.json} 中的 {@code creaters} 对象。
 * <p>
 * 四个字段按角色分组存放 {@link DLActor}，字段名与 JSON 键一一对应；接口未给出的分组反序列化后
 * 保持 {@code null}，因此读取前需要判空（{@link cn.tealc.asmr.task.dlsite.DLSiteThread}
 * 就是先判断 {@link #getVoice_by()} 非空才填充作品声优）。
 * <p>
 * 无创作者时接口会把 {@code creaters} 返回成空数组而非对象，
 * {@link cn.tealc.asmr.task.dlsite.DLSiteThread} 在解析前统一替换为 {@code {}}。
 *
 * @author Leck
 */
public class DLCreator {
    /** 社团/作者 */
    private List<DLActor> created_by;
    /** 剧本 */
    private List<DLActor> scenario_by;
    /** 插画 */
    private List<DLActor> illust_by;
    /** 声优，唯一被同步使用的分组 */
    private List<DLActor> voice_by;


    public List<DLActor> getCreated_by() {
        return created_by;
    }

    public void setCreated_by(List<DLActor> created_by) {
        this.created_by = created_by;
    }

    public List<DLActor> getScenario_by() {
        return scenario_by;
    }

    public void setScenario_by(List<DLActor> scenario_by) {
        this.scenario_by = scenario_by;
    }

    public List<DLActor> getIllust_by() {
        return illust_by;
    }

    public void setIllust_by(List<DLActor> illust_by) {
        this.illust_by = illust_by;
    }

    public List<DLActor> getVoice_by() {
        return voice_by;
    }

    public void setVoice_by(List<DLActor> voice_by) {
        this.voice_by = voice_by;
    }
}
