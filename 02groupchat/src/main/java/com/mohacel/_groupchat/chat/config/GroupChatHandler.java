package com.mohacel._groupchat.chat.config;

import com.mohacel._groupchat.chat.dto.MessageDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GroupChatHandler implements WebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(GroupChatHandler.class);

    private final Map<String, WebSocketSession> userSession = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> groupChatUsers = new ConcurrentHashMap<>();


    private static final ObjectMapper objectMapper = new ObjectMapper();

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
        String uniqueKey = "";
        if (username != null && !username.toString().isEmpty()) {
             uniqueKey = username + "-" + UUID.randomUUID().toString().substring(0, 8);
            userSession.put(uniqueKey, session);
        }

        String message = String.format("Your uniqueKey: %s", uniqueKey);
        session.sendMessage(new TextMessage(message));
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
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
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        logger.info("afterConnectionClosed: {} : {}", session.getId(), closeStatus.getReason());
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
        groupChatUsers.get(message.getGroupId()).forEach(userId -> {
            if (userSession.get(userId) != null) {
                if (userSession.get(userId).isOpen()){
                    try {
                        userSession.get(userId).sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
                    } catch (IOException e) {
                        logger.error(e.getMessage());
                    }
                }else{
                    userSession.remove(userId);
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
}
