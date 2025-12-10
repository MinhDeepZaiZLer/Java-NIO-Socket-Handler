package com.proxy.admin;

import com.proxy.cache.CacheManager;

/**
 * Bridge class to update AdminApp statistics from ProxyServer
 * Thread-safe singleton to avoid concurrency issues
 */
public class AdminStatsUpdater {
    
    private static AdminStatsUpdater instance;
    private AdminApp adminApp;
    private CacheManager cacheManager;
    
    private volatile int totalRequests = 0;
    private volatile int blockedRequests = 0;
    private volatile int activeConnections = 0;
    
    private AdminStatsUpdater() {}
    
    public static synchronized AdminStatsUpdater getInstance() {
        if (instance == null) {
            instance = new AdminStatsUpdater();
        }
        return instance;
    }
    
    /**
     * Set the AdminApp instance to update
     */
    public void setAdminApp(AdminApp adminApp) {
        this.adminApp = adminApp;
    }
    
    /**
     * Set the CacheManager to get cache statistics
     */
    public void setCacheManager(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }
    
    /**
     * Increment total request count
     */
    public synchronized void incrementTotalRequests() {
        totalRequests++;
        updateAdminUI();
    }
    
    /**
     * Increment blocked request count
     */
    public synchronized void incrementBlockedRequests() {
        blockedRequests++;
    }
    
    /**
     * Update active connection count
     */
    public synchronized void setActiveConnections(int count) {
        activeConnections = count;
        if (adminApp != null) {
            adminApp.updateActiveConnections(count);
        }
    }
    
    /**
     * Update AdminApp UI with current statistics
     */
    private void updateAdminUI() {
        if (adminApp != null && cacheManager != null) {
            int cacheHits = cacheManager.getCacheHits();
            adminApp.updateCacheStats(cacheHits, totalRequests);
        }
    }
    
    /**
     * Manually refresh all statistics
     */
    public void refreshStats() {
        updateAdminUI();
    }
    
    /**
     * Get total requests count
     */
    public int getTotalRequests() {
        return totalRequests;
    }
    
    /**
     * Get blocked requests count
     */
    public int getBlockedRequests() {
        return blockedRequests;
    }
    
    /**
     * Get active connections count
     */
    public int getActiveConnections() {
        return activeConnections;
    }
    
    /**
     * Reset all statistics
     */
    public synchronized void resetStats() {
        totalRequests = 0;
        blockedRequests = 0;
        activeConnections = 0;
        updateAdminUI();
    }
}