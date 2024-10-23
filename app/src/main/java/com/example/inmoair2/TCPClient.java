package com.example.inmoair2;

import android.util.Log;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TCPClient {
    private String serverIp;
    private int serverPort;
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private ExecutorService executorService;
    private static final String TAG = "INMO";

    public TCPClient() {
        executorService = Executors.newSingleThreadExecutor();
    }

    /**
     * 连接到指定的服务器IP和端口。
     *
     * @param ip   服务器IP地址
     * @param port 服务器端口号
     * @throws IOException 如果连接失败
     */
    public void connect(String ip, int port) throws IOException {
        if (socket != null && !socket.isClosed()) {
            disconnect();
        }
        this.serverIp = ip;
        this.serverPort = port;
        socket = new Socket(ip, port);
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
    }

    /**
     * 断开与服务器的连接。
     */
    public void disconnect() {
        try {
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            inputStream = null;
            outputStream = null;
            socket = null;
        }
    }

    /**
     * 发送数据到服务器。
     *
     * @param data 要发送的数据
     * @throws IOException 如果发送失败
     */
    public void sendData(byte[] data) throws IOException {
        executorService.execute(() -> {
            try {
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                out.writeInt(data.length);
                out.write(data);
                out.flush();
            } catch (Throwable t) {
                Log.d(TAG, "error", t);
            }
        });
    }

    /**
     * 从服务器接收数据。
     *
     * @return 接收到的数据
     * @throws IOException 如果接收失败
     */
    public byte[] receiveData() throws IOException {
        if (inputStream == null) {
            throw new IOException("Not connected to the server");
        }
        byte[] buffer = new byte[1024];
        int bytesRead = inputStream.read(buffer);
        if (bytesRead == -1) {
            return null; // Connection closed by the server
        }
        byte[] receivedData = new byte[bytesRead];
        System.arraycopy(buffer, 0, receivedData, 0, bytesRead);
        return receivedData;
    }
}