package com.bioshield.wallet.repository;

import com.bioshield.wallet.entity.BankDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BankDetailRepository extends JpaRepository<BankDetail, Long> {
    Optional<BankDetail> findByEmail(String email);
}