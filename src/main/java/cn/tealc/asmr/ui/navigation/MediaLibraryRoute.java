package cn.tealc.asmr.ui.navigation;

/**
 * 媒体库管理页路由。
 */
public record MediaLibraryRoute() implements Route {

    @Override
    public String title() {
        return "管理";
    }
}
