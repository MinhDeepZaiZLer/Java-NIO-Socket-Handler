// File: src/com/proxy/main/ClientHandler.java

package com.proxy.main;

import com.proxy.cache.CacheManager;
import com.proxy.core.HttpRequestProcessor;
import com.proxy.io.ConnectionEstablisher;
import com.proxy.io.TunnelingHelper;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final CacheManager cacheManager;

    // ClientHandler nhận các Dependency (CacheManager) qua Constructor (Dependency Injection)
    public ClientHandler(Socket clientSocket, CacheManager cacheManager) {
        this.clientSocket = clientSocket;
        this.cacheManager = cacheManager;
    }

    @Override
    public void run() {
        try {
            // Khởi tạo các Service tầng dưới (IO)
            ConnectionEstablisher connectionEstablisher = new ConnectionEstablisher();
            TunnelingHelper tunnelingHelper = new TunnelingHelper();
            
            // Khởi tạo Core Processor
            HttpRequestProcessor processor = new HttpRequestProcessor(cacheManager, connectionEstablisher, tunnelingHelper);
            
            // Giao phó logic xử lý chính cho tầng Core
            processor.process(clientSocket);

        } catch (Exception e) {
            System.err.println("   [ERROR] Connection closed unexpectedly: " + e.getMessage());
        } finally {
            try {
                if (!clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException ignored) {}
        }
    }
}