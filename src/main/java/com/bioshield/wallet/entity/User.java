package com.bioshield.wallet.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;
    private String password;
    private String role;
    private double balance;
    private String otp;

    // Tracks when the 4-digit code window closes
    private LocalDateTime otpExpiryTime;
}