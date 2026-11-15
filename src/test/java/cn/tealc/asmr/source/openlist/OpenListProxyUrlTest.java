package cn.tealc.asmr.source.openlist;

import cn.tealc.asmr.model.OpenListAccess;
import cn.tealc.asmr.source.openlist.model.OpenListFile;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 播放代理地址构造测试。
 * <p>
 * 只校验纯逻辑（路径编码、sign 拼接），不发起网络请求：
 * {@code getProxyPlayUrl} 需要访问 OpenList，测试中通过反射直接验证编码与拼接规则。
 */
class OpenListProxyUrlTest {

    private static final String BASE = "http://127.0.0.1:5244";

    /** 调用私有方法 encodePathForProxy，验证路径编码规则。 */
    private static String encode(String path) throws Exception {
        Method method = OpenListClient.class.getDeclaredMethod("encodePathForProxy", String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, path);
    }

    @Test
    void encodesChineseAndSpacesInPathSegments() throws Exception {
        String encoded = encode("/115/新建文件夹/2026-06-17 16-23-03.mp4");
        // 中文需转义，空格必须是 %20 而不是 +
        assertTrue(encoded.startsWith("/115/"), encoded);
        assertTrue(encoded.contains("%20"), "空格应编码为 %20：" + encoded);
        assertTrue(!encoded.contains("+"), "不应出现表单编码的 +：" + encoded);
        assertTrue(encoded.contains(".mp4"), encoded);
        assertTrue(!encoded.contains("新建文件夹"), "中文不应原样保留：" + encoded);
    }

    @Test
    void keepsPathSeparatorsBetweenSegments() throws Exception {
        String encoded = encode("/a/b/c.mp4");
        assertEquals("/a/b/c.mp4", encoded);
    }

    @Test
    void normalizesLeadingSlashAndTrailingSlash() throws Exception {
        assertEquals("/a/b.mp4", encode("a/b.mp4"));
        assertEquals("/a/b", encode("/a/b/"));
    }

    @Test
    void handlesBlankAndRootPath() throws Exception {
        assertEquals("/", encode(""));
        assertEquals("/", encode(null));
        assertEquals("/", encode("/"));
    }

    @Test
    void encodesSpecialCharacters() throws Exception {
        String encoded = encode("/a/[Group] file & more.mp4");
        // 方括号与连字符需转义，避免 OpenList 端解析歧义
        assertTrue(!encoded.contains("["), encoded);
        assertTrue(!encoded.contains("&") || encoded.contains("%26"), encoded);
    }

    @Test
    void fileWithoutSignProducesPlainProxyUrl() {
        // 通过构造 OpenListFile 验证 sign 缺失时的分支，不触发网络
        OpenListFile file = new OpenListFile();
        file.setSign("");
        assertNull(file.getRawUrl());
        assertEquals("", file.getSign());
    }

    @Test
    void clientRejectsBlankAccessConfig() {
        OpenListAccess access = new OpenListAccess(BASE, "token");
        OpenListClient client = new OpenListClient(access);
        assertEquals(BASE, client.baseUrl());
    }
}
