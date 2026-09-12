package com.sindhueventpay.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String type = "Bearer";
    
    public AuthResponse(String token) {
        this.token = token;
    }
}
