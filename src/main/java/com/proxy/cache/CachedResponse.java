package com.proxy.cache;

import java.time.LocalDateTime;

public class CachedResponse {
    private final byte[] data;
    // Cache hết hạn sau 60 giây (ví dụ)
    private static final long EXPIRY_SECONDS = 500; 
    private final LocalDateTime expiryTime;

    public CachedResponse(byte[] data) {
        this.data = data;
        this.expiryTime = LocalDateTime.now().plusSeconds(EXPIRY_SECONDS); 
    }

    public byte[] getData() {
        return data;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryTime);
    }
}