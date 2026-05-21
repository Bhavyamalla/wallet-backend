package com.bioshield.wallet.service.impl;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private BankDetailRepository bankDetailRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JavaMailSender mailSender;

    @Override
    public String registerUser(RegisterRequest req) {
        if (userRepository.findByEmail(req.getEmail()).isPresent()) {
            throw new RuntimeException("User already exists.");
        }
        User user = new User();
        user.setName(req.getName());
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRole("ROLE_USER");
        user.setBalance(25000.00);
        user.setOtp(null);
        user.setOtpExpiryTime(null);

        userRepository.save(user);
        return "Registration successful.";
    }

    @Override
    public User authenticateUser(LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found."));

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials.");
        }

        if (!user.getRole().equals(req.getRole())) {
            throw new RuntimeException("Role mismatch.");
        }

        return user;
    }

    @Override
    public String emitOtpToken(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found."));

        String generatedOtp = String.format("%04d", new Random().nextInt(10000));

        user.setOtp(generatedOtp);
        user.setOtpExpiryTime(LocalDateTime.now().plusMinutes(2));
        userRepository.save(user);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("bioshieldwallet@gmail.com");
            message.setTo(email);
            message.setSubject("🔒 BioShield Dynamic Security Pin");
            message.setText("Greetings Security Entity,\n\n"
                    + "Your critical dynamic access verification token is: " + generatedOtp + "\n\n"
                    + "This authorization block will completely expire in exactly 2 minutes.\n"
                    + "If you did not issue this processing signature request, contact admin nodes immediately.\n\n"
                    + "Regards,\n"
                    + "BioShield AI Core Mitigation Infrastructure");

            mailSender.send(message);
            System.out.println("SMTP dynamic security mail successfully dispatched to: " + email);
        } catch (Exception e) {
            System.out.println("=========================================================================");
            System.out.println("[WARNING] SMTP Outbound mail delivery intercept failure: " + e.getMessage());
            System.out.println("Please configure spring.mail settings in application.properties.");
            System.out.println("GENERATED OTP FOR TESTING: " + generatedOtp + " (User: " + email + ")");
            System.out.println("=========================================================================");
        }

        return generatedOtp;
    }

    @Override
    public double verifyBalanceAccess(String email, String otp) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found."));

        if (user.getOtp() == null || !user.getOtp().equals(otp)) {
            throw new RuntimeException("Invalid authentication credentials token.");
        }

        if (LocalDateTime.now().isAfter(user.getOtpExpiryTime())) {
            user.setOtp(null);
            user.setOtpExpiryTime(null);
            userRepository.save(user);
            throw new RuntimeException("Security clearance window closed. Token expired.");
        }

        user.setOtp(null);
        user.setOtpExpiryTime(null);
        userRepository.save(user);

        return user.getBalance();
    }

    @Override
    public String executeAssetClearing(TransferRequest req) {
        User sender = userRepository.findByEmail(req.getSenderEmail())
                .orElseThrow(() -> new RuntimeException("Sender not found."));

        if (sender.getOtp() == null || !sender.getOtp().equals(req.getOtp())) {
            throw new RuntimeException("Invalid authentication credentials token.");
        }

        if (LocalDateTime.now().isAfter(sender.getOtpExpiryTime())) {
            sender.setOtp(null);
            sender.setOtpExpiryTime(null);
            userRepository.save(sender);
            throw new RuntimeException("Security transaction clearance window closed. Token expired.");
        }

        // --- BYPASS MODE ACTIVATED HERE ---
        Optional<User> receiverOpt = userRepository.findByAccountNumber(req.getReceiverAccountNumber());

        TransactionRecord tx = new TransactionRecord();
        tx.setSenderEmail(req.getSenderEmail());
        tx.setReceiverAccountNumber(req.getReceiverAccountNumber());
        tx.setAmount(req.getAmount());

        String resolvedReceiverName;
        if (receiverOpt.isPresent()) {
            resolvedReceiverName = receiverOpt.get().getName();
        } else {
            // If any dummy account number is passed, it uses this format cleanly instead of throwing an error popup!
            resolvedReceiverName = "External Account (" + req.getReceiverAccountNumber() + ")";
        }
        tx.setReceiverName(resolvedReceiverName);

        // RULE 1: Off-hours transfer restrictions (12:00 AM - 5:00 AM)
        int hour = LocalDateTime.now().getHour();
        if (hour >= 0 && hour < 5) {
            tx.setStatus("BLOCKED_FRAUD_OFF_HOURS");
            transactionRepository.save(tx);

            sender.setOtp(null);
            sender.setOtpExpiryTime(null);
            userRepository.save(sender);

            throw new RuntimeException("Transaction Blocked: Off-hours transfer restrictions active (12:00 AM - 5:00 AM).");
        }

        // RULE 2: Velocity bounds check
        List<TransactionRecord> recentTransfers = transactionRepository.findBySenderEmailAndTimestampAfter(
                sender.getEmail(), LocalDateTime.now().minusMinutes(10));
        if (recentTransfers.size() >= 10) {
            tx.setStatus("BLOCKED_FRAUD_VELOCITY");
            transactionRepository.save(tx);

            sender.setOtp(null);
            sender.setOtpExpiryTime(null);
            userRepository.save(sender);

            throw new RuntimeException("Transaction Blocked: Velocity bounds exceeded (10+ transfers in 10 minutes).");
        }

        // RULE 3: Amount Spike Checks
        List<TransactionRecord> priorSuccessfulTransfers = transactionRepository.findBySenderEmailAndStatus(
                sender.getEmail(), "SUCCESS");
        if (!priorSuccessfulTransfers.isEmpty()) {
            double totalSuccessfulAmount = 0;
            for (TransactionRecord successTx : priorSuccessfulTransfers) {
                totalSuccessfulAmount += successTx.getAmount();
            }
            double averageAmount = totalSuccessfulAmount / priorSuccessfulTransfers.size();
            if (req.getAmount() > 3 * averageAmount) {
                tx.setStatus("BLOCKED_FRAUD_SPIKE");
                transactionRepository.save(tx);

                sender.setOtp(null);
                sender.setOtpExpiryTime(null);
                userRepository.save(sender);

                throw new RuntimeException("Transaction Blocked: Amount spike detected.");
            }
        }

        // RULE 4: High volume limit verification
        if (req.getAmount() > 50000.00) {
            tx.setStatus("BLOCKED_FRAUD");
            transactionRepository.save(tx);

            sender.setOtp(null);
            sender.setOtpExpiryTime(null);
            userRepository.save(sender);

            throw new RuntimeException("Transaction Blocked: Single high volume bounds exceeded (Max ₹50,000 limit).");
        }

        if (sender.getBalance() < req.getAmount()) {
            tx.setStatus("FAILED");
            transactionRepository.save(tx);

            sender.setOtp(null);
            sender.setOtpExpiryTime(null);
            userRepository.save(sender);

            throw new RuntimeException("Insufficient ledger reserves.");
        }

        // Complete the transfer
        sender.setBalance(sender.getBalance() - req.getAmount());

        if (receiverOpt.isPresent()) {
            User realReceiver = receiverOpt.get();
            realReceiver.setBalance(realReceiver.getBalance() + req.getAmount());
            userRepository.save(realReceiver);
        }

        sender.setOtp(null);
        sender.setOtpExpiryTime(null);
        userRepository.save(sender);

        tx.setStatus("SUCCESS");
        transactionRepository.save(tx);

        try {
            jakarta.mail.internet.MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom("bioshieldwallet@gmail.com");
            helper.setTo(sender.getEmail());
            helper.setSubject("🧾 BioShield Settlement Receipt: ₹" + String.format("%.2f", req.getAmount()));

            String htmlContent = "<div style='font-family: Arial, sans-serif; padding: 20px; background-color: #f8fafc; color: #1e293b;'>"
                    + "  <h2 style='color: #0f172a; border-bottom: 2px solid #3b82f6; padding-bottom: 8px;'>Clearance Gateway Transfer Receipt</h2>"
                    + "  <p>Greetings " + sender.getName() + ", your transaction has successfully processed.</p>"
                    + "  <p><strong>Sent To Recipient:</strong> " + resolvedReceiverName + "</p>"
                    + "  <p><strong>Account Number:</strong> " + req.getReceiverAccountNumber() + "</p>"
                    + "  <p><strong>Settlement Volume:</strong> ₹" + String.format("%.2f", req.getAmount()) + "</p>"
                    + "</div>";

            helper.setText(htmlContent, true);
            mailSender.send(mimeMessage);
        } catch (Exception e) {
            System.out.println("[WARNING] Audit email dispatch failure: " + e.getMessage());
        }

        boolean isKnown = transactionRepository.existsBySenderEmailAndReceiverAccountNumberAndStatus(
                sender.getEmail(), req.getReceiverAccountNumber(), "SUCCESS");

        if (!isKnown) {
            return "Transfer sequence completed successfully. ⚠️ Warning: First time interacting with this account segment.";
        }

        return "Transfer sequence completed successfully.";
    }

    @Override
    public List<TransactionRecord> fetchGlobalAuditTrail(String email, String role) {
        if ("ROLE_ADMIN".equals(role)) {
            return transactionRepository.findAll();
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

        if (bankOpt.isPresent()) {
            BankDetail bank = bankOpt.get();
            profile.put("bankName", bank.getBankName());
            profile.put("accountNumber", bank.getAccountNumber());
            profile.put("ifscCode", bank.getIfscCode());
            profile.put("accountHolderName", bank.getAccountHolderName());
        } else {
            profile.put("bankName", "");
            profile.put("accountNumber", "");
            profile.put("ifscCode", "");
            profile.put("accountHolderName", "");
        }

        return profile;
    }

    @Override
    public String updateProfileInfo(String email, String name, String password, String bankName, String accountNumber, String ifscCode) {
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
        BankDetail bank;
        if (bankOpt.isPresent()) {
            bank = bankOpt.get();
        } else {
            bank = new BankDetail();
            bank.setEmail(email);
            bank.setAccountHolderName(name != null ? name : user.getName());
        }

        if (bankName != null && !bankName.trim().isEmpty()) {
            bank.setBankName(bankName);
        }
        if (accountNumber != null && !accountNumber.trim().isEmpty()) {
            bank.setAccountNumber(accountNumber);
        }
        if (ifscCode != null && !ifscCode.trim().isEmpty()) {
            bank.setIfscCode(ifscCode);
        }

        bankDetailRepository.save(bank);

        return "Profile updated successfully.";
    }
}