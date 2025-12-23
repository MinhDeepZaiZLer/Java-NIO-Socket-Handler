package com.proxy.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger; // Import quan trọng

public class CacheManager {
    // Sử dụng ConcurrentHashMap để đảm bảo an toàn luồng (Thread-safe)
    private final Map<String, CachedResponse> cache = new ConcurrentHashMap<>();
    
    // SỬA: Dùng AtomicInteger để đếm an toàn trong môi trường đa luồng
    private final AtomicInteger cacheHits = new AtomicInteger(0);
    private final AtomicInteger cacheMisses = new AtomicInteger(0);
    private final AtomicInteger totalRequests = new AtomicInteger(0);

    // Private constructor cho Singleton
    private CacheManager() {
    }

    private static class SingletonHelper {
        private static final CacheManager INSTANCE = new CacheManager();
    }

    public static CacheManager getInstance() {
        return SingletonHelper.INSTANCE;
    }

    public byte[] get(String url) {
        CachedResponse response = cache.get(url);
        
        // Trường hợp không tìm thấy (Miss)
        if (response == null) {
            return null;
        }

        // Trường hợp hết hạn
        if (response.isExpired()) {
            cache.remove(url);
            System.out.println("   [CACHE] Expired and removed: " + url);
            return null;
        }

        // Trường hợp tìm thấy (Hit)
        // Lưu ý: Logic tăng đếm hit nên được gọi từ UseCase để kiểm soát chính xác hơn,
        // nhưng nếu muốn tự động tăng tại đây cũng được. 
        // Tuy nhiên, để tuân thủ hướng dẫn trước, ta sẽ để UseCase gọi hàm incrementCacheHit()
        
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

    // --- CÁC PHƯƠNG THỨC MỚI THÊM VÀO ĐỂ SỬA LỖI ---

    /**
     * Tăng tổng số request (Được gọi từ ProxyRequestUseCase)
     */
    public void incrementTotalRequests() {
        totalRequests.incrementAndGet();
    }

    /**
     * Tăng số lượng Cache Hit (Được gọi từ ProxyRequestUseCase khi get() != null)
     */
    public void incrementCacheHit() {
        cacheHits.incrementAndGet();
    }

    /**
     * Tăng số lượng Cache Miss (Tùy chọn)
     */
    public void incrementCacheMiss() {
        cacheMisses.incrementAndGet();
    }

    // --- CẬP NHẬT GETTER ---

    public int getCacheHits() {
        return cacheHits.get(); // Lấy giá trị từ Atomic
    }
    
    public int getCacheMisses() {
        return cacheMisses.get();
    }
    
    public int getTotalRequests() {
        return totalRequests.get();
    }
}