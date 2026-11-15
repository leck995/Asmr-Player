package cn.tealc.asmr.ui.navigation;

/**
 * 歌单详情页路由。
 */
public record CollectionDetailRoute(int sheetId) implements Route {

    @Override
    public String title() {
        return "歌单详情";
    }
}
