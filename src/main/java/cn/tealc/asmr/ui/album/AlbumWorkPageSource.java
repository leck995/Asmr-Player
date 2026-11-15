package cn.tealc.asmr.ui.album;

import cn.tealc.asmr.model.album.AlbumWorkPage;
import cn.tealc.asmr.service.WorkAlbumService;
import cn.tealc.asmr.ui.work.list.WorkPage;
import cn.tealc.asmr.ui.work.list.WorkPageSource;

/**
 * 作品集详情的作品列表数据源。
 *
 * @author Leck
 */
public final class AlbumWorkPageSource implements WorkPageSource {

    private final WorkAlbumService service;
    private final int albumId;
    private final String search;
    private final int sortType;
    private final boolean descending;
    private final boolean subtitle;
    private final boolean nsfw;
    private final boolean noMate;
    private final boolean showLocal;
    private final boolean showNetwork;

    public AlbumWorkPageSource(WorkAlbumService service, int albumId, String search,
                               int sortType, boolean descending, boolean subtitle, boolean nsfw,
                               boolean noMate, boolean showLocal, boolean showNetwork) {
        this.service = service;
        this.albumId = albumId;
        this.search = search;
        this.sortType = sortType;
        this.descending = descending;
        this.subtitle = subtitle;
        this.nsfw = nsfw;
        this.noMate = noMate;
        this.showLocal = showLocal;
        this.showNetwork = showNetwork;
    }

    @Override
    public WorkPage load(int pageIndex, int pageSize) {
        AlbumWorkPage page = service.works(albumId, search, pageIndex, pageSize,
                sortType, descending, subtitle, nsfw, noMate, showLocal, showNetwork);
        return new WorkPage(page.works(), page.total());
    }
}
