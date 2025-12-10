package com.proxy.admin;

import com.proxy.cache.BlacklistManager;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableCellRenderer; // Import mới cho căn chỉnh cột
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.JTableHeader; // Import mới cho Header

import java.awt.*;
import java.awt.event.*;
import java.util.Set;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;

public class AdminApp extends JFrame {

    private final BlacklistManager blacklistManager;
    private JTable blacklistTable;
    private DefaultTableModel tableModel;
    private JTextField hostInputField;
    private JTextField searchField;

    // Statistics components
    private JLabel statusLabel;
    private JLabel totalBlockedLabel;
    private JLabel cacheHitsLabel;
    private JLabel uptimeLabel;
    private JLabel requestCountLabel;

    // Stats data
    private int cacheHits = 0;
    private int totalRequests = 0;
    private long startTime;
    private volatile int activeConnections = 0;
    private volatile long totalBytesTransferred = 0;

    // Color scheme
    private final Color PRIMARY_COLOR = new Color(41, 128, 185);
    private final Color SUCCESS_COLOR = new Color(39, 174, 96);
    private final Color DANGER_COLOR = new Color(231, 76, 60);
    private final Color DARK_BG = new Color(44, 62, 80);
    private final Color LIGHT_BG = new Color(236, 240, 241);
    private final Color CARD_BG = Color.WHITE;

    public AdminApp(BlacklistManager blacklistManager) {
        this.blacklistManager = blacklistManager;
        this.startTime = System.currentTimeMillis();

        setupUI();
        updateBlacklistDisplay();
        startUptimeTimer();
    }

    private void setupUI() {

        setTitle("Proxy Server - Admin Control Panel");
        setSize(1200, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));

        // Main container with padding
        JPanel mainContainer = new JPanel(new BorderLayout(10, 10));
        mainContainer.setBackground(LIGHT_BG);
        mainContainer.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Top Section - Statistics Dashboard
        mainContainer.add(createStatisticsPanel(), BorderLayout.NORTH);

        // Center Section - Blacklist Management
        mainContainer.add(createBlacklistPanel(), BorderLayout.CENTER);

        // Bottom Section - Action Buttons
        mainContainer.add(createActionPanel(), BorderLayout.SOUTH);

        add(mainContainer);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private JPanel createStatisticsPanel() {
        JPanel statsPanel = new JPanel(new GridLayout(1, 5, 15, 0));
        statsPanel.setBackground(LIGHT_BG);
        statsPanel.setBorder(new EmptyBorder(0, 0, 15, 0));

        // Status Card
        statusLabel = new JLabel("Running");
        JPanel statusCard = createStatCard("Proxy Status", statusLabel, SUCCESS_COLOR, "●");

        // Total Blocked Card
        totalBlockedLabel = new JLabel("0");
        JPanel blockedCard = createStatCard("Blocked Hosts", totalBlockedLabel, DANGER_COLOR, "🛡");

        // Cache Hits Card
        cacheHitsLabel = new JLabel("0 (0.00%)");
        JPanel cacheCard = createStatCard("Cache Performance", cacheHitsLabel, PRIMARY_COLOR, "⚡");

        // Uptime Card
        uptimeLabel = new JLabel("00:00:00");
        JPanel uptimeCard = createStatCard("Uptime", uptimeLabel, new Color(155, 89, 182), "⏱");

        // Request Count Card
        requestCountLabel = new JLabel("0");
        JPanel requestCard = createStatCard("Total Requests", requestCountLabel, new Color(52, 152, 219), "📊");

        statsPanel.add(statusCard);
        statsPanel.add(blockedCard);
        statsPanel.add(cacheCard);
        statsPanel.add(uptimeCard);
        statsPanel.add(requestCard);

        return statsPanel;
    }

