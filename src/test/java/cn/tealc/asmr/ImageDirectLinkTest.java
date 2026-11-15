package cn.tealc.asmr;

import cn.tealc.asmr.model.OpenListAccess;
import cn.tealc.asmr.source.openlist.OpenListClient;
import cn.tealc.asmr.source.openlist.model.OpenListFile;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * 测试 OpenList 图片通过 raw_url 直链加载。
 * <p>
 * 使用与 OpenListClient 相同的 HttpClient 配置（含代理），下载 raw_url → new Image(InputStream)。
 *
 * @author Leck
 */
public class ImageDirectLinkTest extends Application {

    private static final String IMAGE_PATH = "/115(open)/秋名山/音声/Temp9/RJ01145252/附赠/高清封面无logo.jpg";
    private static final String IMAGE_DIR = "/115(open)/秋名山/音声/Temp9/RJ01145252/附赠";

    private String rawUrl;
    private HttpClient httpClient;

    @Override
    public void init() throws Exception {
        System.out.println("=== 初始化 ===");
        String baseUrl = System.getProperty("asmr.openlist.url");
        String token = System.getProperty("asmr.openlist.token");
        if (baseUrl == null || baseUrl.isBlank() || token == null || token.isBlank()) {
            throw new IllegalStateException("请通过 asmr.openlist.url 和 asmr.openlist.token 指定 OpenList 存储源");
        }
        OpenListClient client = new OpenListClient(new OpenListAccess(baseUrl, token));

        // 构造与 OpenListClient 内部 httpClient 相同配置的 HttpClient
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5));
        if (cn.tealc.asmr.config.Config.setting.proxyModel.get()) {
            String host = cn.tealc.asmr.config.Config.setting.proxyHost.get();
            String port = cn.tealc.asmr.config.Config.setting.proxyPort.get();
            if (host != null && port != null && !host.isBlank() && !port.isBlank()) {
                builder.proxy(ProxySelector.of(new InetSocketAddress(host, Integer.parseInt(port))));
                System.out.println("使用代理: " + host + ":" + port);
            }
        }
        httpClient = builder.build();

        System.out.println("列目录: " + IMAGE_DIR);
        List<OpenListFile> files = client.listFiles(IMAGE_DIR);
        System.out.println("文件数: " + files.size());

        for (OpenListFile f : files) {
            System.out.println("  " + f.getName() + " (sign=" + f.getSign() + ")");
        }

        // getPlayUrl 内部调用 getFileInfo，刚签发的 raw_url 时效内可用
        rawUrl = client.getPlayUrl(IMAGE_PATH);
        System.out.println("\nraw_url: " + rawUrl);
    }

    @Override
    public void start(Stage stage) {
        VBox root = new VBox(10);

        Label statusLabel = new Label("正在下载...");
        ImageView imageView = new ImageView();
        imageView.setFitWidth(600);
        imageView.setFitHeight(500);
        imageView.setPreserveRatio(true);

        root.getChildren().addAll(statusLabel, imageView);

        System.out.println("\n开始下载 raw_url:");
        System.out.println("  URL: " + rawUrl);

        Thread.startVirtualThread(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(rawUrl))
                        .timeout(Duration.ofSeconds(10))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .header("Accept", "image/*,*/*;q=0.8")
                        .GET()
                        .build();

                HttpResponse<byte[]> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofByteArray());

                System.out.println("  HTTP 状态码: " + response.statusCode());

                if (response.statusCode() == 200) {
                    byte[] bytes = response.body();
                    System.out.println("  下载成功: " + bytes.length + " bytes");
                    System.out.println("  Content-Type: " + response.headers().firstValue("Content-Type"));

                    Platform.runLater(() -> {
                        java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(bytes);
                        Image image = new Image(bais);
                        if (image.isError()) {
                            System.err.println("  Image 解析失败");
                            statusLabel.setText("Image 解析失败");
                        } else {
                            System.out.println("  Image 成功: " + image.getWidth() + "x" + image.getHeight());
                            statusLabel.setText("加载成功 " + (int)image.getWidth() + "x" + (int)image.getHeight());
                        }
                        imageView.setImage(image);
                    });
                } else {
                    String error = new String(response.body(), java.nio.charset.StandardCharsets.UTF_8);
                    System.err.println("  下载失败 HTTP " + response.statusCode() + ": " + error);
                    Platform.runLater(() -> statusLabel.setText("HTTP " + response.statusCode() + " 失败"));
                }
            } catch (Exception e) {
                System.err.println("  下载异常: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> statusLabel.setText("异常: " + e.getMessage()));
            }
        });

        Scene scene = new Scene(root, 700, 600);
        stage.setTitle("图片直链测试");
        stage.setScene(scene);
        stage.show();
    }
}
