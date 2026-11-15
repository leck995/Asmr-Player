package cn.tealc.asmr.ui.navigation;

/**
 * 集册页路由。
 */
public record AlbumsRoute() implements Route {

    @Override
    public String title() {
        return "集册";
    }
}
