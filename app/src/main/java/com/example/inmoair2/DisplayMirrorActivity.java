package com.example.inmoair2;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class DisplayMirrorActivity extends AppCompatActivity {
    private static final String TAG = "INMO";
    private static final int width = 1280;
    private static final int height = 800;
    private volatile MediaCodecDecoder decoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_display_mirror);

        TextView textView = findViewById(R.id.textView);
        textView.setText(getLocalIpAddress(this));

        {
            TCPServer server = new TCPServer(50006);
            server.start();
            server.setHandler(new TCPServer.ServerHandler() {
                @Override
                public void onConnected() {
                    runOnUiThread(()->{
                        textView.setVisibility(View.INVISIBLE);
                    });
                }

                @Override
                public void onDisconnected() {
                    runOnUiThread(()->{
                        textView.setVisibility(View.VISIBLE);
                    });
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

        TextureView textureView = findViewById(R.id.mirror_preview);
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
            return "无法获取IP地址";
        }
    }
}
