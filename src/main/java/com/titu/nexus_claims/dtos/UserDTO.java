package com.titu.nexus_claims.dtos;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserDTO {
    private String username;
    private String password;
    private String publicKey;
    private String role;
    private String address;
    private String cnp;
    private String firstName;
    private String lastName;
}
