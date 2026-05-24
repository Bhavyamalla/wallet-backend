package com.bioshield.wallet.dto;

public class LoginResponse {
    private String email;
    private String name;
    private String role;

    public LoginResponse(String email, String name, String role) {
        this.email = email;
        this.name = name;
        this.role = role;
    }

    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getRole() { return role; }
}