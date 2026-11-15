package cn.tealc.asmr.ui.navigation;

/**
 * 歌单页路由。
 */
public record PlaylistsRoute() implements Route {

    @Override
    public String title() {
        return "歌单";
    }
}
