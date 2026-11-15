/*
 * Copyright (C) 2022 Parisi Alessandro
 * This file is part of MaterialFX (https://github.com/palexdev/MaterialFX).
 *
 * MaterialFX is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MaterialFX is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with MaterialFX.  If not, see <http://www.gnu.org/licenses/>.
 */

package cn.tealc.asmr.utils;

import java.io.InputStream;
import java.net.URL;


/**
 * classpath 资源加载工具，统一从 {@code /cn/tealc/asmr/} 根目录解析 URL、外部表单与输入流。
 * <p>
 * 相对路径会自动补全前缀；资源不存在时 {@link Class#getResource} 返回 null，
 * 依赖 {@code toExternalForm}/{@code toString} 的方法会随之抛出 {@link NullPointerException}。
 */
public class FXResourcesLoader {

    private final static String basePath = "/cn/tealc/asmr/%s/%s";

    private FXResourcesLoader() {
    }

    /**
     * 将相对路径补全为 classpath 资源 URL，绝对路径（以 {@code /} 开头）原样使用。
     */
    private static String normalize(String path) {
        return path.startsWith("/") ? path : "/cn/tealc/asmr/" + path;
    }

    /**
     * 加载 classpath 资源并返回其 URL。
     *
     * @param path 资源路径，相对路径会自动补全 {@code /cn/tealc/asmr/} 前缀
     * @return 资源 URL；资源不存在时返回 null
     */
    public static URL loadURL(String path) {
        return FXResourcesLoader.class.getResource(normalize(path));
    }

    /**
     * 加载 classpath 资源并返回其外部表单字符串（如 {@code file:...} 或 {@code jar:...}）。
     *
     * @param path 资源路径，相对路径自动补全前缀
     * @return 外部表单 URL；资源不存在时抛出 {@link NullPointerException}
     */
    public static String load(String path) {
        return loadURL(path).toExternalForm();
    }

    /**
     * 与 {@link #load(String)} 行为完全相同，加载资源并返回外部表单字符串。
     *
     * @param path 资源路径，相对路径自动补全前缀
     * @return 外部表单 URL；资源不存在时抛出 {@link NullPointerException}
     */
    public static String loadStr(String path) {
        return loadURL(path).toExternalForm();
    }

    /**
     * 以输入流形式加载 classpath 资源。
     *
     * @param name 资源路径，相对路径自动补全前缀
     * @return 资源输入流；资源不存在时返回 null
     */
    public static InputStream loadStream(String name) {
        return FXResourcesLoader.class.getResourceAsStream(normalize(name));
    }


    /**
     * 加载 {@code /cn/tealc/asmr/image/} 目录下的图片，返回其 URL 字符串。
     *
     * @param path 相对于 image 目录的图片路径
     * @return 图片 URL 字符串；资源不存在时抛出 {@link NullPointerException}
     */
    public static String image(String path) {
        String image = String.format(basePath, "image", path);
        return loadURL(image).toString();
    }

    /**
     * 加载 {@code /cn/tealc/asmr/css/} 目录下的样式表，返回其 URL 字符串。
     *
     * @param path 相对于 css 目录的样式表路径
     * @return 样式表 URL 字符串；资源不存在时抛出 {@link NullPointerException}
     */
    public static String css(String path) {
        String css = String.format(basePath, "css", path);
        return loadURL(css).toString();
    }
}
