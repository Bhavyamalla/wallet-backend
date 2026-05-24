package com.bioshield.wallet.service.impl;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import com.bioshield.wallet.dto.LoginResponse;
import com.bioshield.wallet.entity.User;
import com.bioshield.wallet.entity.TransactionRecord;
import com.bioshield.wallet.entity.BankDetail;
import com.bioshield.wallet.repository.UserRepository;
import com.bioshield.wallet.repository.TransactionRepository;
import com.bioshield.wallet.repository.BankDetailRepository;
import com.bioshield.wallet.dto.RegisterRequest;
import com.bioshield.wallet.dto.LoginRequest;
import com.bioshield.wallet.dto.TransferRequest;
import com.bioshield.wallet.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Map;
import java.util.HashMap;

@Service
public class UserServiceImpl implements UserService {

    @Autowired private UserRepository userRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private BankDetailRepository bankDetailRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JavaMailSender mailSender;

    @Override
    public String registerUser(RegisterRequest req) {
        if (userRepository.findByEmail(req.getEmail()).isPresent()) {
            throw new RuntimeException("An account with this email already exists.");
        }
        User user = new User();
        user.setName(req.getName());
        user.setEmail(req.getEmail());
        user.setPhoneNumber(req.getPhoneNumber());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRole("ROLE_USER");
        user.setBalance(25000.00);
        user.setOtp(null);
        user.setOtpExpiryTime(null);
        userRepository.save(user);
        return "Registration successful.";
    }

