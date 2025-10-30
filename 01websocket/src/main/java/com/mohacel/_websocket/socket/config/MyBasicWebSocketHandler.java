package com.mohacel._websocket.socket.config;

import com.mohacel._websocket.socket.dto.MessageDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.json.JsonParseException;
import org.springframework.web.socket.*;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MyBasicWebSocketHandler  implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MyBasicWebSocketHandler.class);

    private final Map<String, WebSocketSession> webSocketSessionMap = new ConcurrentHashMap<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Called when connection is established
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("Connection established: {}",  session.getId());
        webSocketSessionMap.put(session.getId(), session);

        String userId = (String) session.getAttributes().get("userId");
        String userName = (String) session.getAttributes().get("userName");

        if (userId != null) {
            log.info("User {} ({}) connected with session {}", userName, userId, session.getId());
        }

        // Optional: Send welcome message
        session.sendMessage(new TextMessage("Welcome! Your session ID is: " + session.getId()));
    }

    // Called when a message is received
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        log.info("Received message from {}:{}", session.getId(), message.getPayload());

        if (message instanceof TextMessage) {
            broadcastTextMessage(session, (TextMessage) message);
        } else if (message instanceof BinaryMessage) {
            broadcastBinaryMessage(session, (BinaryMessage) message);
        } else if (message instanceof PongMessage) {
            handlePongMessage(session, (PongMessage) message);
        } else if (message instanceof PingMessage) {
            handlePingMessage(session, (PingMessage) message);
        }
    }

    // Called when there's a transport error
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("Transport error for session {}: {}", session.getId(), exception.getMessage());

        // Close the session on error
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    // Called after connection is closed
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        log.info("Connection closed: {} with status {}", session.getId(), closeStatus);
        webSocketSessionMap.remove(session.getId());
    }

    // Whether handler supports partial messages
    @Override
    public boolean supportsPartialMessages() {
        return false;
    }


    // ========== Helper Methods ==========

    // Generic broadcast method
    public void broadcast(WebSocketSession sender, WebSocketMessage<?> message) {
        webSocketSessionMap.values().forEach(session -> {
            // Don't send to sender, only to others
            if (!sender.getId().equals(session.getId()) && session.isOpen()) {
                try {
                    // Thread-safe sending
                   synchronized (session) {
                       session.sendMessage(message);
                   }
                    log.debug("Message sent to {}", session.getId());
                } catch (IOException e) {
                    log.error("Failed to send message to {}: {}", session.getId(), e.getMessage());
                    // Remove broken session
                    webSocketSessionMap.remove(session.getId());
                }
            }
        });
    }


    public void broadcastTextMessage(WebSocketSession sender, TextMessage message) throws Exception {
        String payload = message.getPayload();
        MessageDto messageDto = null;
        try {
            messageDto = objectMapper.readValue(payload, MessageDto.class);
        } catch (StreamReadException e) {
            log.info("Invalid JSON format");
            broadcast(sender, new TextMessage(payload));
            return;
        } catch (Exception e) {
            log.error( "Failed to process message");
        }
        
        TextMessage textMessage = new TextMessage(objectMapper.writeValueAsString(messageDto));

        if(messageDto.getReceiverId() != null && !messageDto.getReceiverId().equals(sender.getId())) {
            sendToSession(messageDto.getReceiverId(), textMessage);
        }else{
            broadcast(sender, textMessage);
        }
    }

    private void broadcastBinaryMessage(WebSocketSession sender, BinaryMessage message) {
        ByteBuffer payload = message.getPayload();
        BinaryMessage binaryMessage = new BinaryMessage(payload);

        broadcast(sender, binaryMessage);
    }

    private void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        // Process binary message
        ByteBuffer payload = message.getPayload();
        BinaryMessage binaryMessage = new BinaryMessage(payload);
        session.sendMessage(binaryMessage);
    }

    private void handlePongMessage(WebSocketSession session, PongMessage message) throws Exception {
        log.debug("Received PONG from {}", session.getId());
        // Pong is automatic response to Ping - usually no action needed
    }

    private void handlePingMessage(WebSocketSession session, PingMessage message) throws Exception {
        log.debug("Received PING from {}", session.getId());
        // Send PONG back
        session.sendMessage(new PongMessage());
    }


    public void sendToSession(String sessionId, WebSocketMessage<?> message) throws IOException {
        WebSocketSession session = webSocketSessionMap.get(sessionId);
        if (session != null && session.isOpen()) {
            synchronized (session) {
                session.sendMessage(message);
            }
        } else {
            log.warn("Cannot send to session {}: session not found or closed", sessionId);
        }
    }

    public void broadcastToAll(String message) {
        TextMessage textMessage = new TextMessage(message);
        webSocketSessionMap.values().forEach(session -> {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                } catch (IOException e) {
                    log.error("Failed to broadcast to {}: {}", session.getId(), e.getMessage());
                }
            }
        });
    }

    public int getActiveSessionCount() {
        return webSocketSessionMap.size();
    }
}
