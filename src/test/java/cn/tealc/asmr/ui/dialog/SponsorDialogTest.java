package cn.tealc.asmr.ui.dialog;

import cn.tealc.asmr.ui.component.OverlayLayer;
import cn.tealc.asmr.ui.component.PageHeader;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SponsorDialogTest {

    private static StackPane root;

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException running) {
            Platform.runLater(latch::countDown);
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        fx(() -> {
            Platform.setImplicitExit(false);
            root = new StackPane();
            new OverlayLayer(root);
            return null;
        });
    }

    @BeforeEach
    void setUp() throws Exception {
        fx(() -> {
            root.getChildren().clear();
            return null;
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        fx(() -> {
            root.getChildren().clear();
            return null;
        });
    }

    @Test
    void showsSponsorContentAndQrCode() throws Exception {
        fx(() -> {
            SponsorDialog.show();
            return null;
        });

        JFXDialogLayout layout = fx(() -> {
            Node node = root.lookup(".sponsor-dialog");
            assertNotNull(node, "赞助弹窗应使用 sponsor-dialog 样式类");
            return (JFXDialogLayout) node;
        });

        fx(() -> {
            PageHeader header = (PageHeader) layout.getHeading().getFirst();
            assertEquals("赞助", header.getTitle());
            assertEquals("觉得这个项目不错？欢迎请作者喝一杯咖啡", header.getSubtitle());

            VBox body = (VBox) layout.getBody().getFirst();
            ImageView qrCode = (ImageView) body.lookup(".sponsor-qr");
            assertNotNull(qrCode, "弹窗应展示收款二维码");
            assertNotNull(qrCode.getImage(), "二维码图片应加载成功");
            assertEquals(360, qrCode.getFitWidth(), 0.1);

            Button close = (Button) layout.getActions().getFirst();
            assertEquals("关闭", close.getText());
            assertTrue(close.isCancelButton());
            return null;
        });
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(12, TimeUnit.SECONDS);
    }
}
