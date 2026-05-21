package com.bioshield.wallet.controller;
import java.util.List;
import java.util.Map; // <-- Must be exactly java.util.Map

import com.bioshield.wallet.entity.User;
import com.bioshield.wallet.entity.TransactionRecord;
import com.bioshield.wallet.entity.BankDetail; // Fresh Import
import com.bioshield.wallet.repository.BankDetailRepository; // Fresh Import
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
@CrossOrigin(origins = "http://localhost:5173")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private BankDetailRepository bankDetailRepository; // Injected repository for separate bank saving

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
            User user = userService.authenticateUser(request);
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }

    // =========================================================================
    // NEW ENDPOINT: Called by your second frontend page (Bank Details Page)
    // =========================================================================
    @PostMapping("/link-bank")
    public ResponseEntity<?> linkBankDetails(@RequestBody Map<String, String> payload) {
        try {
            String email = payload.get("email");
            String bankName = payload.get("bankName");
            String accountNumber = payload.get("accountNumber");
            String ifscCode = payload.get("ifscCode");
            String accountHolderName = payload.get("accountHolderName");

            if (email == null || bankName == null || accountNumber == null || ifscCode == null) {
                return ResponseEntity.badRequest().body("All bank details are mandatory.");
            }

            // Create and populate a fresh row for our database table
            BankDetail bankDetail = new BankDetail();
            bankDetail.setEmail(email);
            bankDetail.setBankName(bankName);
            bankDetail.setAccountNumber(accountNumber);
            bankDetail.setIfscCode(ifscCode);
            bankDetail.setAccountHolderName(accountHolderName != null ? accountHolderName : "Standard Account Node");

            // Save straight to the bank_details table
            bankDetailRepository.save(bankDetail);

            return ResponseEntity.ok(Map.of("message", "Bank details verified and linked successfully."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Internal processing error: " + e.getMessage());
        }
    }

    @PostMapping("/generate-otp")
    public ResponseEntity<?> generateOtp(@RequestBody Map<String, String> payload) {
        try {
            userService.emitOtpToken(payload.get("email"));
            return ResponseEntity.ok(Map.of("message", "OTP generated and dispatched to your email address."));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    @PostMapping("/verify-balance-otp")
    public ResponseEntity<?> verifyBalanceOtp(@RequestBody Map<String, String> payload) {
        try {
            double currentBalance = userService.verifyBalanceAccess(payload.get("email"), payload.get("otp"));
            return ResponseEntity.ok(Map.of("balance", currentBalance));
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
    public ResponseEntity<?> getLedgerRecords(@RequestParam String email, @RequestParam String role) {
        try {
            List<TransactionRecord> history = userService.fetchGlobalAuditTrail(email, role);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
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
            String email = payload.get("email");
            String name = payload.get("name");
            String password = payload.get("password");
            String bankName = payload.get("bankName");
            String accountNumber = payload.get("accountNumber");
            String ifscCode = payload.get("ifscCode");

            String result = userService.updateProfileInfo(email, name, password, bankName, accountNumber, ifscCode);
            return ResponseEntity.ok(Map.of("message", result));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }
}