package com.bioshield.wallet.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "bank_details")
@Data
public class BankDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;
    private String bankName;

    @Column(unique = true)
    private String accountNumber;

    private String ifscCode;
    private String accountHolderName;
}