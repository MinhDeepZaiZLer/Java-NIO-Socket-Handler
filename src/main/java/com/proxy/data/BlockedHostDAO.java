package com.proxy.data;

import java.sql.*;
import java.util.HashSet;
import java.util.Set;

public class BlockedHostDAO {
    // Thông tin kết nối DB (Nên đọc từ file config, nhưng tạm thời hardcode)
    private static final String URL = "jdbc:postgresql://localhost:5432/proxyserver_db";
    private static final String USER = "postgres";
    private static final String PASSWORD = "minhnguyen1A"; 

    // Hàm lấy kết nối
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
    
    // Hàm tải toàn bộ danh sách chặn từ DB
    public Set<String> loadAllHosts() {
        Set<String> hosts = new HashSet<>();
        String SQL = "SELECT host_name FROM blocked_hosts";
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SQL)) {
            
            while (rs.next()) {
                hosts.add(rs.getString("host_name").toLowerCase());
            }
        } catch (SQLException e) {
            // Xử lý lỗi SQL (ví dụ: DB chưa chạy)
            System.err.println("[DB ERROR] Failed to load blacklist: " + e.getMessage());
        }
        return hosts;
    }
    
    // Hàm thêm một Host mới vào DB
    public void addHost(String hostName) {
        String SQL = "INSERT INTO blocked_hosts(host_name, added_on) VALUES (?, NOW()) ON CONFLICT (host_name) DO NOTHING";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SQL)) {
            
            pstmt.setString(1, hostName.toLowerCase());
            pstmt.executeUpdate();
            System.out.println("[DB] Added host: " + hostName);
            
        } catch (SQLException e) {
            System.err.println("[DB ERROR] Failed to add host: " + e.getMessage());
        }
    }
    
    // ... (Thêm hàm deleteHost nếu cần)
}