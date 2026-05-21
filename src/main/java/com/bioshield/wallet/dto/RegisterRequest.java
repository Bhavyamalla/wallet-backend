package com.bioshield.wallet.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String name;
    private String email;
    private String phoneNumber; // <-- Added phone number field here
    private String password;

    // Add these inside RegisterRequest class
    private String bankName;
    private String accountNumber;
    private String ifscCode;
}