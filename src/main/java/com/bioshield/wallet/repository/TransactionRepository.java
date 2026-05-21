package com.bioshield.wallet.repository;

import com.bioshield.wallet.entity.TransactionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.time.LocalDateTime;

public interface TransactionRepository extends JpaRepository<TransactionRecord, Long> {
    List<TransactionRecord> findBySenderEmailOrderByIdDesc(String senderEmail);
    List<TransactionRecord> findBySenderEmailAndStatus(String senderEmail, String status);
    List<TransactionRecord> findBySenderEmailAndTimestampAfter(String senderEmail, LocalDateTime timestamp);

    // Updated tracking check from Email to Account Number
    boolean existsBySenderEmailAndReceiverAccountNumberAndStatus(String senderEmail, String receiverAccountNumber, String status);
}