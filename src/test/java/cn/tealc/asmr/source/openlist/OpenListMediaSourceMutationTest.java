package cn.tealc.asmr.source.openlist;

import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.model.OpenListAccess;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenList 写操作请求体测试。
 * <p>
 * 用本地 HTTP 服务模拟 OpenList，校验 delete/rename/move 实际发送的字段，
 * 以及根级条目、目标根护栏和 not found 判断。
 */
class OpenListMediaSourceMutationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ServerSocket server;
    private ExecutorService executor;
    private Future<Request> pending;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = new ServerSocket(0);
        executor = Executors.newSingleThreadExecutor();
        pending = executor.submit(this::serveOne);
        baseUrl = "http://127.0.0.1:" + server.getLocalPort();
    }

    @AfterEach
    void stopServer() throws IOException {
        server.close();
        executor.shutdownNow();
    }

    private OpenListMediaSource source() {
        return new OpenListMediaSource(new OpenListAccess(baseUrl, "test-token"));
    }

    @Test
    void deleteRootLevelFileUsesRootAsParentDirectory() throws Exception {
        assertTrue(source().delete("/foo"));

        Request request = awaitRequest();
        assertEquals("/api/fs/remove", request.path());
        assertNotNull(request.body());
        assertEquals("/", request.body().get("dir").asText());
        assertEquals("foo", request.body().get("names").get(0).asText());
    }

    @Test
    void deleteNestedFileUsesParentDirectory() throws Exception {
        assertTrue(source().delete("/a/b"));

        Request request = awaitRequest();
        assertEquals("/a", request.body().get("dir").asText());
        assertEquals("b", request.body().get("names").get(0).asText());
    }

    @Test
    void renameSendsFullPathAndNewName() throws Exception {
        assertTrue(source().rename("/a/b.mp3", "c.mp3"));

        Request request = awaitRequest();
        assertEquals("/api/fs/rename", request.path());
        assertEquals("/a/b.mp3", request.body().get("path").asText());
        assertEquals("c.mp3", request.body().get("name").asText());
    }

    @Test
    void moveRootLevelEntryUsesRootAsSourceDirectory() throws Exception {
        assertTrue(source().move("/foo", "/bar"));

        Request request = awaitRequest();
        assertEquals("/api/fs/move", request.path());
        assertEquals("/", request.body().get("src_dir").asText());
        assertEquals("/bar", request.body().get("dst_dir").asText());
        assertEquals("foo", request.body().get("names").get(0).asText());
    }

    @Test
    void existsTreatsNotFoundAsFalse() throws Exception {
        assertFalse(source().exists("/missing"));

        Request request = awaitRequest();
        assertEquals("/api/fs/get", request.path());
    }

    @Test
    void listFilesCarriesThumbnailUrl() throws Exception {
        AsmrFile file = source().listFiles("/dir").getFirst();

        assertEquals("a.jpg", file.getFilename());
        assertEquals("https://thumb.example/a.jpg", file.getThumbnailUrl());
        assertEquals("/api/fs/list", awaitRequest().path());
    }

    @Test
    void resolveThumbnailUrlUsesFileInfo() throws Exception {
        assertEquals("https://thumb.example/a.jpg", source().resolveThumbnailUrl("/thumb/a.jpg"));
        assertEquals("/api/fs/get", awaitRequest().path());
    }

    @Test
    void rejectsRootDirectoryAndInvalidNames() {
        OpenListMediaSource source = source();
        assertThrows(IllegalArgumentException.class, () -> source.delete("/"));
        assertThrows(IllegalArgumentException.class, () -> source.rename("/a/b", "c/d"));
        assertThrows(IllegalArgumentException.class, () -> source.rename("/a/b", ".."));
    }

    @Test
    void guardsConfiguredTargetRoot() throws Exception {
        OpenListMediaSource guarded = new OpenListMediaSource(null,
                new OpenListAccess(baseUrl, "test-token"), "/115");

        assertThrows(IllegalArgumentException.class, () -> guarded.delete("/115"));
        assertFalse(pending.isDone());

        assertTrue(guarded.delete("/115/RJ000001"));
        Request request = awaitRequest();
        assertEquals("/115", request.body().get("dir").asText());
    }

    private Request awaitRequest() throws Exception {
        return pending.get(5, TimeUnit.SECONDS);
    }

    private Request serveOne() throws IOException {
        try (Socket socket = server.accept()) {
            socket.setSoTimeout(5000);
            InputStream input = socket.getInputStream();
            String requestLine = readLine(input);
            int contentLength = 0;
            String line;
            while (!(line = readLine(input)).isEmpty()) {
                if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
                }
            }
            byte[] body = input.readNBytes(contentLength);
            String path = requestLine.split(" ")[1];
            JsonNode json = body.length == 0 ? null : MAPPER.readTree(body);
            String response = responseFor(path, json);
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            OutputStream output = socket.getOutputStream();
            output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
                    + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(bytes);
            output.flush();
            return new Request(path, json);
        }
    }

    private static String responseFor(String path, JsonNode body) {
        if ("/api/fs/list".equals(path)) {
            return "{\"code\":200,\"message\":\"success\",\"data\":{\"content\":["
                    + "{\"name\":\"a.jpg\",\"size\":123,\"is_dir\":false,"
                    + "\"thumb\":\"https://thumb.example/a.jpg\","
                    + "\"modified\":\"2026-01-01T00:00:00+08:00\"}],\"total\":1}}";
        }
        if ("/api/fs/get".equals(path)) {
            String requested = body == null ? "" : body.path("path").asText();
            if ("/missing".equals(requested)) {
                return "{\"code\":500,\"message\":\"object not found\",\"data\":null}";
            }
            return "{\"code\":200,\"message\":\"success\",\"data\":{"
                    + "\"name\":\"a.jpg\",\"size\":123,\"is_dir\":false,"
                    + "\"thumb\":\"https://thumb.example/a.jpg\","
                    + "\"modified\":\"2026-01-01T00:00:00+08:00\"}}";
        }
        return "{\"code\":200,\"message\":\"success\",\"data\":null}";
    }

    private static String readLine(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) != -1) {
            if (value == '\n') {
                break;
            }
            if (value != '\r') {
                buffer.write(value);
            }
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private record Request(String path, JsonNode body) {
    }
}
