package cn.tealc.asmr.utils;


import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.service.WorkService;
import javafx.scene.image.Image;

import java.io.File;

/**
 * 作品封面与背景图片的加载工具。
 * <p>
 * 按作品目录路径加载封面：优先读取 {@link CoverUtils} 的应用目录缓存，其次查找目录内的
 * {@code cover-main.*}/{@code cover-thumb.*} 文件，均不存在时回退到内置占位图 {@code album.jpg}。
 *
 * @author Leck
 */
public class ImageLoadUtils {

    /**
     * 加载默认的背景图片。
     *
     * @return 默认背景的 URI，没有设置时返回 null
     */
    public static String getDefaultBackground() {
        File file = AppPaths.backgroundFile();
        if (file.exists()) {
            return file.toURI().toString();
        } else {
            return null;
        }
    }

    /**
     * 加载作品主封面，尺寸不限。
     * <p>
     * 先查 {@link CoverUtils} 的 {@code main} 缓存，未命中再依次查找目录内的
     * {@code cover-main.png/.jpg/.jpeg}。
     *
     * @param url 作品目录路径，可为 null
     * @return 主封面图片；{@code url} 为 null 或找不到文件时返回占位图 {@code album.jpg}
     */
    public static Image getCoverImage(String url) {

        if (url == null) {
            return new Image(FXResourcesLoader.load("/cn/tealc/asmr/image/album.jpg"), true);
        }
        AsmrWork work = DI.get(WorkService.class).getByFilepath(url);
        if (work != null && work.getWorkId() != null) {
            Image cached = CoverUtils.getFromAppDir(work.getWorkId(), "main", 0, 0, true);
            if (cached != null) {
                return cached;
            }
        }

        File imageFile1 = new File(url + File.separator + "cover-main.png");
        System.out.println(imageFile1.getPath());
        if (imageFile1.exists()) {
            return new Image("file:" + imageFile1.toURI().getPath(), true);
        } else {
            imageFile1 = new File(url + File.separator + "cover-main.jpg");
            if (imageFile1.exists())
                return new Image("file:" + imageFile1.toURI().getPath(), true);
            else {
                imageFile1 = new File(url + File.separator + "cover-main.jpeg");
                if (imageFile1.exists())
                    return new Image("file:" + imageFile1.toURI().getPath(), true);
                else
                    return new Image(FXResourcesLoader.load("/cn/tealc/asmr/image/album.jpg"), true);
            }
        }
    }


    /**
     * 加载作品缩略封面，固定为 100x100。
     * <p>
     * 先查 {@link CoverUtils} 的 {@code thumb} 缓存，未命中再依次查找目录内的
     * {@code cover-thumb.png/.jpg/.jpeg}。
     *
     * @param url 作品目录路径，可为 null
     * @return 缩略封面图片；{@code url} 为 null 或找不到文件时返回占位图 {@code album.jpg}
     */
    public static Image getThumbCoverImage(String url) {

        int defaultSize = 100;
        if (url == null) {
            return new Image(FXResourcesLoader.load("/cn/tealc/asmr/image/album.jpg"), defaultSize, defaultSize, false, true);
        }
        AsmrWork work = DI.get(WorkService.class).getByFilepath(url);
        if (work != null && work.getWorkId() != null) {
            Image cached = CoverUtils.getFromAppDir(work.getWorkId(), "thumb", defaultSize, defaultSize, true);
            if (cached != null) {
                return cached;
            }
        }

        File imageFile1 = new File(url + File.separator + "cover-thumb.png");
        System.out.println(imageFile1.getPath());
        if (imageFile1.exists()) {
            return new Image("file:" + imageFile1.toURI().getPath(), defaultSize, defaultSize, true, true, true);
        } else {
            imageFile1 = new File(url + File.separator + "cover-thumb.jpg");
            if (imageFile1.exists())
                return new Image("file:" + imageFile1.toURI().getPath(), defaultSize, defaultSize, true, true, true);
            else {
                imageFile1 = new File(url + File.separator + "cover-thumb.jpeg");
                if (imageFile1.exists())
                    return new Image("file:" + imageFile1.toURI().getPath(), defaultSize, defaultSize, true, true, true);
                else
                    return new Image(FXResourcesLoader.load("/cn/tealc/asmr/image/album.jpg"), defaultSize, defaultSize, false, true);
            }
        }
    }
}
