package cn.tealc.asmr.utils;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CoverViewportTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        onFxThread(() -> {
            Platform.setImplicitExit(false);
            return null;
        });
    }

    @ParameterizedTest
    @CsvSource({
            "120, 240, 0, 75, 120, 90",
            "320, 120, 80, 0, 160, 120",
            "240, 180, 0, 0, 240, 180",
            "240, 240, 0, 30, 240, 180",
            "40, 400, 0, 185, 40, 30"
    })
    void cropsCenteredFourToThreeRegion(int width, int height, double x, double y,
                                       double cropWidth, double cropHeight) throws Exception {
        onFxThread(() -> {
            ImageView view = new ImageView(new WritableImage(width, height));
            CoverViewport.configure(view);
            assertTrue(view.isPreserveRatio());
            assertEquals(new Rectangle2D(x, y, cropWidth, cropHeight), view.getViewport());
            return null;
        });
    }

    @Test
    void updatesViewportWhenImageIsReplacedOrCleared() throws Exception {
        onFxThread(() -> {
            ImageView view = new ImageView();
            CoverViewport.configure(view);
            assertNull(view.getViewport());
            view.setImage(new WritableImage(120, 240));
            assertEquals(new Rectangle2D(0, 75, 120, 90), view.getViewport());
            view.setImage(new WritableImage(320, 120));
            assertEquals(new Rectangle2D(80, 0, 160, 120), view.getViewport());
            view.setImage(null);
            assertNull(view.getViewport());
            return null;
        });
    }

    @ParameterizedTest
    @CsvSource({"120, 240, 240, 180", "320, 120, 400, 300", "120, 240, 340, 255", "320, 120, 340, 255"})
    void rendersOnlyCroppedRegionAndKeepsRatioWhenResized(int width, int height,
                                                        int fitWidth, int fitHeight) throws Exception {
        onFxThread(() -> {
            WritableImage image = new WritableImage(width, height);
            double cropWidth = Math.min(width, height * 4.0 / 3.0);
            double cropHeight = cropWidth * 3.0 / 4.0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    boolean inside = x >= (width - cropWidth) / 2 && x < (width + cropWidth) / 2
                            && y >= (height - cropHeight) / 2 && y < (height + cropHeight) / 2;
                    image.getPixelWriter().setColor(x, y, inside ? Color.LIME : Color.MAGENTA);
                }
            }
            ImageView view = new ImageView(image);
            CoverViewport.configure(view);
            view.setFitWidth(fitWidth);
            view.setFitHeight(fitHeight);
            assertCroppedSnapshot(view, fitWidth, fitHeight);
            view.setFitWidth(480);
            view.setFitHeight(360);
            assertCroppedSnapshot(view, 480, 360);
            return null;
        });
    }

    @Test
    void followsBoundCoverProperty() throws Exception {
        onFxThread(() -> {
            var cover = new SimpleObjectProperty<Image>();
            ImageView view = new ImageView();
            view.imageProperty().bind(cover);
            view.setFitWidth(340);
            view.setFitHeight(255);
            CoverViewport.configure(view);
            assertNull(view.getViewport());
            cover.set(new WritableImage(120, 240));
            assertEquals(new Rectangle2D(0, 75, 120, 90), view.getViewport());
            assertEquals(340, view.getLayoutBounds().getWidth());
            assertEquals(255, view.getLayoutBounds().getHeight());
            cover.set(new WritableImage(320, 120));
            assertEquals(new Rectangle2D(80, 0, 160, 120), view.getViewport());
            cover.set(null);
            assertNull(view.getViewport());
            return null;
        });
    }

    @Test
    void updatesViewportAfterBackgroundImageLoads() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(120, 240, BufferedImage.TYPE_INT_RGB), "png", output);
        byte[] png = output.toByteArray();
        CountDownLatch releaseResponse = new CountDownLatch(1);
        try (ServerSocket server = new ServerSocket();
             var executor = Executors.newSingleThreadExecutor()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            server.setSoTimeout(10000);
            var response = executor.submit(() -> {
                try (var socket = server.accept()) {
                    socket.setSoTimeout(10000);
                    var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    while (true) {
                        String line = reader.readLine();
                        if (line == null || line.isEmpty()) {
                            break;
                        }
                    }
                    assertTrue(releaseResponse.await(10, TimeUnit.SECONDS));
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: "
                            + png.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().write(png);
                    socket.getOutputStream().flush();
                    return null;
                }
            });
            CompletableFuture<Void> loaded = new CompletableFuture<>();
            ImageView view = onFxThread(() -> {
                Image image = new Image("http://127.0.0.1:" + server.getLocalPort() + "/cover.png", true);
                ImageView result = new ImageView(image);
                CoverViewport.configure(result);
                assertNull(result.getViewport());
                image.progressProperty().addListener((observable, before, progress) -> {
                    if (progress.doubleValue() == 1) {
                        loaded.complete(null);
                    }
                });
                return result;
            });
            releaseResponse.countDown();
            response.get(10, TimeUnit.SECONDS);
            loaded.get(10, TimeUnit.SECONDS);
            onFxThread(() -> {
                assertFalse(view.getImage().isError());
                assertEquals(new Rectangle2D(0, 75, 120, 90), view.getViewport());
                return null;
            });
        } finally {
            releaseResponse.countDown();
        }
    }

    private static void assertCroppedSnapshot(ImageView view, int width, int height) {
        WritableImage snapshot = view.snapshot(null, null);
        assertEquals(width, snapshot.getWidth());
        assertEquals(height, snapshot.getHeight());
        for (int y : new int[]{10, height / 2, height - 10}) {
            for (int x : new int[]{10, width / 2, width - 10}) {
                assertEquals(Color.LIME, snapshot.getPixelReader().getColor(x, y));
            }
        }
    }

    private static <T> T onFxThread(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
