package cn.tealc.asmr.model.album;

import cn.tealc.asmr.model.AsmrWork;

/**
 * 作品集列表的一行摘要。
 * <p>
 * {@code workCount} 为集内作品总数；{@code coverWork} 取最近加入的作品作为封面，
 * 空集册在 LEFT JOIN 后没有作品行，此时为 null。由 {@code WorkAlbumDao} 组装。
 *
 * @author Leck
 */
public record WorkAlbumSummary(WorkAlbum album, int workCount, AsmrWork coverWork) {
}
