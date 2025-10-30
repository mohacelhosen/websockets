    package com.mohacel._websocket.socket.dto;

    import lombok.AllArgsConstructor;
    import lombok.Builder;
    import lombok.Data;
    import lombok.NoArgsConstructor;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public class MessageDto {

        private String senderId;
        private String senderName;

        private String receiverId;
        private String receiverName;

        private String messageType;
        private String content;
        private String timestamp;
    }
