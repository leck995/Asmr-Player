package cn.tealc.asmr.model;

import cn.tealc.asmr.utils.WorkCodeUtils;

/**
 * 音声作品模型，对应数据库 {@code asmr_works} 表的一条作品记录。
 * <p>
 * 多值字段 {@code vas}、{@code tags}、{@code categories} 为英文逗号分隔的字符串；
 * 封面区分 SAM 原图、缩略图与主图三种 URL。判等以 {@code filepath} 为准。
 *
 * @author Leck
 */
public class AsmrWork {
    private int id;
    /** 作品编号，支持 RJ、BJ、VJ；与数据库自增主键 id 区分。 */
    private String workId;
    private String title;
    private String titleCN;
    private String circle;
    private String release;
    private Double rate;
    private Boolean subtitle;
    private Boolean nsfw;
    private Integer visits;
    private Boolean favorites;
    private String sources;
    private String filepath;
    private String parentPath;
    private String vas;
    private String tags;

    private String categories;
    private String samCoverUrl;
    private String thumbnailCoverUrl;
    private String mainCoverUrl;
    /** 存储类型：0 为本地作品，非 0 为网络作品 */
    private Integer storageType;

    /** 所属媒体库 ID，手动添加或尚未归属媒体库的作品为空 */
    private Integer mediaLibraryId;

    /** 所属存储源 ID，用于按具体连接配置访问文件。 */
    private Integer storageSourceId;

    private Boolean hasInfo; //判断该媒体目录是否有信息文件，有则加载

    /** 关联语言版本，格式为“作品编号#语言”，多项以 * 分隔。 */
    private String relatedWorkIds;

    /** 原始版本的作品编号。 */
    private String originWorkId;

    public AsmrWork() {
    }

    public Boolean getHasInfo() {
        return hasInfo;
    }

    public void setHasInfo(Boolean hasInfo) {
        this.hasInfo = hasInfo;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getWorkId() {
        return workId;
    }

    public void setWorkId(String workId) {
        String normalized = WorkCodeUtils.normalize(workId);
        this.workId = normalized != null ? normalized : workId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitleCN() {
        return titleCN;
    }

    public void setTitleCN(String titleCN) {
        this.titleCN = titleCN;
    }

    public String getCircle() {
        return circle;
    }

    public void setCircle(String circle) {
        this.circle = circle;
    }

    public String getRelease() {
        return release;
    }

    public void setRelease(String release) {
        this.release = release;
    }

    public Double getRate() {
        return rate;
    }

    public void setRate(Double rate) {
        this.rate = rate;
    }

    public Boolean getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(Boolean subtitle) {
        this.subtitle = subtitle;
    }

    public Boolean getNsfw() {
        return nsfw;
    }

    public void setNsfw(Boolean nsfw) {
        this.nsfw = nsfw;
    }

    public Integer getVisits() {
        return visits;
    }

    public void setVisits(Integer visits) {
        this.visits = visits;
    }

    public Boolean getFavorites() {
        return favorites;
    }

    public void setFavorites(Boolean favorites) {
        this.favorites = favorites;
    }

    public String getSources() {
        return sources;
    }

    public void setSources(String sources) {
        this.sources = sources;
    }

    public String getFilepath() {
        return filepath;
    }

    public void setFilepath(String filepath) {
        this.filepath = filepath;
    }

    public String getParentPath() {
        return parentPath;
    }

    public void setParentPath(String parentPath) {
        this.parentPath = parentPath;
    }

    public String getVas() {
        return vas;
    }

    public void setVas(String vas) {
        this.vas = vas;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getCategories() {
        return categories;
    }

    public void setCategories(String categories) {
        this.categories = categories;
    }

    public String getSamCoverUrl() {
        return samCoverUrl;
    }

    public void setSamCoverUrl(String samCoverUrl) {
        this.samCoverUrl = samCoverUrl;
    }

    public String getThumbnailCoverUrl() {
        return thumbnailCoverUrl;
    }

    public void setThumbnailCoverUrl(String thumbnailCoverUrl) {
        this.thumbnailCoverUrl = thumbnailCoverUrl;
    }

    public String getMainCoverUrl() {
        return mainCoverUrl;
    }

    public void setMainCoverUrl(String mainCoverUrl) {
        this.mainCoverUrl = mainCoverUrl;
    }

    public Integer getStorageType() {
        return storageType;
    }

    public void setStorageType(Integer storageType) {
        this.storageType = storageType;
    }

    public Integer getMediaLibraryId() {
        return mediaLibraryId;
    }

    public void setMediaLibraryId(Integer mediaLibraryId) {
        this.mediaLibraryId = mediaLibraryId;
    }

    public Integer getStorageSourceId() {
        return storageSourceId;
    }

    public void setStorageSourceId(Integer storageSourceId) {
        this.storageSourceId = storageSourceId;
    }

    public String getRelatedWorkIds() {
        return relatedWorkIds;
    }

    public void setRelatedWorkIds(String relatedWorkIds) {
        this.relatedWorkIds = relatedWorkIds;
    }

    public String getOriginWorkId() {
        return originWorkId;
    }

    public void setOriginWorkId(String originWorkId) {
        this.originWorkId = originWorkId;
    }

    @Override
    public boolean equals(Object obj) {
        AsmrWork asmrWorks= (AsmrWork) obj;
        if (asmrWorks == null){
            return super.equals(obj);
        }else{
            return this.filepath.equals(asmrWorks.filepath);
        }

    }
    @Override
    public String toString() {
        StringBuilder stringBuilder=new StringBuilder();
        if (Boolean.TRUE.equals(subtitle)){
            stringBuilder.append("【字幕】");
        }
        stringBuilder.append(title)
                .append("【")
                .append(workId != null && !workId.isBlank() ? workId : "ID" + id)
                .append("】");

        return stringBuilder.toString();
    }
}


