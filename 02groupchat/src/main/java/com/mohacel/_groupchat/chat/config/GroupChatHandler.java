package com.mohacel._groupchat.chat.config;

import com.mohacel._groupchat.chat.dto.MessageDto;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class GroupChatHandler implements WebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(GroupChatHandler.class);

    private final Map<String, WebSocketSession> userSession = new ConcurrentHashMap<>();
    private final Map<String, String> sessionUserMap = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> groupChatUsers = new ConcurrentHashMap<>();

    // Track last pong time per session
    private final Map<String, Long>  lastPongMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatScheduler  = Executors.newScheduledThreadPool(1);

    private static final ObjectMapper objectMapper = new ObjectMapper();


    @PostConstruct
    public void startHeartbeatScheduler() {
        heartbeatScheduler.scheduleAtFixedRate(this::sendPingToAll, 0, 30, TimeUnit.SECONDS);
        logger.info("Heartbeat scheduler started ✅");
    }

    @PreDestroy
    public void stopHeartbeatScheduler() {
        heartbeatScheduler.shutdown();
        logger.info("Heartbeat scheduler stopped ❌");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info("afterConnectionEstablished: {}",session.getId());

        Map<String, Object> params = new HashMap<>();
        URI uri = session.getUri();

        if (uri != null && uri.getQuery() != null) {
            String query = uri.getQuery();
            Arrays.stream(query.split("&")).forEach(s -> {
                String[] kv = s.split("=", 2);
                params.put(kv[0], kv.length > 1 ? kv[1].replaceAll("\\s+", "").toLowerCase(): "");
            });
        }

        params.forEach((k, v) -> logger.info("{} : {}", k, v));

        Object username = params.getOrDefault("username", "");
        String userUniqueKey = "";
        if (username != null && !username.toString().isEmpty()) {
            userUniqueKey = username + "-" + UUID.randomUUID().toString().substring(0, 8);
            userSession.put(userUniqueKey, session);
            sessionUserMap.put(session.getId(), userUniqueKey);
        }

        String message = String.format("Your uniqueKey: %s", userUniqueKey);
        session.sendMessage(new TextMessage(message));
        lastPongMap.put(session.getId(), System.currentTimeMillis());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        logger.info("afterConnectionClosed: {} : {}", session.getId(), closeStatus.getReason());
        String userId = sessionUserMap.remove(session.getId());
        if (userId != null) userSession.remove(userId);
        lastPongMap.remove(session.getId());
    }


    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        if(message instanceof PongMessage){
            lastPongMap.put(session.getId(), System.currentTimeMillis());
            return;
        }

        String payload = (String) message.getPayload();
        MessageDto messageDto = objectMapper.readValue(payload, MessageDto.class);
        String messageType = messageDto.getMessageType();

        switch (messageType) {
            case "CREATE-ROOM":
                createGroupChatSession(session, messageDto);
                break;

            case "JOIN-ROOM":
                joinGroupChat(session, messageDto);
                break;

            case "GROUP-CHAT":
                handleGroupChatMessage(messageDto);
                break;

            default:
                break;
        }

    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {

    }


    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

//    helper method
    public void  createGroupChatSession(WebSocketSession session, MessageDto message) throws IOException {
        String groupName = message.getContent().replaceAll("\\s+", "");
        String uniqueGroupName = groupName + "-" + getUniqueKey();
        groupChatUsers.put(uniqueGroupName, new HashSet<>(List.of(message.getSenderId())));
        message.setGroupId(uniqueGroupName);

        String payload = objectMapper.writeValueAsString(message);
        session.sendMessage(new TextMessage(payload));
        logger.info("{} create new room: {}", message.getSenderId(), uniqueGroupName);
    }

    public void joinGroupChat(WebSocketSession session, MessageDto message) throws IOException {
        Set<String> userIds = groupChatUsers.get(message.getGroupId());
        userIds.add(message.getSenderId());
        groupChatUsers.put(message.getGroupId(), userIds);

        message.setContent(userIds.toString());
        String payload = objectMapper.writeValueAsString(message);
        session.sendMessage(new TextMessage(payload));
        logger.info("join room {}: {}", message.getContent(), message.getSenderId());
    }

    public void handleGroupChatMessage(MessageDto message) throws IOException {
        Set<String> userIds = groupChatUsers.get(message.getGroupId());
        if (userIds == null) {
            logger.warn("Group {} not found for message from {}", message.getGroupId(), message.getSenderId());
            return;
        }

        userIds.forEach(userId -> {
            if (userSession.get(userId) != null) {
                if (userSession.get(userId).isOpen()){
                    try {
                        userSession.get(userId).sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
                    } catch (IOException e) {
                        logger.error(e.getMessage());
                    }
                }
            }else{
                userSession.remove(userId);
                groupChatUsers.get(message.getGroupId()).remove(userId);
            }
        });
    }

    public String getUniqueKey(){
        return UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8);
    }

    // -------------------- Ping / Pong logic --------------------
    private void sendPingToAll() {

        if (userSession.isEmpty()) {
            logger.debug("No active sessions — skipping heartbeat cycle.");
            return;
        }

        long now = System.currentTimeMillis();
        for(WebSocketSession session : new ArrayList<>(userSession.values())){
            if (!session.isOpen()) continue;

            try {
                session.sendMessage(new PingMessage(ByteBuffer.wrap("ping".getBytes())));
                logger.debug("Sent PING to {}", session.getId());
            } catch (IOException e) {
                logger.error("Ping failed: {}", e.getMessage());
            }

            // Check if last pong is too old
            Long lastPong = lastPongMap.get(session.getId());
            if (lastPong != null && (now - lastPong > 60000)) {
                logger.warn("Session {} inactive >60s, closing", session.getId());
                try {
                    session.close(CloseStatus.SESSION_NOT_RELIABLE);
                }catch (IOException ignored) {
                    try {
                        afterConnectionClosed(session, CloseStatus.SESSION_NOT_RELIABLE);
                    } catch (Exception e) {
                        logger.error(e.getMessage());
                    }
                }
            }
        }
    }

}
