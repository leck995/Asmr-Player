package cn.tealc.asmr.task.player;

import cn.tealc.asmr.utils.ImageUtils;
import javafx.concurrent.Task;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;

/**
 * 生成播放界面背景：处理传入的封面 {@link Image}，进行高斯模糊和亮度调整。
 * <p>
 * 先将封面缩宽至 100 像素，再依次套用高斯模糊（半径 25）与亮度系数 0.45，
 * 组装为居中自动铺放的 {@link Background}。
 *
 * @author Leck
 */
public class PlayerBackgroundTask extends Task<Background> {

    private static final int WIDTH = 100;
    private static final double BLUR_RADIUS = 25;
    private static final double BRIGHTNESS = 0.45;

    private final Image image;

    public PlayerBackgroundTask(Image image) {
        this.image = image;
    }

    @Override
    protected Background call() {
        WritableImage filtered = ImageUtils.resize(image, WIDTH, Integer.MAX_VALUE);
        ImageUtils.blur(filtered, BLUR_RADIUS);
        ImageUtils.multiplyBrightness(filtered, BRIGHTNESS);
        javafx.scene.layout.BackgroundImage backgroundImage = new javafx.scene.layout.BackgroundImage(
                filtered,
                BackgroundRepeat.NO_REPEAT,
                BackgroundRepeat.NO_REPEAT,
                BackgroundPosition.CENTER,
                new BackgroundSize(BackgroundSize.AUTO, BackgroundSize.AUTO, true, true, true, true));
        return new Background(backgroundImage);
    }
}
