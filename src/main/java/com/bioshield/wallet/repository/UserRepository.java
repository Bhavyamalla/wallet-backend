package com.bioshield.wallet.repository;

import com.bioshield.wallet.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.email = (SELECT b.email FROM BankDetail b WHERE b.accountNumber = :accountNumber)")
    Optional<User> findByAccountNumber(@Param("accountNumber") String accountNumber);
}