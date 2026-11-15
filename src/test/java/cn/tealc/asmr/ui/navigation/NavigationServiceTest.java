package cn.tealc.asmr.ui.navigation;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationServiceTest {
    private NavigationService service;
    private StackPane host;
    private AtomicInteger shown;
    private AtomicInteger hidden;
    private AtomicInteger disposed;

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        fx(() -> {
            Platform.setImplicitExit(false);
            return null;
        });
    }

    @BeforeEach
    void setUp() throws Exception {
        service = new NavigationService();
        host = new StackPane();
        shown = new AtomicInteger();
        hidden = new AtomicInteger();
        disposed = new AtomicInteger();
        fx(() -> {
            service.attach(host, route -> new RouteView() {
                private final Label view = createNode(route);

                @Override
                public Node node() {
                    return view;
                }

                @Override
                public void onShow() {
                    shown.incrementAndGet();
                }

                @Override
                public void onHide() {
                    hidden.incrementAndGet();
                }

                @Override
                public void dispose() {
                    disposed.incrementAndGet();
                }
            });
            return null;
        });
    }

    @Test
    void pushBackAndForwardKeepSingleHistory() throws Exception {
        Route all = new WorksRoute(WorkSource.all());
        Route categories = new CategoriesRoute();

        fx(() -> {
            service.openRoot(all);
            return null;
        });
        assertEquals(all, current());
        assertEquals(all, hostRoute());
        assertEquals(1, shown.get());
        assertFalse(fx(() -> service.canGoBackProperty().get()));

        fx(() -> {
            service.push(categories);
            return null;
        });
        assertEquals(categories, current());
        assertEquals(categories, hostRoute());
        assertEquals(1, hidden.get());
        assertEquals(2, shown.get());
        assertTrue(fx(() -> service.canGoBackProperty().get()));

        fx(() -> {
            service.back();
            return null;
        });
        assertEquals(all, current());
        assertEquals(all, hostRoute());
        assertEquals(2, hidden.get());
        assertEquals(3, shown.get());
        assertTrue(fx(() -> service.canGoForwardProperty().get()));

        fx(() -> {
            service.forward();
            return null;
        });
        assertEquals(categories, current());
        assertEquals(categories, hostRoute());
        assertEquals(3, hidden.get());
        assertEquals(4, shown.get());
        assertFalse(fx(() -> service.canGoForwardProperty().get()));
    }

    @Test
    void openRootClearsHistoryAndDisposesPages() throws Exception {
        Route all = new WorksRoute(WorkSource.all());
        Route categories = new CategoriesRoute();
        Route playlists = new PlaylistsRoute();

        fx(() -> {
            service.openRoot(all);
            service.push(categories);
            service.openRoot(playlists);
            return null;
        });

        assertEquals(playlists, current());
        assertEquals(playlists, hostRoute());
        assertFalse(fx(() -> service.canGoBackProperty().get()));
        assertFalse(fx(() -> service.canGoForwardProperty().get()));
        assertEquals(2, disposed.get());
        assertEquals(3, shown.get());
    }

    @Test
    void sameRouteIsNotPushedTwice() throws Exception {
        Route all = new WorksRoute(WorkSource.all());

        fx(() -> {
            service.openRoot(all);
            service.push(all);
            return null;
        });

        assertEquals(all, current());
        assertEquals(1, shown.get());
        assertFalse(fx(() -> service.canGoBackProperty().get()));
    }

    @Test
    void navigationBeforeAttachRendersAfterAttach() throws Exception {
        NavigationService unbound = new NavigationService();
        StackPane unboundHost = new StackPane();
        Route all = new WorksRoute(WorkSource.all());

        fx(() -> {
            unbound.openRoot(all);
            return null;
        });
        assertNull(fx(unbound::getCurrentRoute));

        fx(() -> {
            unbound.attach(unboundHost, route -> new RouteView() {
                private final Label view = createNode(route);

                @Override
                public Node node() {
                    return view;
                }
            });
            return null;
        });

        assertEquals(all, fx(unbound::getCurrentRoute));
        assertEquals(all, fx(() -> unboundHost.getChildren().getFirst().getUserData()));
    }

    private static Label createNode(Route route) {
        Label label = new Label(route.title());
        label.setUserData(route);
        return label;
    }

    private Route current() throws Exception {
        return fx(service::getCurrentRoute);
    }

    private Object hostRoute() throws Exception {
        return fx(() -> host.getChildren().getFirst().getUserData());
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(12, TimeUnit.SECONDS);
    }
}
