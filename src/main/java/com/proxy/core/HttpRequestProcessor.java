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
        // Port mặc định cho HTTPS là 443
        int port = (hostPort.length > 1) ? Integer.parseInt(hostPort[1]) : 443;

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

            // Bước 1: Kiểm tra Cache
            byte[] cachedData = cacheManager.get(urlString);
            if (cachedData != null) {
                clientOut.write(cachedData);
                clientOut.flush();
                return;
            }

            // Bước 2: Cache MISS - Phân tích URL và kết nối Server đích
            URL url = new URL(urlString);
            String host = url.getHost();
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
            byte[] buffer = new byte[8192];
            int bytesRead;

            try {
                // Vòng lặp nhận dữ liệu (sẽ thoát sau 10s timeout nếu keep-alive)
                while ((bytesRead = serverIn.read(buffer)) != -1) {
                    fullResponse.write(buffer, 0, bytesRead);
                }
            } catch (SocketTimeoutException ignored) {
                // Thoát khỏi vòng lặp Keep-Alive, đây là hành vi mong muốn.
            }

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
