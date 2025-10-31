package com.mohacel._groupchat.chat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

@Component
public class GroupChatHandler implements WebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(GroupChatHandler.class);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info("afterConnectionEstablished: {}",session.getId());
        String message = String.format("Your session id: %s", session.getId());
        session.sendMessage(new TextMessage(message));
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {

    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {

    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {

    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
