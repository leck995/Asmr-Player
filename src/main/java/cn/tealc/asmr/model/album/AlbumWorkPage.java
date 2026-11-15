package cn.tealc.asmr.model.album;

import cn.tealc.asmr.model.AsmrWork;
import java.util.List;

/**
 * 作品集作品列表的一页数据。
 * <p>
 * {@code works} 为当前页作品（构造时复制为不可变列表），{@code total} 为相同筛选条件下
 * 的作品总数，供分页控件计算页数；由 {@code WorkAlbumDao} 的分页查询组装。
 *
 * @author Leck
 */
public record AlbumWorkPage(List<AsmrWork> works, int total) {
    public AlbumWorkPage {
        works = List.copyOf(works);
    }
}
