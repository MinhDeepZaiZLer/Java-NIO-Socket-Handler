package com.proxy.cache;

import com.proxy.data.BlockedHostDAO;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.io.*;
import java.nio.file.*;
import java.util.stream.Collectors;

public class BlacklistManager {

    private volatile Set<String> blockedHosts;
    private final BlockedHostDAO dao = new BlockedHostDAO();
    private long lastModified;

    // Constructor private để ngăn tạo instance từ bên ngoài
    private BlacklistManager() {
        this.blockedHosts = dao.loadAllHosts();
        this.lastModified = System.currentTimeMillis();
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

    /**
     * Kiểm tra xem host có bị chặn không
     * @param host Hostname cần kiểm tra
     * @return true nếu bị chặn
     */
    public boolean isBlocked(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        
        String normalizedHost = host.toLowerCase().trim();
        
        // Kiểm tra exact match
        if (blockedHosts.contains(normalizedHost)) {
            return true;
        }
        
        // Kiểm tra wildcard (*.example.com)
        for (String blocked : blockedHosts) {
            if (blocked.startsWith("*.")) {
                String domain = blocked.substring(2);
                if (normalizedHost.endsWith(domain)) {
                    return true;
                }
            }
        }
        
        return false;
    }

    /**
     * Thêm host vào blacklist
     * @param host Hostname cần chặn
     * @throws IllegalArgumentException nếu host không hợp lệ
     */
    public void addHost(String host) {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Host cannot be null or empty");
        }
        
        String normalizedHost = host.toLowerCase().trim();
        
        // Validate hostname format
        if (!isValidHostname(normalizedHost)) {
            throw new IllegalArgumentException("Invalid hostname format: " + host);
        }
        
        try {
            dao.addHost(normalizedHost);
            this.blockedHosts = dao.loadAllHosts();
            this.lastModified = System.currentTimeMillis();
            System.out.println("   [SECURITY] Host blocked: " + normalizedHost);
        } catch (Exception e) {
            System.err.println("   [ERROR] Failed to add host to blacklist: " + e.getMessage());
            throw new RuntimeException("Failed to add host to blacklist", e);
        }
    }

    /**
     * Xóa host khỏi blacklist
     * @param host Hostname cần gỡ chặn
     * @throws Exception nếu có lỗi database
     */
    public void removeHost(String host) throws Exception {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Host cannot be null or empty");
        }
        
        String normalizedHost = host.toLowerCase().trim();
        
        if (!blockedHosts.contains(normalizedHost)) {
            throw new IllegalArgumentException("Host not found in blacklist: " + host);
        }
        