    private JPanel createStatCard(String title, JLabel valueLabel, Color accentColor, String icon) {
        JPanel card = new JPanel(new BorderLayout(10, 5));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(220, 220, 220), 1, true),
                new EmptyBorder(15, 15, 15, 15)));

        // Icon and title
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        headerPanel.setBackground(CARD_BG);

        JLabel iconLabel = new JLabel(icon);
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 24));
        iconLabel.setForeground(accentColor);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        titleLabel.setForeground(new Color(127, 140, 141));

        headerPanel.add(iconLabel);
        headerPanel.add(titleLabel);

        // Value
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        valueLabel.setForeground(DARK_BG);
        JPanel valuePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        valuePanel.setBackground(CARD_BG);
        valuePanel.add(valueLabel);

        card.add(headerPanel, BorderLayout.NORTH);
        card.add(valuePanel, BorderLayout.CENTER);

        return card;
    }

    private JPanel createBlacklistPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(LIGHT_BG);

        // Header with search
        JPanel headerPanel = new JPanel(new BorderLayout(10, 0));
        headerPanel.setBackground(CARD_BG);
        headerPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JLabel titleLabel = new JLabel("🚫 Blacklist Management");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setForeground(DARK_BG);

        // Search panel
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        searchPanel.setBackground(CARD_BG);

        searchField = new JTextField(20);
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        searchField.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(189, 195, 199), 1, true),
                new EmptyBorder(5, 10, 5, 10)));

        JButton searchBtn = createStyledButton("Search", PRIMARY_COLOR, false);
        searchBtn.addActionListener(e -> performSearch());

        JButton clearSearchBtn = createStyledButton("Clear", new Color(149, 165, 166), false);
        clearSearchBtn.addActionListener(e -> {
            searchField.setText("");
            updateBlacklistDisplay();
        });

        searchPanel.add(new JLabel("Search: "));
        searchPanel.add(searchField);
        searchPanel.add(searchBtn);
        searchPanel.add(clearSearchBtn);

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(searchPanel, BorderLayout.EAST);

        // Table
        String[] columns = { "#", "Hostname", "Added Date", "Status", "Actions" };
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 4; // Only actions column editable
            }
            // Quan trọng: Tắt chỉnh sửa kiểu mặc định
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 4) return JButton.class;
                return String.class;
            }
        };

        blacklistTable = new JTable(tableModel);
        blacklistTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        blacklistTable.setRowHeight(35);
        blacklistTable.setShowGrid(false);
        blacklistTable.setIntercellSpacing(new Dimension(0, 0));
        blacklistTable.setSelectionBackground(new Color(52, 152, 219, 50));
        
        // Cải thiện header: Tăng độ dày chữ và căn giữa
        JTableHeader header = blacklistTable.getTableHeader();
        header.setFont(new Font("Segoe UI", Font.BOLD, 13));
        header.setBackground(new Color(245, 245, 245));
        header.setForeground(DARK_BG);

        // Column widths
        blacklistTable.getColumnModel().getColumn(0).setMaxWidth(50);
        blacklistTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        blacklistTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        blacklistTable.getColumnModel().getColumn(4).setPreferredWidth(100); // Tăng 1 chút

        // Căn chỉnh nội dung cột
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        DefaultTableCellRenderer leftRenderer = new DefaultTableCellRenderer();
        leftRenderer.setHorizontalAlignment(JLabel.LEFT);
        
        blacklistTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer); // #
        blacklistTable.getColumnModel().getColumn(1).setCellRenderer(leftRenderer); // Hostname
        blacklistTable.getColumnModel().getColumn(2).setCellRenderer(centerRenderer); // Added Date
        blacklistTable.getColumnModel().getColumn(3).setCellRenderer(centerRenderer); // Status
        
        // Add delete button to actions column
        blacklistTable.getColumn("Actions").setCellRenderer(new ButtonRenderer());
        blacklistTable.getColumn("Actions").setCellEditor(new ButtonEditor(new JCheckBox()));

        JScrollPane scrollPane = new JScrollPane(blacklistTable);
        scrollPane.setBorder(new LineBorder(new Color(220, 220, 220), 1));
        scrollPane.getViewport().setBackground(Color.WHITE);

        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBackground(CARD_BG);
        tablePanel.setBorder(new EmptyBorder(0, 15, 15, 15));
        tablePanel.add(scrollPane);

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(tablePanel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createActionPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(CARD_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(220, 220, 220), 1, true),
                new EmptyBorder(15, 15, 15, 15)));

        // Add host section
        JPanel addPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        addPanel.setBackground(CARD_BG);

        JLabel addLabel = new JLabel("Add to Blacklist:");
        addLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

        hostInputField = new JTextField(30);
        hostInputField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        hostInputField.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(189, 195, 199), 1, true),
                new EmptyBorder(5, 10, 5, 10)));

        JButton addBtn = createStyledButton("➕ Add Host", SUCCESS_COLOR, true);
        addBtn.addActionListener(e -> addHostAction());

        JButton importBtn = createStyledButton("📥 Import List", PRIMARY_COLOR, false);
        importBtn.addActionListener(e -> importBlacklist());

        JButton exportBtn = createStyledButton("📤 Export List", PRIMARY_COLOR, false);
        exportBtn.addActionListener(e -> exportBlacklist());

        addPanel.add(addLabel);
        addPanel.add(hostInputField);
        addPanel.add(addBtn);
        addPanel.add(new JSeparator(SwingConstants.VERTICAL));
        addPanel.add(importBtn);
        addPanel.add(exportBtn);

        // Action buttons section
        JPanel actionBtnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionBtnPanel.setBackground(CARD_BG);

        JButton refreshBtn = createStyledButton("🔄 Refresh", PRIMARY_COLOR, false);
        refreshBtn.addActionListener(e -> updateBlacklistDisplay());

        JButton clearAllBtn = createStyledButton("🗑 Clear All", DANGER_COLOR, false);
        clearAllBtn.addActionListener(e -> clearAllHosts());

        actionBtnPanel.add(refreshBtn);
        actionBtnPanel.add(clearAllBtn);

        panel.add(addPanel, BorderLayout.WEST);
        panel.add(actionBtnPanel, BorderLayout.EAST);

        return panel;
    }

    private JButton createStyledButton(String text, Color bgColor, boolean isPrimary) {
        JButton button = new JButton(text) {
            @Override
            public boolean isOpaque() {
                return true;
            }
        };

        button.setFont(new Font("Segoe UI", isPrimary ? Font.BOLD : Font.PLAIN, 13));
        button.setForeground(Color.WHITE);
        button.setBackground(bgColor);
        button.setBorder(new EmptyBorder(8, 20, 8, 20));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // BẮT BUỘC thêm dòng này để Windows L&F không ghi đè màu
        button.setContentAreaFilled(false);
        button.setOpaque(true);

        // Hover effect mạnh hơn
        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                button.setBackground(bgColor.darker());
            }

            public void mouseExited(MouseEvent e) {
                button.setBackground(bgColor);
            }
        });

        return button;
    }

    private void addHostAction() {
        String host = hostInputField.getText().trim();
        if (host.isEmpty()) {
            showMessage("Host field cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            blacklistManager.addHost(host);
            hostInputField.setText("");
            updateBlacklistDisplay();
            showMessage("Host '" + host + "' added successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            showMessage("Database Error: Could not add host.", "Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void deleteHost(String host) {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to remove '" + host + "' from blacklist?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                blacklistManager.removeHost(host);
                updateBlacklistDisplay();
                showMessage("Host removed successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                showMessage("Error removing host.", "Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }
    }

    private void clearAllHosts() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to clear ALL blacklist entries?",
                "Confirm Clear All",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                Set<String> hosts = blacklistManager.getBlockedHosts();
                for (String host : hosts) {
                    blacklistManager.removeHost(host);
                }
                updateBlacklistDisplay();
                showMessage("All hosts cleared!", "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                showMessage("Error clearing hosts.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void performSearch() {
        String searchTerm = searchField.getText().trim().toLowerCase();
        if (searchTerm.isEmpty()) {
            updateBlacklistDisplay();
            return;
        }

        Set<String> allHosts = blacklistManager.getBlockedHosts();
        List<String> filteredHosts = new ArrayList<>();

        for (String host : allHosts) {
            if (host.toLowerCase().contains(searchTerm)) {
                filteredHosts.add(host);
            }
        }

        updateTableWithHosts(filteredHosts);
    }

    private void updateBlacklistDisplay() {
        Set<String> hosts = blacklistManager.getBlockedHosts();
        updateTableWithHosts(new ArrayList<>(hosts));
        totalBlockedLabel.setText(String.valueOf(hosts.size()));
    }

    private void updateTableWithHosts(List<String> hosts) {
        tableModel.setRowCount(0);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        for (int i = 0; i < hosts.size(); i++) {
            String host = hosts.get(i);

            // Get detailed info if available (giả định)
            String addedDate = dateFormat.format(new Date());

            Object[] row = {
                    i + 1,
                    host,
                    addedDate,
                    "🔴 Blocked",
                    "Remove" // Thay đổi text từ "Delete" thành "Remove"
            };
            tableModel.addRow(row);
        }
    }

    private void importBlacklist() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Import Blacklist");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Text Files", "txt"));

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                String filePath = fileChooser.getSelectedFile().getAbsolutePath();
                int count = blacklistManager.importFromFile(filePath);
                updateBlacklistDisplay();
                showMessage("Successfully imported " + count + " hosts!", "Import Success",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                showMessage("Failed to import: " + ex.getMessage(), "Import Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }
    }

    private void exportBlacklist() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Blacklist");
        fileChooser.setSelectedFile(new java.io.File("blacklist_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".txt"));
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Text Files", "txt"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                String filePath = fileChooser.getSelectedFile().getAbsolutePath();
                if (!filePath.endsWith(".txt")) {
                    filePath += ".txt";
                }
                blacklistManager.exportToFile(filePath);
                showMessage("Blacklist exported successfully to:\n" + filePath, "Export Success",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                showMessage("Failed to export: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }
    }

    private void startUptimeTimer() {
        Timer timer = new Timer(1000, e -> {
            long uptime = System.currentTimeMillis() - startTime;
            long hours = uptime / (1000 * 60 * 60);
            long minutes = (uptime / (1000 * 60)) % 60;
            long seconds = (uptime / 1000) % 60;
            uptimeLabel.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
        });
        timer.start();
    }

    // Method to update cache statistics from outside
    public void updateCacheStats(int hits, int total) {
        SwingUtilities.invokeLater(() -> {
            this.cacheHits = hits;
            this.totalRequests = total;
            double hitRate = total > 0 ? (hits * 100.0 / total) : 0;
            cacheHitsLabel.setText(String.format("%d (%.2f%%)", hits, hitRate));
            requestCountLabel.setText(String.valueOf(total));
        });
    }

    // Method to update active connections
    public void updateActiveConnections(int count) {
        SwingUtilities.invokeLater(() -> {
            this.activeConnections = count;
        });
    }

    // Method to update status
    public void updateProxyStatus(boolean isRunning) {
        SwingUtilities.invokeLater(() -> {
            if (isRunning) {
                statusLabel.setText("Running");
                statusLabel.setForeground(SUCCESS_COLOR);
            } else {
                statusLabel.setText("Stopped");
                statusLabel.setForeground(DANGER_COLOR);
            }
        });
    }

    // Method to increment request count (called from ProxyServer)
    public synchronized void incrementRequestCount() {
        SwingUtilities.invokeLater(() -> {
            this.totalRequests++;
            requestCountLabel.setText(String.valueOf(totalRequests));
        });
    }

    private void showMessage(String message, String title, int messageType) {
        JOptionPane.showMessageDialog(this, message, title, messageType);
    }

    // === ButtonRenderer đã được điều chỉnh ===
    class ButtonRenderer extends JButton implements TableCellRenderer {
        public ButtonRenderer() {
            setOpaque(true);
            setText("Remove"); // Text mới
            setForeground(Color.WHITE);
            setBackground(DANGER_COLOR);
            setFont(new Font("Segoe UI", Font.BOLD, 12)); // Font đậm hơn
            setBorder(new EmptyBorder(5, 12, 5, 12)); // Padding lớn hơn
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFocusPainted(false);
            
            // Hover effect
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) {
                    setBackground(DANGER_COLOR.darker());
                }
    
                public void mouseExited(MouseEvent e) {
                    setBackground(DANGER_COLOR);
                }
            });
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            
            // Đảm bảo nút không bị mất màu khi được chọn
            if (isSelected) {
                setBackground(DANGER_COLOR.darker());
            } else {
                setBackground(DANGER_COLOR);
            }
            return this;
        }
    }

    // === ButtonEditor đã được điều chỉnh ===
    class ButtonEditor extends DefaultCellEditor {
        private JButton button;
        private String host;
        private boolean isPushed;

        public ButtonEditor(JCheckBox checkBox) {
            super(checkBox);
            setClickCountToStart(1);
            button = new JButton("Remove"); // Text mới
            button.setOpaque(true);
            button.setForeground(Color.WHITE);
            button.setBackground(DANGER_COLOR);
            button.setFont(new Font("Segoe UI", Font.BOLD, 12));
            button.setBorder(new EmptyBorder(5, 12, 5, 12));
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.setFocusPainted(false);


            button.addActionListener(e -> {
                isPushed = true;
                fireEditingStopped(); // Quan trọng: dừng edit ngay lập tức
            });
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                boolean isSelected, int row, int column) {

            host = (String) table.getValueAt(row, 1); // Lấy hostname từ cột 1

            if (isSelected) {
                button.setBackground(DANGER_COLOR.darker());
            } else {
                button.setBackground(DANGER_COLOR);
            }

            isPushed = false;
            return button;
        }

        @Override
        public Object getCellEditorValue() {
            if (isPushed) {
                SwingUtilities.invokeLater(() -> deleteHost(host)); // Dùng invokeLater để tránh deadlock
            }
            isPushed = false;
            return "Remove";
        }

        @Override
        public boolean stopCellEditing() {
            isPushed = false;
            return super.stopCellEditing();
        }

        @Override
        protected void fireEditingStopped() {
            super.fireEditingStopped();
        }
    }
}