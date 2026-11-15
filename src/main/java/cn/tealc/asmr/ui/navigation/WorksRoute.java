package cn.tealc.asmr.ui.navigation;

import java.util.Objects;

/**
 * 作品列表页路由。
 */
public record WorksRoute(WorkSource source) implements Route {

    public WorksRoute {
        Objects.requireNonNull(source, "source");
    }

    @Override
    public String title() {
        return source.title();
    }
}
