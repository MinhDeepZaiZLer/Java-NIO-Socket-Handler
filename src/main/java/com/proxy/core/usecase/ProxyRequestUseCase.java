package com.proxy.core.usecase;

import com.proxy.cache.BlacklistManager;
import com.proxy.cache.CacheManager;
import java.net.URL; 

/**
 * Use Case xử lý Request đầu tiên, độc lập với tầng I/O.
 */
public class ProxyRequestUseCase {
    
    private final CacheManager cacheManager;
    private final BlacklistManager blacklistManager;

    // Dependency Injection: Nhận các Entity/Service từ bên ngoài
    public ProxyRequestUseCase(CacheManager cacheManager, BlacklistManager blacklistManager) {
        this.cacheManager = cacheManager;
        this.blacklistManager = blacklistManager;
    }

    /**
     * Thực thi logic nghiệp vụ (Blacklist/Parsing) trên request line.
     * @param requestLine Dòng đầu tiên của Request HTTP/HTTPS.
     * @return HostPort chứa thông tin server đích, hoặc null nếu bị chặn/lỗi.
     */
    public HostPort processInitialRequest(String requestLine) {
        String[] parts = requestLine.split(" ");
        if (parts.length < 3) return null;
        
        // --- [SỬA 1] TĂNG BIẾN ĐẾM TOTAL REQUESTS ---
        // Mỗi khi có request hợp lệ đi vào đây, ta tăng số lượng request lên
        cacheManager.incrementTotalRequests();

        String method = parts[0];
        String urlString = parts[1];
        
        HostPort hp = null;

        if (method.equalsIgnoreCase("CONNECT")) {
            // HTTPS Tunneling
            String[] hostPort = urlString.split(":");
            String host = hostPort[0];
            int port = (hostPort.length > 1) ? Integer.parseInt(hostPort[1]) : 443;
            hp = new HostPort(host, port, true);
        } else {
            // HTTP
            try {
                URL url = new URL(urlString);
                String host = url.getHost();
                int port = url.getPort() == -1 ? 80 : url.getPort();
                hp = new HostPort(host, port, false);
            } catch (Exception e) {
                System.err.println("   [CORE ERROR] Invalid URL: " + urlString);
                return null;
            }
        }
        
        if (hp == null) return null;
        
        // --- Business Rule: Blacklist Check ---
        if (blacklistManager.isBlocked(hp.getHost())) {
            System.out.println("   [CORE] Blocked access to: " + hp.getHost());
            return null; // Bị chặn
        }
        
        return hp;
    }
    
    /**
     * --- [SỬA 2] HÀM LẤY CACHE CÓ KÈM LOGIC ĐẾM HIT ---
     * NioConnectionHandler nên gọi hàm này thay vì gọi trực tiếp cacheManager.get()
     * để đảm bảo cacheHits được tính toán đúng.
     */
    public byte[] getCachedResponse(String url) {
        byte[] data = cacheManager.get(url);
        if (data != null) {
            // Nếu có dữ liệu -> Tăng biến đếm Hit
            cacheManager.incrementCacheHit();
        }
        return data;
    }

    /**
     * DTO (Data Transfer Object) chứa thông tin Host đích.
     */
    public static class HostPort {
        private final String host;
        private final int port;
        private final boolean isTunneling;

        public HostPort(String host, int port, boolean isTunneling) {
            this.host = host;
            this.port = port;
            this.isTunneling = isTunneling;
        }

        public String getHost() { return host; }
        public int getPort() { return port; }
        public boolean isTunneling() { return isTunneling; }
    }
}