package com.proxy.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class CacheManager {
    // Sử dụng ConcurrentHashMap để đảm bảo an toàn luồng (Thread-safe)
    private final Map<String, CachedResponse> cache = new ConcurrentHashMap<>();

    // Private constructor cho Singleton
    private CacheManager() {}

    private static class SingletonHelper {
        private static final CacheManager INSTANCE = new CacheManager();
    }

    public static CacheManager getInstance() {
        return SingletonHelper.INSTANCE;
    }

    public byte[] get(String url) {
        CachedResponse response = cache.get(url);
        if (response == null) return null;

        if (response.isExpired()) {
            cache.remove(url);
            System.out.println("   [CACHE] Expired and removed: " + url);
            return null;
        }
        
        System.out.println("   [CACHE] HIT (Serving from cache): " + url);
        return response.getData();
    }

    public void put(String url, byte[] data) {
        // Chỉ cache GET Request và dữ liệu nhỏ (ví dụ: < 1MB)
        if (data.length < 1024 * 1024) { 
            cache.put(url, new CachedResponse(data));
            System.out.println("   [CACHE] Stored: " + url + " (" + data.length + " bytes)");
        }
    }
}