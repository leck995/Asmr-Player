package cn.tealc.asmr.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.nio.file.Paths;

/**
 * 退出时记录的播放历史快照，用于下次启动恢复播放进度。
 * <p>
 * 以 JSON 文件持久化（路径见 {@code AppPaths.historyFile()}），由
 * {@code PlayHistoryService} 读写；{@code @JsonIgnoreProperties} 保证读取旧版本
 * 多余字段时不报错。
 *
 * @author Leck
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LastPlayingRecord {
    private Integer asmrId;
    private String title;
    private String chapter;
    private String filepath; //当前播放媒体的地址
    /** 退出时的播放进度（毫秒） */
    private double time;
    /** 退出时选中的字幕文本 */
    private String text;
    private String saveDate;


    /**
     * 获取当前播放媒体所在目录，供按目录重建播放列表使用。
     *
     * @return 播放地址的父目录路径；{@code filepath} 为 null 时返回 null
     */
    public String getFilepathDir(){
        if (filepath != null){
            return Paths.get(filepath).getParent().toString();
        }
        return null;
    }


    public Integer getAsmrId() {
        return asmrId;
    }

    public void setAsmrId(Integer asmrId) {
        this.asmrId = asmrId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getChapter() {
        return chapter;
    }

    public void setChapter(String chapter) {
        this.chapter = chapter;
    }

    public String getFilepath() {
        return filepath;
    }

    public void setFilepath(String filepath) {
        this.filepath = filepath;
    }

    public double getTime() {
        return time;
    }

    public void setTime(double time) {
        this.time = time;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getSaveDate() {
        return saveDate;
    }

    public void setSaveDate(String saveDate) {
        this.saveDate = saveDate;
    }
}