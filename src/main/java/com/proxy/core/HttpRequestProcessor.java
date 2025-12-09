package com.proxy.core;

import com.proxy.cache.CacheManager;
import com.proxy.io.ConnectionEstablisher;
import com.proxy.io.TunnelingHelper;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;

public class HttpRequestProcessor {
    private final CacheManager cacheManager;
    private final ConnectionEstablisher connectionEstablisher;
    private final TunnelingHelper tunnelingHelper;

    public HttpRequestProcessor(CacheManager cacheManager, ConnectionEstablisher connectionEstablisher,
            TunnelingHelper tunnelingHelper) {
        this.cacheManager = cacheManager;
        this.connectionEstablisher = connectionEstablisher;
        this.tunnelingHelper = tunnelingHelper;
    }

    // --- PHƯƠNG THỨC XỬ LÝ CHÍNH ĐƯỢC GỌI TỪ CLIENTHANDLER ---
    public void process(Socket clientSocket) throws IOException, InterruptedException {
        // Dùng try-with-resources để đóng streams tự động
        try (InputStream clientInStream = clientSocket.getInputStream();
                OutputStream clientOutStream = clientSocket.getOutputStream();
                // Sử dụng BufferedReader để đọc dòng đầu tiên
                BufferedReader clientIn = new BufferedReader(new InputStreamReader(clientInStream))) {

            String requestLine = clientIn.readLine();
            if (requestLine == null || requestLine.isEmpty())
                return;

            String[] parts = requestLine.split(" ");
            String method = parts[0];
            String urlString = parts[1];

            System.out.println("   [CORE] Method: " + method + ", URL: " + urlString);

            if (method.equalsIgnoreCase("CONNECT")) {
                // Xử lý HTTPS (Tunneling)
                handleHttpsTunnel(urlString, clientOutStream, clientSocket);
            } else {
                // Xử lý HTTP (Caching và Forwarding)
                handleHttpRequest(requestLine, clientIn, clientOutStream);
            }

        } catch (IOException e) {
            // Đây thường là lỗi kết nối đóng đột ngột (Connection reset)
            System.err.println("   [ERROR] Connection closed unexpectedly during process: " + e.getMessage());
        }
    }

    private void handleHttpsTunnel(String urlString, OutputStream clientOut, Socket clientSocket)
            throws IOException, InterruptedException {
        String[] hostPort = urlString.split(":");
        String host = hostPort[0];
        int port = (hostPort.length > 1) ? Integer.parseInt(hostPort[1]) : 443;

        // --- BƯỚC MỚI: KIỂM TRA BLACKLIST CHO HTTPS ---
        // Gọi BlacklistManager đã kết nối với PostgreSQL
        if (com.proxy.cache.BlacklistManager.getInstance().isBlocked(host)) {
            // Gửi phản hồi 403 Proxy về trình duyệt
            String forbiddenResponse = "HTTP/1.1 403 Forbidden\r\nProxy-agent: Clean-Java-Proxy\r\n\r\n";
            clientOut.write(forbiddenResponse.getBytes());
            clientOut.flush();
            System.out.println("   [SECURITY] Blocked access to: " + host + " (HTTPS)");
            return; // Dừng xử lý
        }
        // --- KẾT THÚC KIỂM TRA ---
        Socket serverSocket = null;
        try {
            // Bước 1: Kết nối Server đích (có thể lỗi nếu Server đích không phản hồi)
            System.out.println("   [DEBUG-1] Attempting to connect Server đích: " + host + ":" + port);
            serverSocket = connectionEstablisher.establish(host, port);
            System.out.println("   [DEBUG-2] Connection established successfully.");

            // Bước 2: Báo cho Client biết Tunnel đã sẵn sàng (200 OK)
            String connectResponse = "HTTP/1.1 200 Connection Established\r\nProxy-agent: Clean-Java-Proxy\r\n\r\n";
            clientOut.write(connectResponse.getBytes());
            clientOut.flush();
            System.out.println("   [DEBUG-3] 200 OK sent to Client. Starting tunnel...");

            // Bước 3: Tạo Tunnel 2 chiều và chờ chúng hoàn tất
            tunnelingHelper.createTunnel(clientSocket, serverSocket);
            System.out.println("   [DEBUG-4] Tunnel completed and sockets closed.");

        } catch (IOException e) {
            // Gửi lỗi 503 về Client nếu không thể kết nối Server đích
            String errorResponse = "HTTP/1.1 503 Service Unavailable\r\n\r\n";
            try {
                clientOut.write(errorResponse.getBytes());
                clientOut.flush();
            } catch (IOException ignored) {
            }

            System.err.println("   [CRITICAL ERROR] Tunnel failed at connection or transfer: " + e.getMessage());
        } finally {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        }
    }

