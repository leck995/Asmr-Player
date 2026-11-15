package cn.tealc.asmr.ui.main;

/**
 * 主网格当前生效的排序与筛选条件快照。
 *
 * @param sortType  排序：1=添加时间，2=作品编号，3=发行时间，4=评分，5=随机
 * @param order     是否倒序
 * @param subtext   是否有字幕
 * @param nsfw      是否包含 NSFW
 * @param noMate    是否仅显示未同步作品
 * @param showLocal 是否显示本地作品
 * @param showNetwork 是否显示网络作品
 * @author Leck
 */
public record WorkFilter(int sortType, boolean order, boolean subtext, boolean nsfw,
                         boolean noMate, boolean showLocal, boolean showNetwork) {
}
