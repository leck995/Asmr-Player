package cn.tealc.asmr.source.openlist;

import cn.tealc.asmr.model.OpenListAccess;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenListClientAuthorizationTest {
    @Test
    void sendsOpenListTokenWithoutBearerPrefix() throws Exception {
        try (ServerSocket server = new ServerSocket(0);
             var executor = Executors.newSingleThreadExecutor()) {
            var authorization = executor.submit(() -> {
                try (var socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    String header = null;
                    String line;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) {
                        if (line.regionMatches(true, 0, "Authorization:", 0, "Authorization:".length())) {
                            header = line.substring("Authorization:".length()).trim();
                        }
                    }
                    byte[] body = "{\"code\":200,\"data\":{\"content\":[]}}".getBytes(StandardCharsets.UTF_8);
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    socket.getOutputStream().write(body);
                    socket.getOutputStream().flush();
                    return header;
                }
            });
            var client = new OpenListClient(new OpenListAccess("http://127.0.0.1:" + server.getLocalPort(), "openlist-test-token"));
            client.listFiles("/");
            assertEquals("openlist-test-token", authorization.get(5, TimeUnit.SECONDS));
        }
    }
}
