package com.bioshield.wallet.service;

import com.bioshield.wallet.entity.User;
import com.bioshield.wallet.entity.TransactionRecord;
import com.bioshield.wallet.dto.RegisterRequest;
import com.bioshield.wallet.dto.LoginRequest;
import com.bioshield.wallet.dto.TransferRequest;
import java.util.List;
import java.util.Map;

public interface UserService {
    String registerUser(RegisterRequest req);
    User authenticateUser(LoginRequest req);
    String emitOtpToken(String email);
    double verifyBalanceAccess(String email, String otp);
    String executeAssetClearing(TransferRequest req);
    List<TransactionRecord> fetchGlobalAuditTrail(String email, String role);
    Map<String, Object> fetchProfileInfo(String email);
    String updateProfileInfo(String email, String name, String password, String bankName, String accountNumber, String ifscCode);
}