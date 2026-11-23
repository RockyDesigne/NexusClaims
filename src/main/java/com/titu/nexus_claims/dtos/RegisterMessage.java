package com.titu.nexus_claims.dtos;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RegisterMessage extends WebSocketMessage {
    private String message;
}
