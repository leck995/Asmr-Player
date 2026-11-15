package cn.tealc.asmr.ui.navigation;

/**
 * 设置页路由。
 */
public record SettingsRoute() implements Route {

    @Override
    public String title() {
        return "设置";
    }
}
