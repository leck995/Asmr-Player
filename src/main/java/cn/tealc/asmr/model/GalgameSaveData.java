package cn.tealc.asmr.model;

import java.nio.file.Paths;

/**
 * Galgame 模式的一条游戏进度存档，对应数据库 {@code asmr_galgame_save} 表。
 * <p>
 * 由 Galgame 详情页保存，记录恢复播放所需的曲目、进度、字幕选段与背景/缩略图，
 * 经 {@code SaveDataDao} 持久化。
 *
 * @author Leck
 */
public class GalgameSaveData {
    private int id;
    private Integer asmrId;
    private String title;
    private String chapter;
    private String filepath; //当前播放媒体的地址
    /** 存档缩略图截图的本地路径 */
    private String thumb;
    private String background;
    /** 保存时的播放进度（毫秒），恢复时按 {@code Duration.millis} 解释 */
    private double time;
    /** 保存时选中的字幕文本 */
    private String text;
    private String saveDate;


    /**
     * 获取当前播放媒体所在目录，供按目录重新定位曲目使用。
     *
     * @return 播放地址的父目录路径；{@code filepath} 为 null 时返回 null
     */
    public String getFilepathDir(){
        if (filepath != null){
            return Paths.get(filepath).getParent().toString();
        }
        return null;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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

    public String getThumb() {
        return thumb;
    }

    public void setThumb(String thumb) {
        this.thumb = thumb;
    }

    public String getBackground() {
        return background;
    }

    public void setBackground(String background) {
        this.background = background;
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