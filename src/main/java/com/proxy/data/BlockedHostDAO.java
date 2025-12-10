package com.proxy.data;

import java.sql.*;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;

public class BlockedHostDAO {
    
    // Database configuration
    private static final String URL = "jdbc:postgresql://localhost:5432/proxyserver_db";
    private static final String USER = "postgres";
    private static final String PASSWORD = "minhnguyen1A";
    
    // Connection pooling (simple implementation)
    private static final int MAX_CONNECTIONS = 10;
    
    public BlockedHostDAO() {
        initializeDatabase();
    }
    
    /**
     * Get database connection
     */
    private Connection getConnection() throws SQLException {
        try {
            // Load PostgreSQL driver
            Class.forName("org.postgresql.Driver");
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (ClassNotFoundException e) {
            throw new SQLException("PostgreSQL JDBC Driver not found", e);
        }
    }
    
    /**
     * Initialize database table if not exists
     */
    private void initializeDatabase() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS blocked_hosts (
                id SERIAL PRIMARY KEY,
                host_name VARCHAR(255) NOT NULL UNIQUE,
                added_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                block_count INTEGER DEFAULT 0,
                notes TEXT
            );
            
            CREATE INDEX IF NOT EXISTS idx_host_name ON blocked_hosts(host_name);
            CREATE INDEX IF NOT EXISTS idx_added_on ON blocked_hosts(added_on DESC);
        """;
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.execute(createTableSQL);
            System.out.println("   [DATABASE] PostgreSQL table initialized successfully");
            
        } catch (SQLException e) {
            System.err.println("   [DB ERROR] Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Load all blocked hosts from database
     */
    public Set<String> loadAllHosts() {
        Set<String> hosts = new HashSet<>();
        String SQL = "SELECT host_name FROM blocked_hosts ORDER BY host_name";
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SQL)) {
            
            while (rs.next()) {
                hosts.add(rs.getString("host_name").toLowerCase());
            }
            
            System.out.println("   [DATABASE] Loaded " + hosts.size() + " blocked hosts from PostgreSQL");
            
        } catch (SQLException e) {
            System.err.println("   [DB ERROR] Failed to load blacklist: " + e.getMessage());
            e.printStackTrace();
        }
        
        return hosts;
    }
    
    /**
     * Add a host to blacklist
     */
    public void addHost(String hostName) {
        String SQL = "INSERT INTO blocked_hosts(host_name, added_on) VALUES (?, NOW()) " +
                    "ON CONFLICT (host_name) DO UPDATE SET updated_on = NOW()";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SQL)) {
            
            pstmt.setString(1, hostName.toLowerCase());
            int affected = pstmt.executeUpdate();
            
            if (affected > 0) {
                System.out.println("   [DATABASE] Added/Updated host: " + hostName);
            }
            
        } catch (SQLException e) {
            System.err.println("   [DB ERROR] Failed to add host: " + e.getMessage());
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }
    
    /**
     * Remove a host from blacklist
     */
    public void removeHost(String hostName) throws SQLException {
        String SQL = "DELETE FROM blocked_hosts WHERE host_name = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SQL)) {
            
            pstmt.setString(1, hostName.toLowerCase());
            int affected = pstmt.executeUpdate();
            
            if (affected > 0) {
                System.out.println("   [DATABASE] Removed host: " + hostName);
            } else {
                System.out.println("   [DATABASE] Host not found: " + hostName);
            }
            
        } catch (SQLException e) {
            System.err.println("   [DB ERROR] Failed to remove host: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Remove all hosts from blacklist
     */
    public int removeAllHosts() throws SQLException {
        String SQL = "DELETE FROM blocked_hosts";
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            int affected = stmt.executeUpdate(SQL);
            System.out.println("   [DATABASE] Cleared " + affected + " hosts from blacklist");
            return affected;
            
        } catch (SQLException e) {
            System.err.println("   [DB ERROR] Failed to clear blacklist: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Check if host exists in blacklist
     */
    public boolean exists(String hostName) {
        String SQL = "SELECT COUNT(*) FROM blocked_hosts WHERE host_name = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SQL)) {
            
            pstmt.setString(1, hostName.toLowerCase());
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            
        } catch (SQLException e) {
            System.err.println("   [DB ERROR] Failed to check host existence: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Get total count of blocked hosts
     */
    public int getCount() {
        String SQL = "SELECT COUNT(*) FROM blocked_hosts";
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SQL)) {
            
            if (rs.next()) {
                return rs.getInt(1);
            }
            
        } catch (SQLException e) {
            System.err.println("   [DB ERROR] Failed to get count: " + e.getMessage());
        }
        
        return 0;
    }
    
    /**
     * Search hosts by pattern
     */
    public Set<String> searchHosts(String pattern) {
        Set<String> hosts = new HashSet<>();
        String SQL = "SELECT host_name FROM blocked_hosts WHERE host_name LIKE ? ORDER BY host_name";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SQL)) {
            
            pstmt.setString(1, "%" + pattern.toLowerCase() + "%");
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                hosts.add(rs.getString("host_name"));
            }
            
        } catch (SQLException e) {
            System.err.println("   [DB ERROR] Failed to search hosts: " + e.getMessage());
        }
        
        return hosts;
    }
    
    /**
     * Get detailed information about a host
     */
    public Map<String, Object> getHostDetails(String hostName) {
        String SQL = "SELECT * FROM blocked_hosts WHERE host_name = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SQL)) {
            
            pstmt.setString(1, hostName.toLowerCase());
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                Map<String, Object> details = new HashMap<>();
                details.put("id", rs.getInt("id"));
                details.put("host_name", rs.getString("host_name"));
                details.put("added_on", rs.getTimestamp("added_on"));
                details.put("updated_on", rs.getTimestamp("updated_on"));
                details.put("block_count", rs.getInt("block_count"));
                details.put("notes", rs.getString("notes"));
                return details;
            }
            
        } catch (SQLException e) {
            System.err.println("   [DB ERROR] Failed to get host details: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Increment block count for a host
     */
    public void incrementBlockCount(String hostName) {
        String SQL = "UPDATE blocked_hosts SET block_count = block_count + 1, " +
                    "updated_on = NOW() WHERE host_name = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SQL)) {
            
            pstmt.setString(1, hostName.toLowerCase());
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            System.err.println("   [DB ERROR] Failed to increment block count: " + e.getMessage());
        }
    }
    
    /**
     * Batch add hosts (for import functionality)
     */
    public int addHostsBatch(Set<String> hostNames) {
        if (hostNames == null || hostNames.isEmpty()) {
            return 0;
        }
        
        String SQL = "INSERT INTO blocked_hosts(host_name, added_on) VALUES (?, NOW()) " +
                    "ON CONFLICT (host_name) DO NOTHING";
        int successCount = 0;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SQL)) {
            
            conn.setAutoCommit(false);
            
            for (String hostName : hostNames) {
                pstmt.setString(1, hostName.toLowerCase().trim());
                pstmt.addBatch();
            }
            
            int[] results = pstmt.executeBatch();
            conn.commit();
            
            for (int result : results) {
                if (result > 0) {
                    successCount++;
                }
            }
            
            System.out.println("   [DATABASE] Batch added " + successCount + " hosts");
            
        } catch (SQLException e) {
            System.err.println("   [DB ERROR] Failed to batch add hosts: " + e.getMessage());
            e.printStackTrace();
        }
        
        return successCount;
    }
    
    /**
     * Update notes for a host
     */
    public void updateNotes(String hostName, String notes) {
        String SQL = "UPDATE blocked_hosts SET notes = ?, updated_on = NOW() WHERE host_name = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SQL)) {
            
            pstmt.setString(1, notes);
            pstmt.setString(2, hostName.toLowerCase());
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            System.err.println("   [DB ERROR] Failed to update notes: " + e.getMessage());
        }
    }
    
    /**
     * Get statistics about blocked hosts
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        String SQL = """
            SELECT 
                COUNT(*) as total_hosts,
                SUM(block_count) as total_blocks,
                MAX(added_on) as latest_addition,
                MIN(added_on) as oldest_entry
            FROM blocked_hosts
        """;
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SQL)) {
            
            if (rs.next()) {
                stats.put("total_hosts", rs.getInt("total_hosts"));
                stats.put("total_blocks", rs.getLong("total_blocks"));
                stats.put("latest_addition", rs.getTimestamp("latest_addition"));
                stats.put("oldest_entry", rs.getTimestamp("oldest_entry"));
            }
            
        } catch (SQLException e) {
            System.err.println("   [DB ERROR] Failed to get statistics: " + e.getMessage());
        }
        
        return stats;
    }
    
    /**
     * Get most blocked hosts
     */
    public Map<String, Integer> getTopBlockedHosts(int limit) {
        Map<String, Integer> topHosts = new HashMap<>();
        String SQL = "SELECT host_name, block_count FROM blocked_hosts " +
                    "WHERE block_count > 0 ORDER BY block_count DESC LIMIT ?";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SQL)) {
            
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                topHosts.put(rs.getString("host_name"), rs.getInt("block_count"));
            }
            
        } catch (SQLException e) {
            System.err.println("   [DB ERROR] Failed to get top blocked hosts: " + e.getMessage());
        }
        
        return topHosts;
    }
    
    /**
     * Test database connection
     */
    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            System.err.println("   [DB ERROR] Connection test failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Close all connections (cleanup)
     */
    public void cleanup() {
        // In a real application with connection pooling, close the pool here
        System.out.println("   [DATABASE] Cleanup completed");
    }
}