package cn.tealc.asmr.ui.guide;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * 从 classpath 读取内置教程内容。
 *
 * @author Leck
 */
public final class GuideContentLoader {

    private static final String RESOURCE = "/cn/tealc/asmr/guide/guide.json";

    private GuideContentLoader() {
    }

    /**
     * 读取并解析教程 JSON。
     *
     * @throws IllegalStateException 资源缺失
     * @throws UncheckedIOException  JSON 读取或解析失败
     */
    public static GuideContent load() {
        try (InputStream in = GuideContentLoader.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("找不到教程内容文件：" + RESOURCE);
            }
            return new ObjectMapper().readValue(in, GuideContent.class);
        } catch (IOException e) {
            throw new UncheckedIOException("读取教程内容失败：" + RESOURCE, e);
        }
    }
}