        try {
            dao.removeHost(normalizedHost);
            this.blockedHosts = dao.loadAllHosts();
            this.lastModified = System.currentTimeMillis();
            System.out.println("   [SECURITY] Host unblocked: " + normalizedHost);
        } catch (Exception e) {
            System.err.println("   [ERROR] Failed to remove host from blacklist: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Xóa tất cả hosts khỏi blacklist
     * @throws Exception nếu có lỗi
     */
    public void clearAll() throws Exception {
        try {
            Set<String> currentHosts = new HashSet<>(blockedHosts);
            for (String host : currentHosts) {
                dao.removeHost(host);
            }
            this.blockedHosts = dao.loadAllHosts();
            this.lastModified = System.currentTimeMillis();
            System.out.println("   [SECURITY] Blacklist cleared completely");
        } catch (Exception e) {
            System.err.println("   [ERROR] Failed to clear blacklist: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Lấy danh sách tất cả blocked hosts (read-only)
     * @return Unmodifiable set of blocked hosts
     */
    public Set<String> getBlockedHosts() {
        return Collections.unmodifiableSet(blockedHosts);
    }

    /**
     * Lấy số lượng hosts bị chặn
     * @return Số lượng hosts
     */
    public int getBlockedHostsCount() {
        return blockedHosts.size();
    }

    /**
     * Reload blacklist từ database
     */
    public void reload() {
        this.blockedHosts = dao.loadAllHosts();
        this.lastModified = System.currentTimeMillis();
        System.out.println("   [SECURITY] Blacklist reloaded. Count: " + blockedHosts.size());
    }

    /**
     * Import blacklist từ file
     * @param filePath Đường dẫn file
     * @return Số lượng hosts được import thành công
     * @throws IOException nếu có lỗi đọc file
     */
    public int importFromFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new FileNotFoundException("File not found: " + filePath);
        }
        
        int successCount = 0;
        int failCount = 0;
        
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                try {
                    addHost(line);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    System.err.println("   [WARNING] Failed to import host: " + line);
                }
            }
        }
        
        System.out.println("   [SECURITY] Import completed. Success: " + successCount + ", Failed: " + failCount);
        return successCount;
    }

    /**
     * Export blacklist ra file
     * @param filePath Đường dẫn file xuất
     * @throws IOException nếu có lỗi ghi file
     */
    public void exportToFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        
        try (BufferedWriter writer = Files.newBufferedWriter(path, 
                StandardOpenOption.CREATE, 
                StandardOpenOption.TRUNCATE_EXISTING)) {
            
            writer.write("# Proxy Server Blacklist");
            writer.newLine();
            writer.write("# Exported at: " + new java.util.Date());
            writer.newLine();
            writer.write("# Total hosts: " + blockedHosts.size());
            writer.newLine();
            writer.write("#");
            writer.newLine();
            writer.newLine();
            
            for (String host : blockedHosts.stream().sorted().collect(Collectors.toList())) {
                writer.write(host);
                writer.newLine();
            }
        }
        
        System.out.println("   [SECURITY] Blacklist exported to: " + filePath);
    }

    /**
     * Tìm kiếm hosts theo keyword
     * @param keyword Từ khóa tìm kiếm
     * @return Set các hosts chứa keyword
     */
    public Set<String> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptySet();
        }
        
        String searchTerm = keyword.toLowerCase().trim();
        return blockedHosts.stream()
            .filter(host -> host.contains(searchTerm))
            .collect(Collectors.toSet());
    }

    /**
     * Thêm nhiều hosts cùng lúc
     * @param hosts Set các hosts cần thêm
     * @return Số lượng hosts được thêm thành công
     */
    public int addHosts(Set<String> hosts) {
        if (hosts == null || hosts.isEmpty()) {
            return 0;
        }
        
        int successCount = 0;
        for (String host : hosts) {
            try {
                addHost(host);
                successCount++;
            } catch (Exception e) {
                System.err.println("   [WARNING] Failed to add host: " + host);
            }
        }
        
        return successCount;
    }

    /**
     * Lấy thời gian sửa đổi cuối cùng
     * @return Timestamp
     */
    public long getLastModified() {
        return lastModified;
    }

    /**
     * Validate hostname format
     * @param hostname Hostname cần validate
     * @return true nếu hợp lệ
     */
    private boolean isValidHostname(String hostname) {
        if (hostname == null || hostname.isEmpty()) {
            return false;
        }
        
        // Allow wildcard
        if (hostname.startsWith("*.")) {
            hostname = hostname.substring(2);
        }
        
        // Basic validation
        // Hostname can contain: letters, numbers, dots, hyphens
        // Cannot start or end with dot or hyphen
        String regex = "^(?!-)[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*(?<!-)$";
        return hostname.matches(regex) && hostname.length() <= 253;
    }

    /**
     * Lấy thống kê blacklist
     * @return String chứa thông tin thống kê
     */
    public String getStatistics() {
        long wildcardCount = blockedHosts.stream()
            .filter(h -> h.startsWith("*."))
            .count();
        
        long exactCount = blockedHosts.size() - wildcardCount;
        
        return String.format(
            "Blacklist Statistics:\n" +
            "  Total hosts: %d\n" +
            "  Exact matches: %d\n" +
            "  Wildcard rules: %d\n" +
            "  Last modified: %s",
            blockedHosts.size(),
            exactCount,
            wildcardCount,
            new java.util.Date(lastModified)
        );
    }

    /**
     * Kiểm tra xem blacklist có rỗng không
     * @return true nếu rỗng
     */
    public boolean isEmpty() {
        return blockedHosts.isEmpty();
    }
}