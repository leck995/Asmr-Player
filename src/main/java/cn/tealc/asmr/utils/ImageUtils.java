package cn.tealc.asmr.utils;

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

/**
 * 纯 JavaFX 像素级图像缩放与滤镜，避免引入额外的图像处理库。
 * <p>
 * 缩放采用逐级减半加双线性采样，避免大比例缩小产生锯齿；模糊采用可分离高斯核
 * （sigma = radius / 3，与原 jhlabs 实现一致），只处理缩略后的少量像素。
 *
 * @author Leck
 */
public final class ImageUtils {

    private ImageUtils() {
    }

    /**
     * 等比缩放到不超过 {@code maxWidth} × {@code maxHeight}。
     *
     * @param source    源图
     * @param maxWidth  目标最大宽度
     * @param maxHeight 目标最大高度
     * @return 缩放后的图像
     * @throws IllegalArgumentException 源图或目标尺寸无效
     */
    public static WritableImage resize(Image source, int maxWidth, int maxHeight) {
        int sourceWidth = (int) Math.round(source.getWidth());
        int sourceHeight = (int) Math.round(source.getHeight());
        if (sourceWidth <= 0 || sourceHeight <= 0 || maxWidth <= 0 || maxHeight <= 0) {
            throw new IllegalArgumentException(
                    "图像尺寸无效: " + sourceWidth + "x" + sourceHeight + " -> " + maxWidth + "x" + maxHeight);
        }
        double scale = Math.min(maxWidth / (double) sourceWidth, maxHeight / (double) sourceHeight);
        int width = Math.max(1, (int) Math.round(sourceWidth * scale));
        int height = Math.max(1, (int) Math.round(sourceHeight * scale));

        int[] pixels = readPixels(source, sourceWidth, sourceHeight);
        int currentWidth = sourceWidth;
        int currentHeight = sourceHeight;
        while (currentWidth >= width * 2 && currentHeight >= height * 2) {
            int nextWidth = Math.max(width, (currentWidth + 1) / 2);
            int nextHeight = Math.max(height, (currentHeight + 1) / 2);
            pixels = resample(pixels, currentWidth, currentHeight, nextWidth, nextHeight);
            currentWidth = nextWidth;
            currentHeight = nextHeight;
        }
        if (currentWidth != width || currentHeight != height) {
            pixels = resample(pixels, currentWidth, currentHeight, width, height);
        }
        return toImage(pixels, width, height);
    }

    /**
     * 原地高斯模糊。
     *
     * @param image  待处理图像
     * @param radius 模糊半径，{@code <= 0} 时不做处理
     */
    public static void blur(WritableImage image, double radius) {
        if (radius <= 0) {
            return;
        }
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        int[] pixels = readPixels(image, width, height);
        gaussianBlur(pixels, width, height, radius);
        writePixels(image, width, height, pixels);
    }

