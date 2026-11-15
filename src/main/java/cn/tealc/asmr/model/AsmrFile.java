package cn.tealc.asmr.model;

import java.io.File;

/**
 * 媒体源目录列表中的一个文件或目录条目。
 * <p>
 * 由各 {@code MediaSource} 实现产出：本地来源的 {@code filepath} 为磁盘绝对路径，
 * 远程来源为远程路径，具体解释由所属来源决定。判等以 {@code filepath} 为准。
 *
 * @author Leck
 */
public class AsmrFile {
    private String filename;
    /** 列表显示名，用于隐藏真实文件名，无额外展示名时同 filename */
    private String hideName;
    /** AI 翻译生成的文件名预览，仅影响列表展示，不改动真实文件名 */
    private String previewName;
    /** 远程来源提供的缩略图地址（可能带时效），本地来源为 null */
    private String thumbnailUrl;
    private String type;
    private boolean isDir;
    private String filepath;
    private long size;          // 文件大小（字节），目录为 0
    private String modified;   // 修改时间（来源相关格式，本地为 epoch millis 的字符串，OpenList 为 API 原始格式）





    public AsmrFile(File file,String hideName) {
        this.filename=file.getName();
        this.hideName=hideName;
        this.isDir=file.isDirectory();
        if ( this.isDir){
            this.type="dir";
        }else {
            this.type=file.getName().substring(file.getName().lastIndexOf(".")+1);
        }
        this.filepath=file.getAbsolutePath();
    }

    /**
     * 来源无关构造器，供 {@code MediaSource} 实现构造文件项时使用。
     * <p>
     * 本地来源传入磁盘绝对路径，远程来源传入远程路径；{@code filepath} 的具体语义
     * 由所属 {@code MediaSource} 解释。
     *
     * @param filename 原始文件名
     * @param filepath 文件路径（本地为磁盘路径，远程为远程路径）
     * @param type     文件类型（目录为 {@code "dir"}，否则为扩展名，不含点）
     * @param isDir    是否为目录
     * @param hideName 显示名，无额外显示名时同 filename
     * @param size     文件大小（字节），目录为 0
     * @param modified 修改时间（来源相关格式）
     */
    public AsmrFile(String filename, String filepath, String type, boolean isDir, String hideName, long size, String modified) {
        this.filename = filename;
        this.filepath = filepath;
        this.type = type;
        this.isDir = isDir;
        this.hideName = hideName;
        this.size = size;
        this.modified = modified;
    }


    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getHideName() {
        return hideName;
    }

    public void setHideName(String hideName) {
        this.hideName = hideName;
    }

    public String getPreviewName() {
        return previewName;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    /**
     * 返回文件列表当前应展示的名称。
     */
    public String getDisplayName() {
        return previewName == null || previewName.isBlank() ? hideName : previewName;
    }

    /**
     * 设置仅用于列表展示的 AI 文件名预览，不会改动真实文件名。
     */
    public void setPreviewName(String previewName) {
        this.previewName = previewName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isDir() {
        return isDir;
    }

    public void setDir(boolean dir) {
        isDir = dir;
    }

    public String getFilepath() {
        return filepath;
    }

    public void setFilepath(String filepath) {
        this.filepath = filepath;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getModified() {
        return modified;
    }

    public void setModified(String modified) {
        this.modified = modified;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AsmrFile cop)) {
            return false;
        }
        return cop.getFilepath().equals(this.filepath);
    }

    @Override
    public String toString() {
        return filename;
    }
}
