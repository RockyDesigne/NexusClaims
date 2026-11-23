package com.titu.nexus_claims.dtos;

import lombok.Data;

@Data
public class WebSocketMessage {
    private String type;
    private String senderUsername;
    private String receiverUsername;
    private Object message;  // Using Object to handle different message payloads
}
