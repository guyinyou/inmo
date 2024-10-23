package com.example.inmoair2;

import android.util.Log;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class TCPServer {
    private int port;
    private ServerHandler handler;
    private volatile boolean running = false;
    private Thread serverThread;

    public TCPServer(int port) {
        this.port = port;
    }

    public void setHandler(ServerHandler handler) {
        this.handler = handler;
    }

    public void start() {
        if (running) return;

        running = true;
        serverThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                while (running) {
                    Log.d("TCPServer", "Waiting for a client to connect...");
                    Socket clientSocket = serverSocket.accept();
                    Log.d("TCPServer", "Client connected: " + clientSocket.getInetAddress());
                    if (handler != null) {
                        handler.onConnected();
                    }
                    // Handle the client in a separate thread
                    handleClient(clientSocket);
                }
            } catch (IOException e) {
                if (running) {
                    e.printStackTrace();
                }
            }
        });

        serverThread.start();
    }

    public void stop() {
        running = false;
        if (serverThread != null) {
            serverThread.interrupt();
        }
    }

    private void handleClient(Socket clientSocket) {
        try (DataInputStream dis = new DataInputStream(clientSocket.getInputStream())) {
            while (running) {
                int length = dis.readInt(); // 先读取长度
                byte[] data = new byte[length];
                dis.readFully(data); // 根据长度读取消息内容
                if (handler != null) {
                    handler.handleData(data);
                }
            }
        } catch (IOException e) {
            if (running) {
                e.printStackTrace();
            }
        } finally {
            try {
                clientSocket.close();
                System.out.println("Client disconnected");
                if (handler != null) {
                    handler.onDisconnected();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public interface ServerHandler {
        void onConnected();
        void onDisconnected();
        void handleData(byte[] data);
    }
}