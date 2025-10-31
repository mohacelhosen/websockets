package com.mohacel._groupchat.chat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;


@Configuration
@EnableWebSocket
public class Websocket implements WebSocketConfigurer {

    private final GroupChatHandler groupChatHandler;

    public Websocket(GroupChatHandler groupChatHandler) {
        this.groupChatHandler = groupChatHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
                .addHandler(groupChatHandler, "/ws")
                .setAllowedOrigins(
                        "http://localhost",
                        "http://localhost:*",
                        "http://192.168.0.178",
                        "http://192.168.0.178:*"
                );
    }
}
