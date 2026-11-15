package cn.tealc.asmr.ui.work.list;

import cn.tealc.asmr.model.AsmrWork;

import java.util.List;

/**
 * 作品列表的一页数据。
 *
 * @param works 当前页作品
 * @param total 符合条件的作品总数
 * @author Leck
 */
public record WorkPage(List<AsmrWork> works, int total) {

    public WorkPage {
        works = List.copyOf(works);
        if (total < 0) {
            total = 0;
        }
    }
}
