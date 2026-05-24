package com.bioshield.wallet.repository;

import com.bioshield.wallet.entity.TransactionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.LocalDateTime;

public interface TransactionRepository extends JpaRepository<TransactionRecord, Long> {
    List<TransactionRecord> findBySenderEmailOrderByIdDesc(String senderEmail);
    List<TransactionRecord> findBySenderEmailAndStatus(String senderEmail, String status);
    List<TransactionRecord> findBySenderEmailAndTimestampAfter(String senderEmail, LocalDateTime timestamp);
    boolean existsBySenderEmailAndReceiverAccountNumberAndStatus(String senderEmail, String receiverAccountNumber, String status);

    @Query("SELECT t FROM TransactionRecord t WHERE t.senderEmail = :email OR t.receiverAccountNumber = :accountNumber ORDER BY t.id DESC")
    List<TransactionRecord> findAllByUserOrderByIdDesc(@Param("email") String email, @Param("accountNumber") String accountNumber);
}