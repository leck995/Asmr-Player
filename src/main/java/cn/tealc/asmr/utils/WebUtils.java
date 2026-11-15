package cn.tealc.asmr.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * 通过系统默认浏览器打开 URL。
 *
 * @author Leck
 */
public class WebUtils {
    private static final Logger LOG = LoggerFactory.getLogger(WebUtils.class);
    /**
     * 调用系统默认浏览器打开指定 URL；打开失败时仅记录日志，不抛出异常。
     *
     * @param url 待打开的完整 URL，需为合法 URI，否则会被捕获并记录
     */
    public static void browser(String url){
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (IOException | URISyntaxException e) {
            LOG.error(e.getMessage());
        }
    }
}