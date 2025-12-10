package com.proxy.main;

import com.proxy.admin.AdminApp;
import com.proxy.admin.AdminStatsUpdater;
import com.proxy.cache.BlacklistManager;
import com.proxy.cache.CacheManager;
import com.proxy.core.usecase.ProxyRequestUseCase;

import javax.swing.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class ProxyServer {

    private static final int PROXY_PORT = 8888;
    private static Selector selector;
    
    private static CacheManager cacheManager;
    private static BlacklistManager blacklistManager;
    private static ProxyRequestUseCase proxyRequestUseCase;
    private static AdminApp adminApp;
    private static AdminStatsUpdater statsUpdater;
    
    // Server state
    private static volatile boolean isRunning = true;
    private static volatile int activeConnections = 0;

    public static void main(String[] args) {
        printBanner();
        
        System.out.println("🔧 Initializing Proxy Server with Clean Architecture (NIO)...");

        // 1) INITIALIZE CORE (USE CASES & ENTITIES)
        cacheManager = CacheManager.getInstance();
        blacklistManager = BlacklistManager.getInstance();
        proxyRequestUseCase = new ProxyRequestUseCase(cacheManager, blacklistManager);
        statsUpdater = AdminStatsUpdater.getInstance();
        
        // Set cache manager for stats updater
        statsUpdater.setCacheManager(cacheManager);

        System.out.println("  [TEST] Testing PostgreSQL persistence and adding host...");
        try {
            blacklistManager.addHost("test-startup-connection.com");
            System.out.println("  [TEST] ✅ Database connection OK.");
        } catch (Exception e) {
            System.err.println("  [CRITICAL] ❌ Database startup failure: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        // 2) START UI/ADMIN
        SwingUtilities.invokeLater(() -> {
            try {
                // Set system look and feel
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                System.err.println("  [WARNING] Could not set system look and feel");
            }
            
            System.out.println("  [GUI] Starting AdminApp...");
            adminApp = new AdminApp(blacklistManager);
            
            // Connect AdminApp with stats updater
            statsUpdater.setAdminApp(adminApp);
            adminApp.updateProxyStatus(true);
            
            System.out.println("  [GUI] ✅ Admin Panel launched successfully!");
        });

        // Give UI time to initialize
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 3) START INFRASTRUCTURE (NIO Server)
        try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {

            serverChannel.configureBlocking(false);
            serverChannel.socket().bind(new InetSocketAddress(PROXY_PORT));
            
            selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("✅ NIO Proxy Server running on port " + PROXY_PORT);
            System.out.println("  📡 Configure browser proxy to: 127.0.0.1:" + PROXY_PORT);
            System.out.println("  🎛️  Admin Panel is ready for monitoring");
            System.out.println("\n" + "=".repeat(60));
            System.out.println("  Server is listening for connections...");
            System.out.println("=".repeat(60) + "\n");

            nioEventLoop();

        } catch (IOException e) {
            System.err.println("❌ NIO Server Error: " + e.getMessage());
            e.printStackTrace();
            
            // Update admin UI
            if (adminApp != null) {
                adminApp.updateProxyStatus(false);
            }
        }
    }

    private static void nioEventLoop() {
        try {
            while (isRunning) {
                selector.select();

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();

                    // Check if key is still valid
                    if (!key.isValid()) {
                        continue;
                    }

                    try {
                        if (key.isAcceptable()) {
                            handleAccept(key);
                        } else {
                            // Get handler and check for null BEFORE any operations
                            NioConnectionHandler handler = (NioConnectionHandler) key.attachment();
                            if (handler == null) {
                                System.err.println("  [NIO WARN] Key has no handler attachment, skipping");
                                key.cancel();
                                continue;
                            }

                            // Get client key safely
                            SelectionKey clientKey = handler.getClientKey();
                            if (clientKey == null) {
                                System.err.println("  [NIO WARN] Handler has no client key, closing");
                                handler.closeConnection();
                                decrementActiveConnections();
                                continue;
                            }

                            // Determine if this is the client or server key
                            boolean isClientKey = (key == clientKey);

                            if (key.isConnectable()) {
                                handler.handleServerConnect(key);
                            } else if (key.isReadable()) {
                                if (isClientKey) {
                                    handler.handleRead();
                                } else {
                                    handler.handleServerRead(key);
                                }
                            } else if (key.isWritable()) {
                                if (isClientKey) {
                                    handler.handleWrite();
                                } else {
                                    handler.handleServerWrite(key);
                                }
                            }
                        }
                    } catch (IOException e) {
                        // Handle I/O errors and close connection
                        System.err.println("  [NIO ERROR] Connection error: " + e.getMessage());
                        
                        NioConnectionHandler handler = (NioConnectionHandler) key.attachment();
                        if (handler != null) {
                            handler.closeConnection();
                            decrementActiveConnections();
                        } else {
                            key.cancel();
                            try {
                                if (key.channel() != null && key.channel().isOpen()) {
                                    key.channel().close();
                                }
                            } catch (IOException ignored) {
                            }
                        }
                    } catch (Exception e) {
                        // Catch ANY other exception to prevent loop crash
                        System.err.println("  [NIO ERROR] Unexpected error: " + e.getClass().getName() + ": " + e.getMessage());
                        e.printStackTrace();
                        
                        // Try to clean up
                        NioConnectionHandler handler = (NioConnectionHandler) key.attachment();
                        if (handler != null) {
                            try {
                                handler.closeConnection();
                                decrementActiveConnections();
                            } catch (Exception cleanupError) {
                                System.err.println("  [NIO ERROR] Error during cleanup: " + cleanupError.getMessage());
                            }
                        }
                        key.cancel();
                    }
                }
                
                // Update stats periodically
                updateStatsIfNeeded();
            }
        } catch (Exception e) {
            System.err.println("❌ Critical NIO Loop Error: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            
            // Update admin UI
            if (adminApp != null) {
                adminApp.updateProxyStatus(false);
            }
        } finally {
            try {
                if (selector != null && selector.isOpen()) {
                    selector.close();
                }
            } catch (IOException ignored) {
            }
        }
    }

    private static void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = null;
        
        try {
            clientChannel = serverChannel.accept();
            if (clientChannel == null) {
                return; // Non-blocking accept returned null
            }
            
            clientChannel.configureBlocking(false);
            SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ);
            
            // Wire: Inject Core Use Case into Infrastructure Handler
            new NioConnectionHandler(clientKey, proxyRequestUseCase, cacheManager);

            System.out.println("-> [NIO NEW] Connection from: " + clientChannel.getRemoteAddress());
            
            // Update stats
            incrementActiveConnections();
            statsUpdater.incrementTotalRequests();
            
        } catch (IOException e) {
            System.err.println("  [NIO ERROR] Failed to accept connection: " + e.getMessage());
            if (clientChannel != null && clientChannel.isOpen()) {
                try {
                    clientChannel.close();
                } catch (IOException ignored) {
                }
            }
            throw e;
        }
    }
    
    private static synchronized void incrementActiveConnections() {
        activeConnections++;
        statsUpdater.setActiveConnections(activeConnections);
    }
    
    private static synchronized void decrementActiveConnections() {
        activeConnections--;
        if (activeConnections < 0) activeConnections = 0;
        statsUpdater.setActiveConnections(activeConnections);
    }
    
    private static long lastStatsUpdate = 0;
    private static final long STATS_UPDATE_INTERVAL = 2000; // 2 seconds
    
    private static void updateStatsIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastStatsUpdate > STATS_UPDATE_INTERVAL) {
            statsUpdater.refreshStats();
            lastStatsUpdate = now;
        }
    }
    
    private static void printBanner() {
        String banner = """
            ╔═══════════════════════════════════════════════════════════╗
            ║                                                           ║
            ║        🌐 PROXY WEB SERVER v2.0 - NIO Edition            ║
            ║        Advanced Network Security & Caching               ║
            ║                                                           ║
            ║        Features:                                          ║
            ║        ✓ Non-blocking I/O (NIO) Architecture            ║
            ║        ✓ PostgreSQL Blacklist Management                ║
            ║        ✓ Smart Response Caching                         ║
            ║        ✓ Real-time Admin Dashboard                      ║
            ║        ✓ Import/Export Configuration                    ║
            ║        ✓ Clean Architecture Design                      ║
            ║                                                           ║
            ╚═══════════════════════════════════════════════════════════╝
            """;
        System.out.println(banner);
    }
    
    /**
     * Gracefully shutdown the server
     */
    public static void shutdown() {
        System.out.println("\n[SHUTDOWN] Stopping proxy server...");
        isRunning = false;
        
        try {
            if (selector != null && selector.isOpen()) {
                selector.wakeup();
            }
        } catch (Exception e) {
            System.err.println("  [ERROR] Error during shutdown: " + e.getMessage());
        }
        
        // Update admin UI
        if (adminApp != null) {
            adminApp.updateProxyStatus(false);
        }
        
        System.out.println("[SHUTDOWN] ✅ Server stopped gracefully");
    }
}