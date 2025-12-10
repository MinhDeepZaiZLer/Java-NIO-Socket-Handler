package com.proxy.main;

import com.proxy.cache.CacheManager;
import com.proxy.core.usecase.ProxyRequestUseCase;
import com.proxy.core.usecase.ProxyRequestUseCase.HostPort;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class NioConnectionHandler {

  private static final int BUFFER_SIZE = 8192;
  private final ByteBuffer clientReadBuffer = ByteBuffer.allocate(BUFFER_SIZE);
  private final ByteBuffer clientWriteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
  private final ByteBuffer serverReadBuffer = ByteBuffer.allocate(BUFFER_SIZE);
  private final ByteBuffer serverWriteBuffer = ByteBuffer.allocate(BUFFER_SIZE);

  private final SelectionKey clientKey;
  private final Selector selector;
  private SocketChannel serverChannel;
  private SelectionKey serverKey;
  private HostPort targetHostPort;

  private final ProxyRequestUseCase useCase;
  private final CacheManager cacheManager;

  private enum State {
    READING_REQUEST_LINE, CONNECTING, FORWARDING
  }

  private State state = State.READING_REQUEST_LINE;
  private volatile boolean isClosed = false;

  public NioConnectionHandler(SelectionKey clientKey, ProxyRequestUseCase useCase, CacheManager cacheManager) {
    this.clientKey = clientKey;
    this.selector = clientKey.selector();
    this.clientKey.attach(this);
    this.useCase = useCase;
    this.cacheManager = cacheManager;
  }

  public SelectionKey getClientKey() {
    return clientKey;
  }

  public void handleRead() throws IOException {
    if (isClosed) return;

    SocketChannel clientChannel = (SocketChannel) clientKey.channel();
    int bytesRead;
    
    try {
      bytesRead = clientChannel.read(clientReadBuffer);
    } catch (IOException e) {
      closeConnection();
      throw e;
    }

    if (bytesRead == -1) {
      closeConnection();
      return;
    }

    if (bytesRead > 0) {
      if (state == State.READING_REQUEST_LINE) {
        String requestLine = extractRequestLineFromBuffer();
        if (requestLine != null) {
          targetHostPort = useCase.processInitialRequest(requestLine);

          if (targetHostPort == null) {
            sendForbiddenResponse(clientChannel, requestLine.startsWith("CONNECT"));
            closeConnection();
            return;
          }

          state = State.CONNECTING;
          
          // Clear buffer for CONNECT tunneling
          if (targetHostPort.isTunneling()) {
            clientReadBuffer.clear();
          }
          
          startNonBlockingConnect();
        }
      } else if (state == State.FORWARDING) {
        // Client -> Server: Transfer data with overflow protection
        clientReadBuffer.flip();

        if (clientReadBuffer.hasRemaining()) {
          // FIX: Check available space in serverWriteBuffer before putting
          int availableSpace = serverWriteBuffer.remaining();
          int dataToTransfer = Math.min(clientReadBuffer.remaining(), availableSpace);
          
          if (dataToTransfer > 0) {
            // Transfer only what fits
            int oldLimit = clientReadBuffer.limit();
            clientReadBuffer.limit(clientReadBuffer.position() + dataToTransfer);
            serverWriteBuffer.put(clientReadBuffer);
            clientReadBuffer.limit(oldLimit);
            
            // Enable server write
            if (serverKey != null && serverKey.isValid()) {
              serverKey.interestOps(serverKey.interestOps() | SelectionKey.OP_WRITE);
            } else {
              closeConnection();
              return;
            }
          }
          
          // If serverWriteBuffer is full, disable client read until it drains
          if (serverWriteBuffer.remaining() == 0 && clientKey.isValid()) {
            clientKey.interestOps(clientKey.interestOps() & ~SelectionKey.OP_READ);
          }
        }
        clientReadBuffer.compact();
      }
    }
  }

  public void handleWrite() throws IOException {
    if (isClosed) return;

    SocketChannel clientChannel = (SocketChannel) clientKey.channel();
    clientWriteBuffer.flip();
    
    try {
      clientChannel.write(clientWriteBuffer);
    } catch (IOException e) {
      closeConnection();
      throw e;
    }
    
    if (clientWriteBuffer.hasRemaining()) {
      clientWriteBuffer.compact();
    } else {
      clientWriteBuffer.clear();
      if (clientKey.isValid()) {
        clientKey.interestOps(clientKey.interestOps() & ~SelectionKey.OP_WRITE);
        
        // Re-enable server read if it was disabled due to full buffer
        if (serverKey != null && serverKey.isValid()) {
          serverKey.interestOps(serverKey.interestOps() | SelectionKey.OP_READ);
        }
      }
    }
  }

  public void handleServerRead(SelectionKey serverKey) throws IOException {
    if (isClosed) return;

    SocketChannel serverChannel = (SocketChannel) serverKey.channel();
    int bytesRead;
    
    try {
      bytesRead = serverChannel.read(serverReadBuffer);
    } catch (IOException e) {
      closeConnection();
      throw e;
    }

    if (bytesRead == -1) {
      closeConnection();
      return;
    }

    if (bytesRead > 0) {
      serverReadBuffer.flip();
      
      // FIX: Check available space in clientWriteBuffer before putting
      int availableSpace = clientWriteBuffer.remaining();
      int dataToTransfer = Math.min(serverReadBuffer.remaining(), availableSpace);
      
      if (dataToTransfer > 0) {
        // Transfer only what fits
        int oldLimit = serverReadBuffer.limit();
        serverReadBuffer.limit(serverReadBuffer.position() + dataToTransfer);
        clientWriteBuffer.put(serverReadBuffer);
        serverReadBuffer.limit(oldLimit);
        
        // Enable client write
        if (clientKey.isValid()) {
          clientKey.interestOps(clientKey.interestOps() | SelectionKey.OP_WRITE);
        }
      }
      
      serverReadBuffer.compact();
      
      // If clientWriteBuffer is full, disable server read until it drains
      if (clientWriteBuffer.remaining() == 0 && serverKey.isValid()) {
        serverKey.interestOps(serverKey.interestOps() & ~SelectionKey.OP_READ);
      }
    }
  }

  public void handleServerConnect(SelectionKey serverKey) throws IOException {
    if (isClosed) return;

    SocketChannel serverChannel = (SocketChannel) serverKey.channel();
    
    try {
      if (serverChannel.isConnectionPending()) {
        serverChannel.finishConnect();
      }
    } catch (IOException e) {
      System.err.println("  [NIO ERROR] Server connection failed: " + e.getMessage());
      closeConnection();
      throw e;
    }

    System.out.println("  [NIO] Server connection established: " + 
        (targetHostPort != null ? targetHostPort.getHost() + ":" + targetHostPort.getPort() : "unknown"));

    state = State.FORWARDING;
    
    if (!serverKey.isValid()) {
      closeConnection();
      return;
    }
    
    serverKey.interestOps(SelectionKey.OP_READ);

    if (targetHostPort != null && targetHostPort.isTunneling()) {
      // Send CONNECT response
      String connectResponse = "HTTP/1.1 200 Connection Established\r\nProxy-agent: Clean-Java-Proxy\r\n\r\n";
      clientWriteBuffer.put(connectResponse.getBytes(StandardCharsets.ISO_8859_1));
      if (clientKey.isValid()) {
        clientKey.interestOps(clientKey.interestOps() | SelectionKey.OP_WRITE);
      }
    } else {
      // HTTP: Forward initial request to server
      clientReadBuffer.flip();
      
      if (clientReadBuffer.hasRemaining()) {
        // FIX: Check space before putting
        int availableSpace = serverWriteBuffer.remaining();
        int dataToTransfer = Math.min(clientReadBuffer.remaining(), availableSpace);
        
        if (dataToTransfer > 0) {
          int oldLimit = clientReadBuffer.limit();
          clientReadBuffer.limit(clientReadBuffer.position() + dataToTransfer);
          serverWriteBuffer.put(clientReadBuffer);
          clientReadBuffer.limit(oldLimit);
        }
      }
      clientReadBuffer.clear();

      int ops = SelectionKey.OP_READ;
      if (serverWriteBuffer.position() > 0) {
        ops |= SelectionKey.OP_WRITE;
      }
      if (serverKey.isValid()) {
        serverKey.interestOps(ops);
      }
    }
  }

  public void handleServerWrite(SelectionKey serverKey) throws IOException {
    if (isClosed) return;

    SocketChannel serverChannel = (SocketChannel) serverKey.channel();
    serverWriteBuffer.flip();
    
    try {
      serverChannel.write(serverWriteBuffer);
    } catch (IOException e) {
      closeConnection();
      throw e;
    }
    
    if (serverWriteBuffer.hasRemaining()) {
      serverWriteBuffer.compact();
    } else {
      serverWriteBuffer.clear();
      if (serverKey.isValid()) {
        serverKey.interestOps(serverKey.interestOps() & ~SelectionKey.OP_WRITE);
        
        // Re-enable client read if it was disabled due to full buffer
        if (clientKey.isValid()) {
          clientKey.interestOps(clientKey.interestOps() | SelectionKey.OP_READ);
        }
      }
    }
  }

  private void deregisterAndClose(SelectionKey key) {
    if (key != null) {
      try {
        if (key.isValid()) {
          key.cancel();
        }
      } catch (Exception ignored) {
      }
      
      try {
        if (key.channel() != null && key.channel().isOpen()) {
          key.channel().close();
        }
      } catch (IOException ignored) {
      }
    }
  }

  public void closeConnection() {
    if (isClosed) return;
    isClosed = true;

    try {
      System.out.println("  [NIO] Closing connection for client: " + 
          (clientKey.channel() != null ? clientKey.channel().toString() : "null"));
    } catch (Exception ignored) {
    }

    deregisterAndClose(clientKey);

    if (serverKey != null) {
      deregisterAndClose(serverKey);
      serverKey = null;
    }
    
    if (serverChannel != null) {
      try {
        if (serverChannel.isOpen()) {
          serverChannel.close();
        }
      } catch (IOException ignored) {
      }
      serverChannel = null;
    }
  }

  private void startNonBlockingConnect() throws IOException {
    if (targetHostPort == null || targetHostPort.getHost() == null) {
      closeConnection();
      return;
    }
    
    try {
      serverChannel = SocketChannel.open();
      serverChannel.configureBlocking(false);
      serverChannel.connect(new InetSocketAddress(targetHostPort.getHost(), targetHostPort.getPort()));

      serverKey = serverChannel.register(selector, SelectionKey.OP_CONNECT);
      serverKey.attach(this);
    } catch (IOException e) {
      System.err.println("  [NIO ERROR] Failed to start connection to " + 
          targetHostPort.getHost() + ":" + targetHostPort.getPort() + " - " + e.getMessage());
      closeConnection();
      throw e;
    }
  }

  private void sendForbiddenResponse(SocketChannel clientChannel, boolean isTunneling) throws IOException {
    String forbiddenResponse = isTunneling 
        ? "HTTP/1.1 403 Forbidden\r\nProxy-agent: Clean-Java-Proxy\r\n\r\n"
        : "HTTP/1.1 403 Forbidden\r\nContent-Type: text/plain\r\nContent-Length: 13\r\n\r\nAccess Denied!";
    
    ByteBuffer buffer = ByteBuffer.wrap(forbiddenResponse.getBytes(StandardCharsets.ISO_8859_1));
    try {
      while (buffer.hasRemaining()) {
        clientChannel.write(buffer);
      }
    } catch (IOException ignored) {
      // Client may have already disconnected
    }
  }

  private String extractRequestLineFromBuffer() {
    clientReadBuffer.flip();
    int limit = clientReadBuffer.limit();
    int requestLineEnd = -1;
    
    for (int i = clientReadBuffer.position(); i < limit - 1; i++) {
      if (clientReadBuffer.get(i) == '\r' && clientReadBuffer.get(i + 1) == '\n') {
        requestLineEnd = i;
        break;
      }
    }

    if (requestLineEnd == -1) {
      clientReadBuffer.compact();
      return null;
    }

    byte[] lineBytes = new byte[requestLineEnd - clientReadBuffer.position()];
    clientReadBuffer.get(lineBytes);
    String requestLine = new String(lineBytes, StandardCharsets.ISO_8859_1);
    clientReadBuffer.position(clientReadBuffer.position() + 2);
    clientReadBuffer.compact();

    return requestLine.trim();
  }
}