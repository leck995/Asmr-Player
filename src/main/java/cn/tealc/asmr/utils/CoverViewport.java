package cn.tealc.asmr.utils;

import javafx.beans.binding.Bindings;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.ImageView;

/**
 * 作品封面以 4:3 等比铺满，超出区域居中裁剪。
 */
public final class CoverViewport {
    private static final double ASPECT_RATIO = 4.0 / 3.0;

    private CoverViewport() {
    }

    /**
     * 让封面铺满 ImageView 指定的区域，超出部分居中裁剪。
     * <p>
     * 这里必须关闭 {@code preserveRatio}：开启时 ImageView 按图片原始比例缩放，
     * 只会“完整容纳”图片，渲染尺寸可能小于设定的 fitWidth/fitHeight，
     * 于是在卡片上方露出左右白边。等比保真由下面的 viewport 裁剪保证，
     * 关闭后图片会精确填满可见区域，不会变形。
     *
     * @param imageView 封面视图，调用前需设置 fitWidth 与 fitHeight
     */
    public static void configure(ImageView imageView) {
        imageView.setPreserveRatio(false);
        // 切换图片时自动解绑旧图片；后台加载产生尺寸后重新计算裁剪区域。
        imageView.viewportProperty().bind(imageView.imageProperty().flatMap(image ->
                Bindings.createObjectBinding(() -> {
                    double width = image.getWidth();
                    double height = image.getHeight();
                    if (width <= 0 || height <= 0) {
                        return null;
                    }
                    double cropWidth = Math.min(width, height * ASPECT_RATIO);
                    double cropHeight = cropWidth / ASPECT_RATIO;
                    return new Rectangle2D((width - cropWidth) / 2, (height - cropHeight) / 2,
                            cropWidth, cropHeight);
                }, image.widthProperty(), image.heightProperty())));
    }
}
