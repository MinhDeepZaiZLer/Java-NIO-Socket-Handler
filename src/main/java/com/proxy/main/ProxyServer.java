package com.proxy.main;

import com.proxy.cache.CacheManager;
import com.proxy.core.HttpRequestProcessor;
import com.proxy.cache.BlacklistManager;

import javax.swing.SwingUtilities;
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

        System.out.println("🔧 Initializing Proxy Server...");

        // ----------------------------------------------------------------------
        // 1) Khởi tạo CacheManager + BlacklistManager + test DB trước
        // ----------------------------------------------------------------------
        cacheManager = CacheManager.getInstance();
        BlacklistManager blacklistManager = BlacklistManager.getInstance();

        System.out.println("   [TEST] Testing PgSQL persistence and adding host...");
        try {
            blacklistManager.addHost("test-startup-connection.com");
            System.out.println("   [TEST] Database connection OK.");
        } catch (Exception e) {
            System.err.println("   [CRITICAL] Database startup failure: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        // ----------------------------------------------------------------------
        // 2) Khởi động Admin GUI trên EDT (sau khi DB OK)
        // ----------------------------------------------------------------------
        SwingUtilities.invokeLater(() -> {
            System.out.println("   [GUI] Starting AdminApp...");
            new com.proxy.admin.AdminApp(blacklistManager);
        });

        // ----------------------------------------------------------------------
        // 3) Mở ServerSocket và chạy Proxy Server
        // ----------------------------------------------------------------------
        try (ServerSocket serverSocket = new ServerSocket(PROXY_PORT)) {

            System.out.println("✅ Proxy Server running on port " + PROXY_PORT);
            System.out.println("   Configure browser proxy to: 127.0.0.1:" + PROXY_PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("-> [NEW] Connection from: " + clientSocket.getInetAddress().getHostAddress());

                threadPool.execute(new ClientHandler(clientSocket, cacheManager));
            }

        } catch (IOException e) {
            System.err.println("❌ ServerSocket Error: " + e.getMessage());
            threadPool.shutdown();
        }
    }
}
