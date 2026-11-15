package cn.tealc.asmr.ui.navigation;

/**
 * 工具箱页路由。
 */
public record ToolsRoute() implements Route {

    @Override
    public String title() {
        return "工具箱";
    }
}
