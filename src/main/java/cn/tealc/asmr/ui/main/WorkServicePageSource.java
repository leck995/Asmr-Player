package cn.tealc.asmr.ui.main;

import cn.tealc.asmr.model.AsmrKeyType;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.service.WorkService;
import cn.tealc.asmr.ui.work.list.WorkPage;
import cn.tealc.asmr.ui.work.list.WorkPageSource;
import cn.tealc.asmr.ui.navigation.WorkSource;

import java.util.List;

/**
 * 基于 {@link WorkService} 分页查询的作品列表数据源。
 *
 * @author Leck
 */
public final class WorkServicePageSource implements WorkPageSource {

    private final WorkService workService;
    private final WorkSource source;
    private final WorkFilter filter;

    public WorkServicePageSource(WorkService workService, WorkSource source, WorkFilter filter) {
        this.workService = workService;
        this.source = source;
        this.filter = filter;
    }

    @Override
    public WorkPage load(int pageIndex, int pageSize) {
        List<AsmrWork> works = workService.queryPage(source.type(), source.key(), pageIndex,
                filter.sortType(), filter.subtext(), filter.nsfw(), filter.order(), filter.noMate(),
                filter.showLocal(), filter.showNetwork(), source.mediaLibraryId(), pageSize);
        int total = workService.queryCount(source.type(), source.key(),
                filter.subtext(), filter.nsfw(), filter.showLocal(), filter.showNetwork(),
                source.mediaLibraryId());
        return new WorkPage(works, total);
    }

    @Override
    public boolean removesOnUnfavorite() {
        return source.type() == AsmrKeyType.FAVORITE;
    }
}
