package com.campuseventhub.user.dto;

public class AuthResponse {

    private String token;
    private String tokenType;
    private long expiresInMs;
    private String username;
    private String role;

    public AuthResponse() {
    }

    public AuthResponse(String token, String tokenType, long expiresInMs, String username, String role) {
        this.token = token;
        this.tokenType = tokenType;
        this.expiresInMs = expiresInMs;
        this.username = username;
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public long getExpiresInMs() {
        return expiresInMs;
    }

    public void setExpiresInMs(long expiresInMs) {
        this.expiresInMs = expiresInMs;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
