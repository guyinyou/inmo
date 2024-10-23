package com.example.inmoair2;


import java.nio.ByteBuffer;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;


public class MediaCodecEncoder {
    private static final String TAG = "INMO";

    private static final String MIME_TYPE = "video/avc";
    private static final int I_FRAME_INTERVAL = 1;

    private MediaCodec mediaCodec;

    private MediaCodecDecoder.OutDataHandler outDataHandler = null;
    private byte[] firstFrame = null;

    public MediaCodecEncoder(int width, int height, int bitRate, int frameRate, Integer formate) {
        Log.d("Unity", "VideoEncoder init : " + width + ", " + height + ", " + bitRate + ", " + frameRate);
        try {
            // 创建 MediaCodec，此时是 Uninitialized 状态
            mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);

            MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);        //颜色格式
            if (formate != null) {
                mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, formate);
            }
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);                                                     //码率
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);                                                               //帧率
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 3);
            mediaFormat.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);

            // 调用 configure 进入 Configured 状态
            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);//I 帧间隔
            mediaCodec.start();
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize video encoder: ", e);
        }
    }

    // 输入数据进行编码
    public void encodeFrame(byte[] data) {
//        Log.d("Unity", "encodeFrame: " + data);
//        Log.d("Unity", "encodeFrameLen: " + data.length);
        try {
            int inputBufferIndex = mediaCodec.dequeueInputBuffer(0);
            if (inputBufferIndex >= 0) {
                ByteBuffer buffer = mediaCodec.getInputBuffers()[inputBufferIndex];
                buffer.clear();
                Log.d(TAG, "encodeIn: " + data.length);
                buffer.put(data);
                mediaCodec.queueInputBuffer(inputBufferIndex, 0, data.length, 0, 0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to encode frame: ", e);
        }
    }

    // 获取编码后的数据
    public byte[] getEncodedFrame() {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, -1);
        if (outputBufferIndex >= 0) {
            ByteBuffer outputBuffer = mediaCodec.getOutputBuffers()[outputBufferIndex];
            byte[] outputData = new byte[bufferInfo.size];
            outputBuffer.get(outputData);
            mediaCodec.releaseOutputBuffer(outputBufferIndex, false);

            if (firstFrame == null) {
                if (isFirstFrame(outputData)) {
                    firstFrame = new byte[outputData.length];
                    System.arraycopy(outputData, 0, firstFrame, 0, firstFrame.length);
                }
            }

            return outputData;
        }
        return null;
    }

    // 释放资源
    public void release() {
        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
            mediaCodec = null;
        }
    }

    public interface OutDataHandler {
        void handle(byte[] data);
    }

    public static boolean isFirstFrame(byte[] frameData) {
        if (frameData == null || frameData.length < 5) {
            return false;
        }

        // 提取 NAL 单元类型
        int nalUnitType = (frameData[4] & 0x1F);

        // 检查是否为 I 帧 (IDR 帧)
        return nalUnitType == 7 || nalUnitType == 8;
    }

    public byte[] getFirstFrame() {
        return firstFrame;
    }
}