    /**
     * 原地调整亮度：各颜色通道乘以给定系数，alpha 保持不变。
     *
     * @param image  待处理图像
     * @param factor 亮度系数，{@code 1.0} 时不做处理
     */
    public static void multiplyBrightness(WritableImage image, double factor) {
        if (factor == 1.0) {
            return;
        }
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        int[] pixels = readPixels(image, width, height);
        int[] lut = new int[256];
        for (int i = 0; i < lut.length; i++) {
            lut[i] = (int) Math.min(255, Math.round(i * factor));
        }
        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            pixels[i] = (argb & 0xFF000000)
                    | (lut[(argb >> 16) & 0xFF] << 16)
                    | (lut[(argb >> 8) & 0xFF] << 8)
                    | lut[argb & 0xFF];
        }
        writePixels(image, width, height, pixels);
    }

    private static int[] readPixels(Image image, int width, int height) {
        int[] pixels = new int[width * height];
        PixelReader reader = image.getPixelReader();
        reader.getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        return pixels;
    }

    private static void writePixels(WritableImage image, int width, int height, int[] pixels) {
        PixelWriter writer = image.getPixelWriter();
        writer.setPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
    }

    private static WritableImage toImage(int[] pixels, int width, int height) {
        WritableImage image = new WritableImage(width, height);
        writePixels(image, width, height, pixels);
        return image;
    }

    private static int[] resample(int[] source, int sourceWidth, int sourceHeight, int width, int height) {
        int[] target = new int[width * height];
        double xScale = sourceWidth / (double) width;
        double yScale = sourceHeight / (double) height;
        for (int y = 0; y < height; y++) {
            double sourceY = (y + 0.5) * yScale - 0.5;
            int y0 = (int) Math.floor(sourceY);
            double fy = sourceY - y0;
            int row0 = clamp(y0, 0, sourceHeight - 1) * sourceWidth;
            int row1 = clamp(y0 + 1, 0, sourceHeight - 1) * sourceWidth;
            int targetRow = y * width;
            for (int x = 0; x < width; x++) {
                double sourceX = (x + 0.5) * xScale - 0.5;
                int x0 = (int) Math.floor(sourceX);
                double fx = sourceX - x0;
                int column0 = clamp(x0, 0, sourceWidth - 1);
                int column1 = clamp(x0 + 1, 0, sourceWidth - 1);
                target[targetRow + x] = bilinear(
                        source[row0 + column0], source[row0 + column1],
                        source[row1 + column0], source[row1 + column1],
                        fx, fy);
            }
        }
        return target;
    }

    private static int bilinear(int topLeft, int topRight, int bottomLeft, int bottomRight,
                                double fx, double fy) {
        double topLeftWeight = (1 - fx) * (1 - fy);
        double topRightWeight = fx * (1 - fy);
        double bottomLeftWeight = (1 - fx) * fy;
        double bottomRightWeight = fx * fy;
        int alpha = channel(topLeft >>> 24, topRight >>> 24, bottomLeft >>> 24, bottomRight >>> 24,
                topLeftWeight, topRightWeight, bottomLeftWeight, bottomRightWeight);
        int red = channel(topLeft >> 16, topRight >> 16, bottomLeft >> 16, bottomRight >> 16,
                topLeftWeight, topRightWeight, bottomLeftWeight, bottomRightWeight);
        int green = channel(topLeft >> 8, topRight >> 8, bottomLeft >> 8, bottomRight >> 8,
                topLeftWeight, topRightWeight, bottomLeftWeight, bottomRightWeight);
        int blue = channel(topLeft, topRight, bottomLeft, bottomRight,
                topLeftWeight, topRightWeight, bottomLeftWeight, bottomRightWeight);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int channel(int topLeft, int topRight, int bottomLeft, int bottomRight,
                               double topLeftWeight, double topRightWeight,
                               double bottomLeftWeight, double bottomRightWeight) {
        return (int) Math.round((topLeft & 0xFF) * topLeftWeight
                + (topRight & 0xFF) * topRightWeight
                + (bottomLeft & 0xFF) * bottomLeftWeight
                + (bottomRight & 0xFF) * bottomRightWeight);
    }

    private static void gaussianBlur(int[] pixels, int width, int height, double radius) {
        int kernelRadius = (int) Math.ceil(radius);
        float[] kernel = new float[kernelRadius * 2 + 1];
        double sigma = radius / 3.0;
        double sigmaSquared = 2 * sigma * sigma;
        double normalization = 1.0 / Math.sqrt(2 * Math.PI * sigma * sigma);
        double total = 0;
        for (int i = -kernelRadius; i <= kernelRadius; i++) {
            double value = Math.exp(-(i * i) / sigmaSquared) * normalization;
            kernel[i + kernelRadius] = (float) value;
            total += value;
        }
        for (int i = 0; i < kernel.length; i++) {
            kernel[i] /= (float) total;
        }

        int[] horizontal = new int[pixels.length];
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                float alpha = 0;
                float red = 0;
                float green = 0;
                float blue = 0;
                for (int k = -kernelRadius; k <= kernelRadius; k++) {
                    int argb = pixels[row + clamp(x + k, 0, width - 1)];
                    float weight = kernel[k + kernelRadius];
                    alpha += ((argb >>> 24) & 0xFF) * weight;
                    red += ((argb >> 16) & 0xFF) * weight;
                    green += ((argb >> 8) & 0xFF) * weight;
                    blue += (argb & 0xFF) * weight;
                }
                horizontal[row + x] = ((int) alpha << 24) | ((int) red << 16) | ((int) green << 8) | (int) blue;
            }
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float alpha = 0;
                float red = 0;
                float green = 0;
                float blue = 0;
                for (int k = -kernelRadius; k <= kernelRadius; k++) {
                    int argb = horizontal[clamp(y + k, 0, height - 1) * width + x];
                    float weight = kernel[k + kernelRadius];
                    alpha += ((argb >>> 24) & 0xFF) * weight;
                    red += ((argb >> 16) & 0xFF) * weight;
                    green += ((argb >> 8) & 0xFF) * weight;
                    blue += (argb & 0xFF) * weight;
                }
                pixels[y * width + x] =
                        ((int) alpha << 24) | ((int) red << 16) | ((int) green << 8) | (int) blue;
            }
        }
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }
}
