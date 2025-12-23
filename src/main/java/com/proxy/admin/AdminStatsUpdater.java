package com.proxy.admin;

import com.proxy.cache.CacheManager;

/**
 * Cầu nối cập nhật thống kê từ Server sang AdminApp (UI).
 * Đảm bảo Thread-safe và đồng bộ dữ liệu chính xác.
 */
public class AdminStatsUpdater {
    
    private static AdminStatsUpdater instance;
    private AdminApp adminApp;
    private CacheManager cacheManager;
    
    // Biến này đếm số kết nối TCP (Socket) từ ProxyServer
    private volatile int totalTcpConnections = 0; 
    
    private volatile int blockedRequests = 0;
    private volatile int activeConnections = 0;
    
    private AdminStatsUpdater() {}
    
    public static synchronized AdminStatsUpdater getInstance() {
        if (instance == null) {
            instance = new AdminStatsUpdater();
        }
        return instance;
    }
    
    public void setAdminApp(AdminApp adminApp) {
        this.adminApp = adminApp;
    }
    
    public void setCacheManager(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }
    
    /**
     * Hàm này được gọi từ ProxyServer khi có kết nối TCP mới (handleAccept).
     * Chúng ta dùng nó để kích hoạt việc cập nhật UI.
     */
    public synchronized void incrementTotalRequests() {
        totalTcpConnections++;
        updateAdminUI();
    }
    
    public synchronized void incrementBlockedRequests() {
        blockedRequests++;
        // Có thể cập nhật UI ngay nếu muốn
        updateAdminUI(); 
    }
    
    public synchronized void setActiveConnections(int count) {
        activeConnections = count;
        if (adminApp != null) {
            adminApp.updateActiveConnections(count);
        }
    }
    
    /**
     * Cập nhật UI: Đây là phần quan trọng nhất đã được sửa.
     */
    private void updateAdminUI() {
        if (adminApp != null && cacheManager != null) {
            // Lấy số liệu thực tế từ CacheManager
            int cacheHits = cacheManager.getCacheHits();
            
            // [QUAN TRỌNG] Lấy Total Requests từ CacheManager (HTTP Requests)
            // Thay vì dùng biến totalTcpConnections cục bộ.
            // Điều này giúp tính đúng tỷ lệ: (Hits / HTTP Requests) * 100
            int totalHttpRequests = cacheManager.getTotalRequests();
            
            // Cập nhật thẻ "Cache Performance" và "Total Requests" trên giao diện
            adminApp.updateCacheStats(cacheHits, totalHttpRequests);
        }
    }
    
    /**
     * Làm mới toàn bộ thống kê thủ công (gọi định kỳ từ ProxyServer)
     */
    public void refreshStats() {
        updateAdminUI();
        if (adminApp != null) {
            adminApp.updateActiveConnections(activeConnections);
        }
    }
    
    public int getTotalRequests() {
        return totalTcpConnections;
    }
    
    public int getBlockedRequests() {
        return blockedRequests;
    }
    
    public int getActiveConnections() {
        return activeConnections;
    }
    
    public synchronized void resetStats() {
        totalTcpConnections = 0;
        blockedRequests = 0;
        activeConnections = 0;
        updateAdminUI();
    }
}