package com.bioshield.wallet.controller;

import com.bioshield.wallet.dto.LoginResponse;
import com.bioshield.wallet.entity.TransactionRecord;
import com.bioshield.wallet.entity.BankDetail;
import com.bioshield.wallet.repository.BankDetailRepository;
import com.bioshield.wallet.service.UserService;
import com.bioshield.wallet.dto.RegisterRequest;
import com.bioshield.wallet.dto.LoginRequest;
import com.bioshield.wallet.dto.TransferRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {
        "http://localhost:5173",
        "https://biosheild.vercel.app"
})
public class AuthController {

    @Autowired private UserService userService;
    @Autowired private BankDetailRepository bankDetailRepository;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            String message = userService.registerUser(request);
            return ResponseEntity.ok(Map.of("message", message));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            LoginResponse response = userService.authenticateUser(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }

    @PostMapping("/link-bank")
    public ResponseEntity<?> linkBankDetails(@RequestBody Map<String, String> payload) {
        try {
            String email = payload.get("email");
            String bankName = payload.get("bankName");
            String accountNumber = payload.get("accountNumber");
            String ifscCode = payload.get("ifscCode");

            if (email == null || bankName == null || accountNumber == null || ifscCode == null) {
                return ResponseEntity.badRequest().body("All bank details are required.");
            }

            BankDetail bankDetail = new BankDetail();
            bankDetail.setEmail(email);
            bankDetail.setBankName(bankName);
            bankDetail.setAccountNumber(accountNumber);
            bankDetail.setIfscCode(ifscCode);
            bankDetail.setAccountHolderName(payload.getOrDefault("accountHolderName", "Account Holder"));

            bankDetailRepository.save(bankDetail);
            return ResponseEntity.ok(Map.of("message", "Bank account linked successfully."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to link bank: " + e.getMessage());
        }
    }

    @PostMapping("/generate-otp")
    public ResponseEntity<?> generateOtp(@RequestBody Map<String, String> payload) {
        try {
            userService.emitOtpToken(payload.get("email"));
            return ResponseEntity.ok(Map.of("message", "OTP sent to your email."));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    @PostMapping("/verify-balance-otp")
    public ResponseEntity<?> verifyBalanceOtp(@RequestBody Map<String, String> payload) {
        try {
            double balance = userService.verifyBalanceAccess(payload.get("email"), payload.get("otp"));
            return ResponseEntity.ok(Map.of("balance", balance));
        } catch (Exception e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }

    @PostMapping("/transfer")
    public ResponseEntity<?> processTransfer(@RequestBody TransferRequest request) {
        try {
            String outcome = userService.executeAssetClearing(request);
            return ResponseEntity.ok(Map.of("message", outcome));
        } catch (Exception e) {
            return ResponseEntity.status(422).body(e.getMessage());
        }
    }

    @GetMapping("/ledger")
    public ResponseEntity<?> getLedgerRecords(@RequestParam String email) {
        try {
            List<TransactionRecord> history = userService.fetchGlobalAuditTrail(email);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getUserProfile(@RequestParam String email) {
        try {
            Map<String, Object> profile = userService.fetchProfileInfo(email);
            return ResponseEntity.ok(profile);
        } catch (Exception e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    @PostMapping("/update-profile")
    public ResponseEntity<?> updateUserProfile(@RequestBody Map<String, String> payload) {
        try {
            String result = userService.updateProfileInfo(
                    payload.get("email"),
                    payload.get("name"),
                    payload.get("password"),
                    payload.get("bankName"),
                    payload.get("accountNumber"),
                    payload.get("ifscCode")
            );
            return ResponseEntity.ok(Map.of("message", result));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }
}