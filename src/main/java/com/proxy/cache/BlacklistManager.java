package com.proxy.cache;

import com.proxy.data.BlockedHostDAO;
import java.util.Set;

public class BlacklistManager {

    private volatile Set<String> blockedHosts;
    private final BlockedHostDAO dao = new BlockedHostDAO();

    // Constructor private để ngăn tạo instance từ bên ngoài
    private BlacklistManager() {
        this.blockedHosts = dao.loadAllHosts();
        System.out.println("   [SECURITY] Blacklist loaded successfully from DB. Count: " + blockedHosts.size());
    }

    // --- Singleton Holder Pattern (Thread-safe, Lazy-loading) ---
    private static class Holder {
        private static final BlacklistManager INSTANCE = new BlacklistManager();
    }

    public static BlacklistManager getInstance() {
        return Holder.INSTANCE;
    }
    // -------------------------------------------------------------

    public boolean isBlocked(String host) {
        return blockedHosts.contains(host.toLowerCase());
    }

    public void addHost(String host) {
        dao.addHost(host);
        this.blockedHosts = dao.loadAllHosts();
    }
    public Set<String> getBlockedHosts() {
    return this.blockedHosts; // Trả về Set đã được tải vào RAM
    }
}