    @Override
    public LoginResponse authenticateUser(LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new RuntimeException("No account found with this email."));

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new RuntimeException("Incorrect password.");
        }
        if (!user.getRole().equals(req.getRole())) {
            throw new RuntimeException("Role mismatch. Check if you selected User or Admin correctly.");
        }
        // Return only safe fields — never the password or OTP
        return new LoginResponse(user.getEmail(), user.getName(), user.getRole());
    }

    @Override
    public void emitOtpToken(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("No account found with this email."));

        String generatedOtp = String.format("%04d", new Random().nextInt(10000));

        // Hash the OTP before storing — never store plain text OTPs
        user.setOtp(passwordEncoder.encode(generatedOtp));
        user.setOtpExpiryTime(LocalDateTime.now().plusMinutes(2));
        userRepository.save(user);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("bioshieldwallet@gmail.com");
            message.setTo(email);
            message.setSubject("Your BioShield OTP Code");
            message.setText(
                    "Hello,\n\n" +
                            "Your one-time verification code is: " + generatedOtp + "\n\n" +
                            "This code expires in 2 minutes.\n" +
                            "If you did not request this, please ignore this email.\n\n" +
                            "BioShield Security"
            );
            mailSender.send(message);
        } catch (Exception e) {
            System.out.println("=== MAIL SEND FAILED ===");
            System.out.println("OTP for " + email + " is: " + generatedOtp);
            System.out.println("========================");
        }
    }

    @Override
    public double verifyBalanceAccess(String email, String otp) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found."));

        if (user.getOtp() == null || !passwordEncoder.matches(otp, user.getOtp())) {
            throw new RuntimeException("Invalid OTP.");
        }
        if (LocalDateTime.now().isAfter(user.getOtpExpiryTime())) {
            user.setOtp(null);
            user.setOtpExpiryTime(null);
            userRepository.save(user);
            throw new RuntimeException("OTP has expired. Please request a new one.");
        }
        user.setOtp(null);
        user.setOtpExpiryTime(null);
        userRepository.save(user);
        return user.getBalance();
    }

    @Override
    public String executeAssetClearing(TransferRequest req) {
        User sender = userRepository.findByEmail(req.getSenderEmail())
                .orElseThrow(() -> new RuntimeException("Sender account not found."));

        // Verify OTP using hash comparison
        if (sender.getOtp() == null || !passwordEncoder.matches(req.getOtp(), sender.getOtp())) {
            throw new RuntimeException("Invalid OTP.");
        }
        if (LocalDateTime.now().isAfter(sender.getOtpExpiryTime())) {
            sender.setOtp(null);
            sender.setOtpExpiryTime(null);
            userRepository.save(sender);
            throw new RuntimeException("OTP has expired. Please request a new one.");
        }

        Optional<User> receiverOpt = userRepository.findByAccountNumber(req.getReceiverAccountNumber());

        // Block self-transfer
        if (receiverOpt.isPresent() && receiverOpt.get().getEmail().equals(req.getSenderEmail())) {
            throw new RuntimeException("You cannot transfer funds to your own account.");
        }

        TransactionRecord tx = new TransactionRecord();
        tx.setSenderEmail(req.getSenderEmail());
        tx.setReceiverAccountNumber(req.getReceiverAccountNumber());
        tx.setAmount(req.getAmount());

        String resolvedReceiverName = receiverOpt.isPresent()
                ? receiverOpt.get().getName()
                : "External Account (" + req.getReceiverAccountNumber() + ")";
        tx.setReceiverName(resolvedReceiverName);

        // Clear OTP no matter what happens next
        sender.setOtp(null);
        sender.setOtpExpiryTime(null);

        // FRAUD RULE 1: Off-hours (12am - 5am)
        int hour = LocalDateTime.now().getHour();
        if (hour >= 0 && hour < 5) {
            tx.setStatus("BLOCKED_FRAUD_OFF_HOURS");
            transactionRepository.save(tx);
            userRepository.save(sender);
            throw new RuntimeException("Transfer blocked: Off-hours restriction active (12:00 AM – 5:00 AM).");
        }

        // FRAUD RULE 2: Velocity check (10+ transfers in 10 minutes)
        List<TransactionRecord> recentTransfers = transactionRepository
                .findBySenderEmailAndTimestampAfter(sender.getEmail(), LocalDateTime.now().minusMinutes(10));
        if (recentTransfers.size() >= 10) {
            tx.setStatus("BLOCKED_FRAUD_VELOCITY");
            transactionRepository.save(tx);
            userRepository.save(sender);
            throw new RuntimeException("Transfer blocked: Too many transfers in a short time.");
        }

        // FRAUD RULE 3: Amount spike (3x average)
        List<TransactionRecord> pastSuccess = transactionRepository
                .findBySenderEmailAndStatus(sender.getEmail(), "SUCCESS");
        if (!pastSuccess.isEmpty()) {
            double avg = pastSuccess.stream().mapToDouble(TransactionRecord::getAmount).average().orElse(0);
            if (req.getAmount() > 3 * avg) {
                tx.setStatus("BLOCKED_FRAUD_SPIKE");
                transactionRepository.save(tx);
                userRepository.save(sender);
                throw new RuntimeException("Transfer blocked: Amount is unusually large compared to your history.");
            }
        }

        // FRAUD RULE 4: Single transfer limit
        if (req.getAmount() > 50000.00) {
            tx.setStatus("BLOCKED_FRAUD");
            transactionRepository.save(tx);
            userRepository.save(sender);
            throw new RuntimeException("Transfer blocked: Maximum single transfer limit is ₹50,000.");
        }

        // Balance check
        if (sender.getBalance() < req.getAmount()) {
            tx.setStatus("FAILED");
            transactionRepository.save(tx);
            userRepository.save(sender);
            throw new RuntimeException("Insufficient balance.");
        }

        // Execute transfer
        sender.setBalance(sender.getBalance() - req.getAmount());
        if (receiverOpt.isPresent()) {
            User receiver = receiverOpt.get();
            receiver.setBalance(receiver.getBalance() + req.getAmount());
            userRepository.save(receiver);
        }
        userRepository.save(sender);

        tx.setStatus("SUCCESS");
        transactionRepository.save(tx);

        // Send receipt email
        try {
            jakarta.mail.internet.MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom("bioshieldwallet@gmail.com");
            helper.setTo(sender.getEmail());
            helper.setSubject("BioShield Transfer Receipt: ₹" + String.format("%.2f", req.getAmount()));
            String html =
                    "<div style='font-family:Arial,sans-serif;padding:20px;background:#f8fafc;color:#1e293b'>" +
                            "<h2 style='color:#0f172a;border-bottom:2px solid #3b82f6;padding-bottom:8px'>Transfer Receipt</h2>" +
                            "<p>Hi " + sender.getName() + ", your transfer was successful.</p>" +
                            "<p><strong>To:</strong> " + resolvedReceiverName + "</p>" +
                            "<p><strong>Account:</strong> " + req.getReceiverAccountNumber() + "</p>" +
                            "<p><strong>Amount:</strong> ₹" + String.format("%.2f", req.getAmount()) + "</p>" +
                            "</div>";
            helper.setText(html, true);
            mailSender.send(mimeMessage);
        } catch (Exception e) {
            System.out.println("[WARNING] Receipt email failed: " + e.getMessage());
        }

        boolean isKnown = transactionRepository.existsBySenderEmailAndReceiverAccountNumberAndStatus(
                sender.getEmail(), req.getReceiverAccountNumber(), "SUCCESS");

        return isKnown
                ? "Transfer successful."
                : "Transfer successful. ⚠️ Note: This is your first transfer to this account.";
    }

    @Override
    public List<TransactionRecord> fetchGlobalAuditTrail(String email) {
        // Look up role from DB — never trust the role sent from the frontend
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found."));

        if ("ROLE_ADMIN".equals(user.getRole())) {
            return transactionRepository.findAll();
        }

        // Get user's account number to also fetch received transactions
        Optional<BankDetail> bankOpt = bankDetailRepository.findByEmail(email);
        if (bankOpt.isPresent()) {
            return transactionRepository.findAllByUserOrderByIdDesc(email, bankOpt.get().getAccountNumber());
        }

        return transactionRepository.findBySenderEmailOrderByIdDesc(email);
    }

    @Override
    public Map<String, Object> fetchProfileInfo(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found."));

        Optional<BankDetail> bankOpt = bankDetailRepository.findByEmail(email);

        Map<String, Object> profile = new HashMap<>();
        profile.put("name", user.getName());
        profile.put("email", user.getEmail());
        profile.put("role", user.getRole());
        profile.put("phoneNumber", user.getPhoneNumber() != null ? user.getPhoneNumber() : "");

        if (bankOpt.isPresent()) {
            BankDetail bank = bankOpt.get();
            profile.put("bankName", bank.getBankName());
            profile.put("accountNumber", bank.getAccountNumber());
            profile.put("ifscCode", bank.getIfscCode());
        } else {
            profile.put("bankName", "");
            profile.put("accountNumber", "");
            profile.put("ifscCode", "");
        }
        return profile;
    }

    @Override
    public String updateProfileInfo(String email, String name, String password,
                                    String bankName, String accountNumber, String ifscCode) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found."));

        if (name != null && !name.trim().isEmpty()) {
            user.setName(name);
        }
        if (password != null && !password.trim().isEmpty()) {
            if (password.length() < 6) {
                throw new RuntimeException("Password must be at least 6 characters.");
            }
            user.setPassword(passwordEncoder.encode(password));
        }
        userRepository.save(user);

        Optional<BankDetail> bankOpt = bankDetailRepository.findByEmail(email);
        BankDetail bank = bankOpt.orElseGet(() -> {
            BankDetail b = new BankDetail();
            b.setEmail(email);
            b.setAccountHolderName(user.getName());
            return b;
        });

        if (bankName != null && !bankName.trim().isEmpty()) bank.setBankName(bankName);
        if (accountNumber != null && !accountNumber.trim().isEmpty()) bank.setAccountNumber(accountNumber);
        if (ifscCode != null && !ifscCode.trim().isEmpty()) bank.setIfscCode(ifscCode);

        bankDetailRepository.save(bank);
        return "Profile updated successfully.";
    }
}