    private void handleHttpRequest(String requestLine, BufferedReader clientIn, OutputStream clientOut)
            throws IOException {

        // Khai báo socket ngoài khối try-catch để đóng socket trong finally
        Socket serverSocket = null;

        try {
            String urlString = requestLine.split(" ")[1];
            URL url = new URL(urlString);
            String host = url.getHost();
            if (com.proxy.cache.BlacklistManager.getInstance().isBlocked(host)) {
                String forbiddenMessage = "HTTP/1.1 403 Forbidden\r\nContent-Type: text/plain\r\nContent-Length: 13\r\n\r\nAccess Denied!";
                clientOut.write(forbiddenMessage.getBytes());
                clientOut.flush();
                System.out.println("   [SECURITY] Blocked access to: " + host + " (HTTP)");
                return; // Dừng xử lý
            }
            // Bước 1: Kiểm tra Cache
            byte[] cachedData = cacheManager.get(urlString);
            if (cachedData != null) {
                clientOut.write(cachedData);
                clientOut.flush();
                return;
            }

            // Bước 2: Cache MISS - Phân tích URL và kết nối Server đích
            int port = url.getPort() == -1 ? 80 : url.getPort();

            // Kết nối Server đích (sẽ có timeout 10s)
            serverSocket = connectionEstablisher.establish(host, port);
            OutputStream serverOut = serverSocket.getOutputStream();
            InputStream serverIn = serverSocket.getInputStream();

            // --- BƯỚC GỬI REQUEST ---
            // 1. Sửa Request Line (chỉ gửi path)
            String path = url.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            String newRequestLine = requestLine.replace(urlString, path);

            // 2. Gửi Request Line và Headers
            serverOut.write((newRequestLine + "\r\n").getBytes());

            String headerLine;
            while (!(headerLine = clientIn.readLine()).isEmpty()) {
                serverOut.write((headerLine + "\r\n").getBytes());
            }
            serverOut.write('\r');
            serverOut.write('\n'); // Kết thúc Headers bằng dòng trống
            serverOut.flush();

            System.out.println("   [DEBUG-REQUEST] Request completely forwarded to origin server.");

            // --- BƯỚC NHẬN RESPONSE ---
            ByteArrayOutputStream fullResponse = new ByteArrayOutputStream();
            // Lớp serverIn (InputStream) đã có
            byte[] buffer = new byte[8192];
            int bytesRead;

            // *Sử dụng BufferedReader để đọc Header (vì nó cần đọc từng dòng)*
            BufferedReader serverReader = new BufferedReader(new InputStreamReader(serverIn));
            String line;
            int contentLength = -1;
            boolean isChunked = false;
            boolean headerFound = false;

            // Đọc và phân tích Header
            while ((line = serverReader.readLine()) != null) {
                
                fullResponse.write(headerLine.getBytes());

                if (line.toLowerCase().startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
                    } catch (NumberFormatException ignored) {
                    }
                } else if (line.toLowerCase().startsWith("transfer-encoding:")
                        && line.toLowerCase().contains("chunked")) {
                    isChunked = true;
                }

                if (line.isEmpty()) {
                    headerFound = true;
                    break; // Kết thúc Header
                }
            }

            // Nếu Header không tìm thấy (lỗi kết nối), thoát
            if (!headerFound) {
                System.err.println("   [ERROR] Failed to read HTTP Response Headers.");
                throw new IOException("Failed to read HTTP Response Headers.");
            }

            // --- BƯỚC B2: ĐỌC BODY DỰA TRÊN HEADER HOẶC TIMEOUT ---

            if (isChunked) {
                // Để xử lý Chunked, chúng ta cần dùng thư viện hoặc chuyển sang NIO.
                // Tạm thời, dùng timeout. Cần đóng serverReader để không làm hỏng stream.
                System.err.println("   [WARNING] Encountered Chunked Encoding. Using Socket Timeout Fallback.");
                // THỰC HIỆN LOGIC TIMEOUT FALLBACK CŨ:
                try {
                    while ((bytesRead = serverIn.read(buffer)) != -1) {
                        fullResponse.write(buffer, 0, bytesRead);
                    }
                } catch (SocketTimeoutException ignored) {
                    /* Exit loop */ }

            } else if (contentLength != -1) {
                // Đọc chính xác số byte (Content-Length)
                System.out.println("   [DEBUG-READ] Reading exactly " + contentLength + " bytes (Content-Length).");
                int totalBytesRead = 0;

                // Vòng lặp đọc Body: LƯU Ý: Phải đọc từ InputStream gốc vì BufferedReader đã
                // hoàn thành vai trò của nó.
                // Dữ liệu còn lại trong InputStream là Body

                while (totalBytesRead < contentLength && (bytesRead = serverIn.read(buffer)) != -1) {
                    int bytesToRead = Math.min(bytesRead, contentLength - totalBytesRead);
                    fullResponse.write(buffer, 0, bytesToRead);
                    totalBytesRead += bytesToRead;

                    if (totalBytesRead >= contentLength) {
                        break; // Đã đọc đủ số byte
                    }
                }

            } else {
                // Không có Content-Length và không phải Chunked: Dùng timeout
                System.err.println("   [WARNING] No Content-Length or Chunked. Using Socket Timeout Fallback.");
                // THỰC HIỆN LOGIC TIMEOUT FALLBACK CŨ:
                try {
                    while ((bytesRead = serverIn.read(buffer)) != -1) {
                        fullResponse.write(buffer, 0, bytesRead);
                    }
                } catch (SocketTimeoutException ignored) {
                    /* Exit loop */ }
            }

            // Chú ý: Đảm bảo không gọi serverReader.close() để tránh đóng serverIn quá sớm.
            // Dù vậy, việc trộn BufferedReader và InputStream gốc vẫn tiềm ẩn rủi ro trong
            // Java.
            // Tuy nhiên, đây là cách triển khai Task B chính xác nhất trong mô hình
            // Blocking I/O.

            byte[] responseData = fullResponse.toByteArray();

            // Bước 4: Lưu vào Cache và gửi về Client
            if (requestLine.startsWith("GET") && responseData.length > 0) {
                cacheManager.put(urlString, responseData);
            }

            clientOut.write(responseData);
            clientOut.flush();

        } catch (IOException e) {
            System.err.println("   [ERROR] HTTP processing failed: " + e.getMessage());
        } finally {
            if (serverSocket != null)
                serverSocket.close();
        }
    }
}
