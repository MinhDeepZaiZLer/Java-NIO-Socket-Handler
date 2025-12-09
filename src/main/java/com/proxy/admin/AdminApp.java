package com.proxy.admin;

import com.proxy.cache.BlacklistManager;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Set;

public class AdminApp extends JFrame {

    private final BlacklistManager blacklistManager;
    private final JTextArea blockedListArea;
    private final JTextField hostInputField;

    public AdminApp(BlacklistManager blacklistManager) {
        this.blacklistManager = blacklistManager;
        
        setTitle("Proxy Server Admin Panel");
        setSize(500, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10)); // Layout chính

        // --- 1. Panel Thống kê (TOP) ---
        JPanel statsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statsPanel.add(new JLabel("Proxy Status: Running | "));
        statsPanel.add(new JLabel("Cache Hits: 0 (0.00%)")); // Sẽ được cập nhật sau
        add(statsPanel, BorderLayout.NORTH);

        // --- 2. Panel Quản lý (CENTER) ---
        JPanel managePanel = new JPanel(new BorderLayout());
        
        // Khu vực hiển thị danh sách chặn
        blockedListArea = new JTextArea("Loading blacklist...");
        blockedListArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(blockedListArea);
        managePanel.add(scrollPane, BorderLayout.CENTER);

        // Khu vực thêm Host (BOTTOM của managePanel)
        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        hostInputField = new JTextField();
        JButton addButton = new JButton("Add Host to Blacklist");
        
        // Logic khi nhấn nút Add
        addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addHostAction();
            }
        });

        inputPanel.add(new JLabel("Host (youtube.com): "), BorderLayout.WEST);
        inputPanel.add(hostInputField, BorderLayout.CENTER);
        inputPanel.add(addButton, BorderLayout.EAST);
        
        managePanel.add(inputPanel, BorderLayout.SOUTH);
        add(managePanel, BorderLayout.CENTER);

        // Hiển thị giao diện
        setVisible(true);
        // Tải dữ liệu ban đầu
        updateBlacklistDisplay();
    }

    private void addHostAction() {
        String host = hostInputField.getText().trim();
        if (host.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Host field cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            blacklistManager.addHost(host);
            hostInputField.setText("");
            updateBlacklistDisplay();
            JOptionPane.showMessageDialog(this, "Host '" + host + "' added successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "DB Error: Could not add host.", "Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void updateBlacklistDisplay() {
        // Cần BlacklistManager có hàm getBlockedHosts() trả về Set<String>
        Set<String> hosts = blacklistManager.getBlockedHosts(); 
        StringBuilder sb = new StringBuilder("--- CURRENT BLOCKED HOSTS (" + hosts.size() + ") ---\n");
        for (String host : hosts) {
            sb.append(host).append("\n");
        }
        blockedListArea.setText(sb.toString());
    }
}