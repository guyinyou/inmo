package com.example.inmoair2;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodecInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import java.util.concurrent.ThreadLocalRandom;

public class ScreenRecordActivity extends AppCompatActivity {

    private static final int REQUEST_CODE = 1000;
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private static final int width = 1280;
    private static final int height = 800;
    private static final String TAG = "INMO";
    private volatile MediaCodecEncoder encoder;
    private volatile MediaCodecDecoder decoder;
    private TCPClient tcpClient = new TCPClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_record);

        TextureView textureView = findViewById(R.id.screen_record_preview);
        Log.d(TAG, "textureView: " + textureView);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int i, int i1) {
                // Initialize the decoder with the TextureView's Surface and EGLContext
                runOnUiThread(() -> {
                    decoder = new MediaCodecDecoder(width, height, new Surface(surface));
                    decoder.setOutDataHandler(new MediaCodecDecoder.OutDataHandler() {
                        @Override
                        public void handle(byte[] data) {
                            Log.d(TAG, "decodeOut: " + data.length);
                        }
                    });
                });
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {

            }
        });
        {
            encoder = new MediaCodecEncoder(width, height, 50 * 1024 * 1024, 60, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
        }

//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
//                != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE);
//        } else {
        startScreenRecording();
//        }

        {
            EditText editText = findViewById(R.id.editTextText2);
            editText.setText(getLocalIpAddress(this));
            // 设置焦点变化监听器
            editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        // 当 EditText 获得焦点时，显示输入法
                        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
                    }
                }
            });
            editText.requestFocus();
            Button button = findViewById(R.id.button4);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    new Thread(()->{
                        try {
                            tcpClient.connect(editText.getText().toString(), 50006);
                        } catch (Throwable t) {
                            Log.e(TAG, "error", t);
                            return;
                        }
                    }).start();
                }
            });

            {
                TCPServer server = new TCPServer(50006);
                server.start();
                server.setHandler(new TCPServer.ServerHandler() {
                    @Override
                    public void onConnected() {

                    }

                    @Override
                    public void onDisconnected() {

                    }

                    @Override
                    public void handleData(byte[] data) {
                        Log.d(TAG, "handleData: " + data.length);
                        if (decoder != null) {
                            decoder.decodeFrame(data);
                        }
                    }
                });
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startScreenRecording();
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void startScreenRecording() {
        // 启动前台服务并传递结果
        Intent serviceIntent = new Intent(this, ScreenRecordService.class);
        serviceIntent.putExtra("mediaProjection", true);
        startForegroundService(serviceIntent);

        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE) {
            if (resultCode != RESULT_OK) {
                Toast.makeText(this, "Screen capture failed", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
            mediaProjection.registerCallback(new MediaProjection.Callback() {
            }, null);
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
            int screenWidth = metrics.widthPixels;
            int screenHeight = metrics.heightPixels;
            screenWidth = width;
            screenHeight = height;

            // 创建ImageReader，指定格式为YUV_420_888
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 1);
            imageReader.setOnImageAvailableListener(imageReaderListener, null);

            // 创建VirtualDisplay，指定格式为YUV_420_888
            virtualDisplay = mediaProjection.createVirtualDisplay("ScreenRecord",
                    screenWidth, screenHeight, metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, null);
        }
    }

    private final ImageReader.OnImageAvailableListener imageReaderListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();
            if (image != null) {
                // 编码最新帧
                byte[] rgba = YuvToRGB.imageToRgba888ByteArray(image);
                byte[] nv12 = YuvToRGB.rgbaToYuv420(rgba, image.getWidth(), image.getHeight());
                encoder.encodeFrame(nv12);

                if (ThreadLocalRandom.current().nextDouble() < 0.1) {
                    byte[] firstFrame = encoder.getFirstFrame();
                    if (firstFrame != null) {
                        Log.d(TAG, "send first frame: " + firstFrame.length);
                        sendOutData(firstFrame);
                    }
                }
                byte[] tmpBytes = encoder.getEncodedFrame();
                if (tmpBytes != null && tmpBytes.length > 0) {
                    Log.d(TAG, "encodeOut: " + tmpBytes.length);
                    MediaCodecDecoder.getFrameType(tmpBytes);
                    sendOutData(tmpBytes);
                }

                image.close();
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (virtualDisplay != null) {
            virtualDisplay.release();
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
        }
    }

    public String getLocalIpAddress(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int ipAddress = wifiInfo.getIpAddress();

            // 将整数形式的IP地址转换为点分十进制字符串
            String ip = String.format(
                    "%d.%d.%d.%d",
                    (ipAddress & 0xff),
                    (ipAddress >> 8 & 0xff),
                    (ipAddress >> 16 & 0xff),
                    (ipAddress >> 24 & 0xff));

            return ip;
        } else {
            return "";
        }
    }
    private void sendOutData(byte[] data) {
//        if (decoder != null) {
//            decoder.decodeFrame(data);
//        }
        try {
            tcpClient.sendData(data);
        } catch (Throwable t) {
            Log.d(TAG, "error", t);
        }
    }
}