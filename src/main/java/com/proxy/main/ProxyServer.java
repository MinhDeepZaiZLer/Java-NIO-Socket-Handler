// File: src/com/proxy/main/ProxyServer.java

package com.proxy.main;

import com.proxy.core.HttpRequestProcessor;
import com.proxy.cache.CacheManager;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyServer {
    private static final int PROXY_PORT = 8888;
    private static final ExecutorService threadPool = Executors.newCachedThreadPool();
    private static CacheManager cacheManager;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PROXY_PORT)) {
            System.out.println("✅ Proxy Server running on port " + PROXY_PORT + ".");
            System.out.println("   Configure browser proxy to: 127.0.0.1:" + PROXY_PORT);
            
            // Khởi tạo CacheManager và các Dependency khác
            cacheManager = CacheManager.getInstance();
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("-> [NEW] Connection from: " + clientSocket.getInetAddress().getHostAddress());

                // Sử dụng ClientHandler (vẫn là Runnable) để xử lý logic chính
                threadPool.execute(new ClientHandler(clientSocket, cacheManager));
            }
        } catch (IOException e) {
            System.err.println("❌ ServerSocket Error: " + e.getMessage());
            threadPool.shutdown();
        }
    }
}