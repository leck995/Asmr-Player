package cn.tealc.asmr.ui.album;

import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.album.AlbumWorkPage;
import cn.tealc.asmr.model.album.WorkAlbumSummary;
import cn.tealc.asmr.service.WorkAlbumService;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 集册主页与详情的独立查询状态，不使用主网格筛选条件。 */
public class WorkAlbumViewModel {
    public static final int PAGE_SIZE = 24;
    private final WorkAlbumService service;

    public enum Sort {
        CREATED("创建时间"), ADDED("最后添加"), COUNT("作品数量"), NAME("名称");
        private final String text;
        Sort(String text) { this.text = text; }
        @Override public String toString() { return text; }
    }

    public record Content(List<WorkAlbumSummary> albums, AlbumWorkPage page) { }

    public WorkAlbumViewModel() { this(DI.get(WorkAlbumService.class)); }

    WorkAlbumViewModel(WorkAlbumService service) { this.service = service; }

    public Content load(Integer albumId, String search, Sort sort, int page) {
        List<WorkAlbumSummary> albums = service.summaries();
        if (albumId == null) {
            String keyword = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
            albums = albums.stream().filter(summary -> summary.album().name().toLowerCase(Locale.ROOT).contains(keyword)
                    || summary.album().description().toLowerCase(Locale.ROOT).contains(keyword))
                    .sorted(comparator(sort)).toList();
            return new Content(albums, null);
        }
        return new Content(albums, service.works(albumId, search, page, PAGE_SIZE));
    }

    private Comparator<WorkAlbumSummary> comparator(Sort sort) {
        Comparator<WorkAlbumSummary> comparator = switch (sort) {
            case CREATED -> Comparator.comparing(summary -> summary.album().createdAt(), Comparator.reverseOrder());
            case ADDED -> Comparator.comparing(summary -> summary.album().lastAddedAt(),
                    Comparator.nullsLast(Comparator.reverseOrder()));
            case COUNT -> Comparator.comparingInt(WorkAlbumSummary::workCount).reversed();
            case NAME -> Comparator.comparing(summary -> summary.album().name(), String.CASE_INSENSITIVE_ORDER);
        };
        return comparator.thenComparing(summary -> summary.album().id(), Comparator.reverseOrder());
    }
}
