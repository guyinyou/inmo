package com.example.inmoair2;

import android.media.Image;

import java.nio.ByteBuffer;

public class YuvToRGB {
    public static byte[] ConvertYUV420SemiPlanarToRGBA_tmpPixels = null;
    public static int ConvertYUV420SemiPlanarToRGBA_tmpPixels_width = -1;
    public static int ConvertYUV420SemiPlanarToRGBA_tmpPixels_height = -1;

    public static byte[] ConvertYUV420SemiPlanarToRGBA(byte[] yuvData, int width, int height) {
        if (ConvertYUV420SemiPlanarToRGBA_tmpPixels == null || ConvertYUV420SemiPlanarToRGBA_tmpPixels.length != width * height) {
            ConvertYUV420SemiPlanarToRGBA_tmpPixels_width = width;
            ConvertYUV420SemiPlanarToRGBA_tmpPixels_height = height;
            ConvertYUV420SemiPlanarToRGBA_tmpPixels = new byte[width * height * 4];
        }

        int yIndex = 0;
        int uIndex = width * height;
        int vIndex = width * height * 5 / 4;

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int y = yuvData[yIndex++] & 0xff;
                int u = yuvData[uIndex + (i / 2) * (width / 2) + (j / 2)] & 0xff;
                int v = yuvData[vIndex + (i / 2) * (width / 2) + (j / 2)] & 0xff;

                // YUV420SemiPlanar 数据转换为 RGB
                int c = y - 16;
                int d = u - 128;
                int e = v - 128;

                int r = (298 * c + 409 * e + 128) >> 8;
                int g = (298 * c - 100 * d - 208 * e + 128) >> 8;
                int b = (298 * c + 516 * d + 128) >> 8;

                r = clamp(r, 0, 255);
                g = clamp(g, 0, 255);
                b = clamp(b, 0, 255);

                int pos = (i * width + j) * 4;
                ConvertYUV420SemiPlanarToRGBA_tmpPixels[pos] = (byte) r;
                ConvertYUV420SemiPlanarToRGBA_tmpPixels[pos + 1] = (byte) g;
                ConvertYUV420SemiPlanarToRGBA_tmpPixels[pos + 2] = (byte) b;
                ConvertYUV420SemiPlanarToRGBA_tmpPixels[pos + 3] = (byte) 255;
            }
        }
        return ConvertYUV420SemiPlanarToRGBA_tmpPixels;
    }

    public static byte[] ConvertImageToYUV420_tmpPixels = null;
    public static byte[] imageToYuv420ByteArray(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int width = image.getWidth();
        int height = image.getHeight();

        // 计算 YUV420 数据的大小
        int ySize = width * height;
        int uvSize = (width / 2) * (height / 2);
        int yuv420Size = ySize + 2 * uvSize;

        // 创建一个新的 byte 数组来保存 YUV420 数据
        if (ConvertImageToYUV420_tmpPixels == null || ConvertImageToYUV420_tmpPixels.length != yuv420Size) {
            ConvertImageToYUV420_tmpPixels = new byte[yuv420Size];
        }

        // 获取每个平面的步长
        int yRowStride = planes[0].getRowStride();
        int uvRowStride = planes[1].getRowStride();
        int uvPixelStride = planes[1].getPixelStride();

        // 读取 Y 分量
        for (int row = 0; row < height; row++) {
            yBuffer.position(row * yRowStride);
            yBuffer.get(ConvertImageToYUV420_tmpPixels, row * width, width);
        }

        // 读取 U 分量
        for (int row = 0; row < height / 2; row++) {
            for (int col = 0; col < width / 2; col++) {
                int index = ySize + (row * (width / 2) + col);
                uBuffer.position((row * uvRowStride) + (col * uvPixelStride));
                ConvertImageToYUV420_tmpPixels[index] = uBuffer.get();
            }
        }

        // 读取 V 分量
        for (int row = 0; row < height / 2; row++) {
            for (int col = 0; col < width / 2; col++) {
                int index = ySize + uvSize + (row * (width / 2) + col);
                vBuffer.position((row * uvRowStride) + (col * uvPixelStride));
                ConvertImageToYUV420_tmpPixels[index] = vBuffer.get();
            }
        }

        return ConvertImageToYUV420_tmpPixels;
    }

    public static byte[] ConvertImageToRGBA888_tmpPixels = null;
    public static byte[] imageToRgba888ByteArray(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int width = image.getWidth();
        int height = image.getHeight();

        int size = width * height * 4;
        // 创建一个新的 byte 数组来保存 YUV420 数据
        if (ConvertImageToRGBA888_tmpPixels == null || ConvertImageToRGBA888_tmpPixels.length != size) {
            ConvertImageToRGBA888_tmpPixels = new byte[size];
        }

        // 裁剪到指定分辨率
        buffer.get(ConvertImageToRGBA888_tmpPixels);

        return ConvertImageToRGBA888_tmpPixels;
    }

    public static byte[] rgbaToYuv420(byte[] rgba, int width, int height) {
        // 计算 YUV420 的大小
        int ySize = width * height;
        int uvSize = (width / 2) * (height / 2);
        int totalSize = ySize + 2 * uvSize;

        // 创建 YUV420 缓冲区
        byte[] yuv420 = new byte[totalSize];

        // 用于存储 Y, U, V 分量
        int yIndex = 0;
        int uIndex = ySize;
        int vIndex = ySize + uvSize;

        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                // 读取 RGBA 像素
                int pos = (j * width + i) * 4; // 每个像素有 4 个字节（R, G, B, A）
                int r = rgba[pos] & 0xFF;
                int g = rgba[pos + 1] & 0xFF;
                int b = rgba[pos + 2] & 0xFF;

                // RGB to YUV 转换
                int y = (int) (0.299f * r + 0.587f * g + 0.114f * b);
                int u = (int) (-0.1687f * r - 0.3313f * g + 0.5f * b + 128);
                int v = (int) (0.5f * r - 0.4187f * g - 0.0813f * b + 128);

                // 存储 Y 分量
                yuv420[yIndex++] = (byte) y;

                // 每隔一行，每隔一列存储 U 和 V 分量
                if (j % 2 == 0 && i % 2 == 0) {
                    yuv420[uIndex++] = (byte) u;
                    yuv420[vIndex++] = (byte) v;
                }
            }
        }

        return yuv420;
    }

    public static byte[] imageToRGBAByteArray(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        yBuffer.clear().limit(yBuffer.capacity());
        uBuffer.clear().limit(uBuffer.capacity());
        vBuffer.clear().limit(vBuffer.capacity());

        int width = image.getWidth();
        int height = image.getHeight();
        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        // Create a new byte array to hold the RGBA data
        byte[] rgba = new byte[width * height * 4];

        // Extract Y, U, and V data
        byte[] yData = new byte[ySize];
        byte[] uData = new byte[uSize];
        byte[] vData = new byte[vSize];
        yBuffer.get(yData);
        uBuffer.get(uData);
        vBuffer.get(vData);

        // Calculate the row strides for each plane
        int yRowStride = planes[0].getRowStride();
        int uvRowStride = planes[1].getRowStride();
        int uvPixelStride = planes[1].getPixelStride();

        // Convert YUV to RGBA
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                int yIndex = j * yRowStride + i;
                int uIndex = (j / 2) * uvRowStride + (i / 2) * uvPixelStride;
                int vIndex = (j / 2) * uvRowStride + (i / 2) * uvPixelStride;

                int y = yData[yIndex] & 0xFF;
                int u = uData[uIndex] & 0xFF;
                int v = vData[vIndex] & 0xFF;

                // Convert YUV to RGB
                int r = Math.round(1.164f * (y - 16) + 1.596f * (v - 128));
                int g = Math.round(1.164f * (y - 16) - 0.813f * (v - 128) - 0.391f * (u - 128));
                int b = Math.round(1.164f * (y - 16) + 2.018f * (u - 128));

                // Clamp values to 0-255
                r = Math.max(0, Math.min(255, r));
                g = Math.max(0, Math.min(255, g));
                b = Math.max(0, Math.min(255, b));

                // Set RGBA value
                int rgbaIndex = (j * width + i) * 4;
                rgba[rgbaIndex] = (byte) r;
                rgba[rgbaIndex + 1] = (byte) g;
                rgba[rgbaIndex + 2] = (byte) b;
                rgba[rgbaIndex + 3] = (byte) 255; // Alpha channel (fully opaque)
            }
        }

        return rgba;
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}