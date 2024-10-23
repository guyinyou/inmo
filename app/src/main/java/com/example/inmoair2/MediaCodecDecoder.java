package com.example.inmoair2;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MediaCodecDecoder {
    private static final String TAG = "INMO";

    private MediaCodec mediaCodec;
    private ByteBuffer[] inputBuffers;
    private ByteBuffer[] outputBuffers;
    private Surface surface = null;
    private OutDataHandler outDataHandler = null;
    private boolean needSyncing = false;
    private boolean hasFirstFrame = false;

    public MediaCodecDecoder(int width, int height, Surface surface) {
        Log.d("Unity", "VideoDecoder init : " + width + ", " + height);
        this.surface = surface;
        try {
            // 创建解码器的 MediaFormat
            MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
            format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
            // 创建解码器并配置
            mediaCodec = MediaCodec.createDecoderByType("video/avc");
            mediaCodec.configure(format, surface, null, 0);

            mediaCodec.start();

        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize video decoder: " + e.getMessage());
        }
    }

    public void decodeFrame(byte[] data) {
        int type = getFrameType(data);
        if (!hasFirstFrame) {
            if (type != 7 && type != 8) {
                return;
            }
            hasFirstFrame = true;
        } else if (type == 7 || type == 8) {
            return;
        }
        if (needSyncing) {
            if (type != NAL_UNIT_TYPE_IDR) {
                Log.d(TAG, "need NAL_UNIT_TYPE_IDR");
                return;
            }
            needSyncing = false;
        }
        try {
            int inputBufferIndex = mediaCodec.dequeueInputBuffer(0);
            inputBuffers = mediaCodec.getInputBuffers();
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                inputBuffer.put(data);
                mediaCodec.queueInputBuffer(inputBufferIndex, 0, data.length, 0, 0);
                Log.d(TAG, "decodeIn: " + data.length);
            }

            handleOutput();
        } catch (Throwable e) {
            Log.e(TAG, "Failed to decode frame: ", e);
        }
    }

    public void release() {
        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
            mediaCodec = null;
        }
    }

    private void handleOutput() {
        byte[] frameData;
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 100000);
        outputBuffers = mediaCodec.getOutputBuffers();
        if (needSyncing || outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
            needSyncing = true;
            return;
        }
        while (outputBufferIndex >= 0) {
            if (this.surface != null) {
                mediaCodec.releaseOutputBuffer(outputBufferIndex, true);
                outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                continue;
            }

            ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
            // 处理解码后的帧数据
            frameData = new byte[bufferInfo.size];
            outputBuffer.get(frameData);

            mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
            outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            outputBuffers = mediaCodec.getOutputBuffers();

            if (outDataHandler != null) {
                outDataHandler.handle(frameData);
            }
        }
    }

    public void setOutDataHandler(OutDataHandler outDataHandler) {
        this.outDataHandler = outDataHandler;
    }

    public interface OutDataHandler {
        void handle(byte[] data);
    }

    private static final int NAL_UNIT_TYPE_IDR = 5;   // IDR 图片
    public static int getFrameType(byte[] frameData) {
        if (frameData == null || frameData.length < 5) {
            return -1;
        }

        // 提取 NAL 单元类型
        int nalUnitType = (frameData[4] & 0x1F);

        Log.d(TAG, "frameType: " + nalUnitType);
        // 检查是否为 I 帧 (IDR 帧)
        return nalUnitType;
    }
}
