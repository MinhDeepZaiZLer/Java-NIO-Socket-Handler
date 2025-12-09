
package com.proxy.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class TunnelingHelper {
    private static final int BUFFER_SIZE = 8192;

    // Inner class (hoặc nên là class riêng nếu muốn OOP hơn nữa) để chuyển tiếp byte
    private static class TransferData implements Runnable {
        private final InputStream input;
        private final OutputStream output;
        
        // Cần truyền trực tiếp stream, không cần truyền Socket nữa
        public TransferData(InputStream input, OutputStream output) { 
            this.input = input;
            this.output = output;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            try {
                // Chuyển tiếp byte liên tục cho đến khi luồng đóng hoặc lỗi
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                    output.flush();
                }
            } catch (IOException e) {
                // Đây là nơi kết nối bị ngắt (thường là Client/Server đích đóng kết nối)
                // KHÔNG cần làm gì cả, chỉ cần thoát khỏi luồng.
            } finally {
                // Đảm bảo đóng các luồng (Streams) để giải phóng tài nguyên I/O.
                // KHÔNG đóng Socket ở đây!
                try {
                    input.close();
                    output.close();
                } catch (IOException ignored) {}
            }
        }
    }
    
    // Phương thức chính khởi tạo và chạy 2 luồng chuyển tiếp (Vẫn giữ nguyên)
    public void createTunnel(Socket clientSocket, Socket serverSocket) throws InterruptedException, IOException {
        // Luồng 1: Client -> Server
        Thread clientToServer = new Thread(new TransferData(clientSocket.getInputStream(), serverSocket.getOutputStream()));
        // Luồng 2: Server -> Client
        Thread serverToClient = new Thread(new TransferData(serverSocket.getInputStream(), clientSocket.getOutputStream()));

        clientToServer.start();
        serverToClient.start();

        // Chờ cho đến khi một trong hai luồng kết thúc (do kết nối đóng/lỗi)
        clientToServer.join();
        serverToClient.join();
    }
}