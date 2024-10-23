package com.example.inmoair2;

import android.Manifest;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.PorterDuff;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.RggbChannelVector;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class CameraPreviewActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final int width = 640;
    private static final int height = 480;
    private static final String TAG = "INMO";
    private TextureView textureView;
    private ImageReader imageReader;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSessions;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private volatile MediaCodecEncoder encoder;
    private volatile MediaCodecDecoder decoder;
    private String cameraId;
    public static Surface sss = null;
    CameraCharacteristics characteristics;
    private TCPClient tcpClient = new TCPClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_camera_preview);

        textureView = findViewById(R.id.camera_preview);
        Log.d(TAG, "textureView: " + textureView);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int i, int i1) {
                // Initialize the decoder with the TextureView's Surface and EGLContext
                new Thread(() -> {
//                    try {
//                        Thread.sleep(1000);
//                    } catch (InterruptedException e) {
//                        throw new RuntimeException(e);
//                    }
                    runOnUiThread(() -> {
                        sss = new Surface(surface);
                        decoder = new MediaCodecDecoder(width, height, sss);
                        decoder.setOutDataHandler(new MediaCodecDecoder.OutDataHandler() {
                            @Override
                            public void handle(byte[] data) {
                                Log.d(TAG, "decodeOut: " + data.length);
                            }
                        });
                    });
                }).start();

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
            encoder = new MediaCodecEncoder(width, height, width * height * 10, 60, null);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            startCamera();
        }

        EditText editText = findViewById(R.id.editTextText);
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
        Button button = findViewById(R.id.button3);
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

    private void startCamera() {
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            String[] cameraIds = manager.getCameraIdList();
            for (String id : cameraIds) {
                Log.d(TAG, "cameraId: " + id);
            }
            cameraId = cameraIds[cameraIds.length - 1]; // 使用第一个后置摄像头
            cameraId = cameraIds[0]; // 使用第一个后置摄像头
            Log.d(TAG, "select cameraId: " + cameraId);
            characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            // 获取所有支持的预览尺寸
            Size[] previewSizes = map.getOutputSizes(SurfaceTexture.class);
            imageDimension = null;
            for (Size previewSize : previewSizes) {
                Log.d(TAG, "previewSizes: " + previewSize);
                if (previewSize.getWidth() == width && previewSize.getHeight() == height) {
                    imageDimension = previewSize;
                }
            }
            if (imageDimension == null) {
                throw new RuntimeException("not found Size: " + width + "x" + height);
            }

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            new Thread(() -> {
                AtomicInteger status = new AtomicInteger(0);
                while (true) {
                    // running
                    int prevStatus = status.getAndSet(1);
                    if (prevStatus == 0) {
                        runOnUiThread(() -> {
                            boolean ret = createCameraPreview();
                            if (ret) {
                                // success
                                status.set(2);
                            } else {
                                status.set(0);
                            }
                        });
                    } else if (prevStatus == 2) {
                        break;
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }).start();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
        }
    };

    private boolean createCameraPreview() {
        try {
            Surface surface = null;
            {
                // 在startCamera()或其他合适的地方初始化ImageReader
                imageReader = ImageReader.newInstance(imageDimension.getWidth(), imageDimension.getHeight(),
                        ImageFormat.YUV_420_888, 1); // 使用YUV格式，缓冲区数量为2
                Log.d(TAG, "imageDimension: " + imageDimension);
                imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(ImageReader reader) {
                        Image image = reader.acquireLatestImage();
                        if (image != null) {
                            // 编码最新帧
                            byte[] nv12 = YuvToRGB.imageToYuv420ByteArray(image);
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
                }, null);
                surface = imageReader.getSurface();
            }
            {
//                SurfaceTexture texture = textureView.getSurfaceTexture();
//                assert texture != null;
//                Log.d(TAG, "imageDimension: " + imageDimension);
//                texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
//                surface = new Surface(texture);
            }

            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
//            {
//                int[] modes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
//                for (int mode : modes) {
//                    Log.d(TAG, "CONTROL_AF_AVAILABLE_MODES: " + mode);
//                }
//                // 关闭自动对焦
//                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
//
//                // 打印设置
//                Log.d(TAG, "CONTROL_AF_MODE set to OFF");
//
//            }

            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range.create(60, 60));
//            captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);
//            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
//            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF);
//            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_OFF);
            captureRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if (null == cameraDevice) {
                        return;
                    }
                    cameraCaptureSessions = cameraCaptureSession;
                    try {
                        cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, null);
                    } catch (CameraAccessException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                }
            }, null);
        } catch (Throwable t) {
            Log.e(TAG, "error", t);
            return false;
        }
        return true;
    }


    public static void drawRgbaOnSurface(byte[] rgba, int width, int height, Surface surface) {
        drawRgbaOnSurface(rgba, width, height, surface, 0, 0);
    }

    public static void drawRgbaOnSurface(byte[] rgba, int width, int height, Surface surface, int left, int top) {
        // Step 1: 创建一个配置为ARGB_8888的Bitmap，因为我们的数据是RGBA格式的
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(rgba));

        // Step 2: 锁定Surface，并获取Canvas
        Canvas canvas = null;
        try {
            canvas = surface.lockCanvas(null);
            if (canvas != null) {
                // Step 3: 在Canvas上绘制Bitmap
//                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); // 清除之前的绘制内容
                canvas.drawBitmap(bitmap, left, top, null); // 绘制Bitmap
            }
        } finally {
            // Step 4: 解锁Surface
            if (canvas != null) {
                surface.unlockCanvasAndPost(canvas);
            }
        }

        // Step 5: 回收Bitmap资源（如果不再需要）
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
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