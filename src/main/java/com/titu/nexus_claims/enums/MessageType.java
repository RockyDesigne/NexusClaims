package com.titu.nexus_claims.enums;

import lombok.Getter;

@Getter
public enum MessageType {
    REGISTER("register"),
    OFFER("offer"),
    ICE_CANDIDATE("iceCandidate");

    MessageType(final String type) {
        this.type = type;
    }

    private final String type;

}
