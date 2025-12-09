package com.proxy.io;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException; 

public class ConnectionEstablisher {
    // Thêm Timeout 5 giây (5000ms)
    private static final int TIMEOUT_MS = 10000; 

    public Socket establish(String host, int port) throws IOException {
        System.out.println("   [IO] Establishing connection to " + host + ":" + port);
        
        Socket socket = new Socket(host, port);
        
        // --- THÊM SOCKET TIMEOUT TẠI ĐÂY ---
        socket.setSoTimeout(TIMEOUT_MS); 
        
        return socket;
    }
}