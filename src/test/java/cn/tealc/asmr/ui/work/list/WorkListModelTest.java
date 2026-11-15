package cn.tealc.asmr.ui.work.list;

import cn.tealc.asmr.model.AsmrWork;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class WorkListModelTest {

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

    @Test
    void paginatesAndReloadsPages() throws Exception {
        WorkListModel model = new WorkListModel(2);
        fx(() -> {
            model.setSource(source(5, false), true);
            return null;
        });
        await(() -> model.isLoaded() && model.getItems().size() == 2);
        assertEquals(5, model.getTotal());
        assertEquals(3, model.getPageCount());
        assertEquals(1, model.getPageIndex());

        fx(() -> {
            model.nextPage();
            return null;
        });
        await(() -> model.getPageIndex() == 2 && model.isLoaded() && model.getItems().size() == 2);
        assertEquals(3, model.getItems().getFirst().getId());

        fx(() -> {
            model.nextPage();
            return null;
        });
        await(() -> model.getPageIndex() == 3 && model.isLoaded() && model.getItems().size() == 1);
        assertEquals(5, model.getItems().getFirst().getId());
        assertFalse(fx(() -> model.isLoading()));
    }

    @Test
    void updatesInPlaceAndRemovesUnfavoritedOnFavoriteSource() throws Exception {
        WorkListModel model = new WorkListModel(10);
        fx(() -> {
            model.setSource(source(2, true), true);
            return null;
        });
        await(() -> model.isLoaded() && model.getItems().size() == 2);

        AsmrWork first = fx(() -> model.getItems().getFirst());
        fx(() -> {
            first.setTitle("已更新");
            model.update(first);
            return null;
        });
        assertEquals("已更新", fx(() -> model.getItems().getFirst().getTitle()));

        fx(() -> {
            first.setFavorites(false);
            model.update(first);
            return null;
        });
        await(() -> model.getItems().size() == 1);
        assertEquals(1, model.getTotal());
        assertEquals(2, model.getItems().getFirst().getId());
    }

    @Test
    void removesDeletedWork() throws Exception {
        WorkListModel model = new WorkListModel(10);
        fx(() -> {
            model.setSource(source(2, false), true);
            return null;
        });
        await(() -> model.isLoaded() && model.getItems().size() == 2);

        AsmrWork first = fx(() -> model.getItems().getFirst());
        fx(() -> {
            model.remove(first);
            return null;
        });
        await(() -> model.getItems().size() == 1);
        assertEquals(1, model.getTotal());
        assertEquals(2, model.getItems().getFirst().getId());
    }

    @Test
    void ignoresPageChangesWhileLoading() throws Exception {
        // 用闸门卡住第一页的加载，模拟网络慢时连按侧键的场景
        java.util.concurrent.CountDownLatch gate = new java.util.concurrent.CountDownLatch(1);
        WorkPageSource gated = new WorkPageSource() {
            @Override
            public WorkPage load(int pageIndex, int pageSize) {
                if (pageIndex == 1) {
                    try {
                        gate.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
                return new WorkPage(List.of(), 100);
            }

            @Override
            public boolean removesOnUnfavorite() {
                return false;
            }
        };

        WorkListModel model = new WorkListModel(2);
        fx(() -> {
            model.setSource(gated, true);
            return null;
        });
        await(() -> model.isLoading());

        // 加载期间连续请求翻页，页码应停在第一页
        fx(() -> {
            model.nextPage();
            model.nextPage();
            model.nextPage();
            return null;
        });
        assertEquals(1, model.getPageIndex());

        gate.countDown();
        await(() -> model.isLoaded());
        assertEquals(1, model.getPageIndex());

        // 加载结束后翻页恢复正常
        fx(() -> {
            model.nextPage();
            return null;
        });
        await(() -> model.getPageIndex() == 2);
    }

    private static WorkPageSource source(int total, boolean removesOnUnfavorite) {
        List<AsmrWork> works = new ArrayList<>();
        for (int id = 1; id <= total; id++) {
            AsmrWork work = new AsmrWork();
            work.setId(id);
            work.setTitle("作品 " + id);
            work.setFavorites(true);
            works.add(work);
        }
        return new WorkPageSource() {
            @Override
            public WorkPage load(int pageIndex, int pageSize) {
                int from = Math.min((pageIndex - 1) * pageSize, works.size());
                int to = Math.min(from + pageSize, works.size());
                return new WorkPage(works.subList(from, to), works.size());
            }

            @Override
            public boolean removesOnUnfavorite() {
                return removesOnUnfavorite;
            }
        };
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
        while (System.nanoTime() < deadline) {
            if (fx(condition::getAsBoolean)) {
                return;
            }
            Thread.sleep(20);
        }
        fail("等待列表加载超时");
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(12, TimeUnit.SECONDS);
    }
}
