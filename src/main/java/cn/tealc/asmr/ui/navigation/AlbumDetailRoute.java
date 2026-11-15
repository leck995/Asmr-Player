package cn.tealc.asmr.ui.navigation;

/**
 * 作品集详情页路由。
 */
public record AlbumDetailRoute(int albumId) implements Route {

    @Override
    public String title() {
        return "作品集";
    }
}
