package cn.tealc.asmr.ui.navigation;

/**
 * 分类浏览页路由。
 */
public record CategoriesRoute() implements Route {

    @Override
    public String title() {
        return "分类";
    }
}
