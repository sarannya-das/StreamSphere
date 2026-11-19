
package com.example.StreamSphere.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Value("${brevo.api.key}")
    private String apiKey;

    @Value("${brevo.sender.name}")
    private String senderName;

    @Value("${brevo.sender.email}")
    private String senderEmail;

    private static final String BREVO_API_URL =
            "https://api.brevo.com/v3/smtp/email";

    private static final long OTP_VALIDITY =
            5 * 60 * 1000;

    private final SecureRandom random = new SecureRandom();

    private final Map<String, OtpEntry> otpStorage =
            new ConcurrentHashMap<>();

    private final RestTemplate restTemplate = new RestTemplate();

    // OTP storage class
    private static class OtpEntry {
        private final String otp;
        private final long expiryTime;

        OtpEntry(String otp, long expiryTime) {
            this.otp = otp;
            this.expiryTime = expiryTime;
        }
    }

    // 1. Send OTP
    @PostMapping("/send-otp")
    public ResponseEntity<Map<String, Object>> sendOtp(
            @RequestBody Map<String, Object> payload) {

        Object emailObj = payload.get("email");

        if (emailObj == null ||
                emailObj.toString().trim().isEmpty()) {

            return ResponseEntity.badRequest().body(
                    response(false, "Email is required")
            );
        }

        String email = emailObj.toString()
                .trim()
                .toLowerCase();

        if (!email.matches(
                "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {

            return ResponseEntity.badRequest().body(
                    response(false, "Invalid email address")
            );
        }

        String otp = String.format(
                "%06d", random.nextInt(1_000_000));

        long expiryTime =
                System.currentTimeMillis() + OTP_VALIDITY;

        try {
            sendEmailViaBrevo(email, otp);

            otpStorage.put(
                    email,
                    new OtpEntry(otp, expiryTime)
            );

            return ResponseEntity.ok(
                    response(true, "OTP sent successfully")
            );

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(response(
                            false,
                            "Failed to send OTP. Please try again."
                    ));
        }
    }

    // 2. Verify OTP
    @PostMapping("/verify-otp")
    public ResponseEntity<Map<String, Object>> verifyOtp(
            @RequestBody Map<String, Object> payload) {

        Object emailObj = payload.get("email");
        Object otpObj = payload.get("otp");

        if (emailObj == null || otpObj == null) {

            return ResponseEntity.badRequest().body(
                    response(false, "Email and OTP are required")
            );
        }

        String email = emailObj.toString()
                .trim()
                .toLowerCase();

        String userOtp = otpObj.toString().trim();

        if (!userOtp.matches("\\d{6}")) {
            return ResponseEntity.badRequest().body(
                    response(false, "Invalid OTP format")
            );
        }

        OtpEntry entry = otpStorage.get(email);

        if (entry == null) {
            return ResponseEntity.badRequest().body(
                    response(
                            false,
                            "No OTP found. Please request a new one."
                    )
            );
        }

        // Check OTP expiration
        if (System.currentTimeMillis() > entry.expiryTime) {

            otpStorage.remove(email);

            return ResponseEntity.badRequest().body(
                    response(
                            false,
                            "OTP expired. Please request a new one."
                    )
            );
        }

        // Verify OTP
        if (entry.otp.equals(userOtp)) {

            otpStorage.remove(email);

            return ResponseEntity.ok(
                    response(true, "OTP verified successfully")
            );
        }

        return ResponseEntity.badRequest().body(
                response(false, "Invalid OTP")
        );
    }

    // 3. Send email using Brevo API
    private void sendEmailViaBrevo(
            String toEmail,
            String otp) {

        HttpHeaders headers = new HttpHeaders();

        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(
                Collections.singletonList(MediaType.APPLICATION_JSON)
        );

        headers.set("api-key", apiKey);

        String htmlBody =
                "<div style='font-family:Arial;padding:20px;" +
                "background-color:#121212;color:white;border-radius:8px;'>" +

                "<h2 style='color:#1DB954;'>StreamSphere Verification</h2>" +

                "<p>Your one-time password (OTP) is:</p>" +

                "<h1 style='letter-spacing:5px;color:#1DB954;'>" +
                otp +
                "</h1>" +

                "<p style='color:#b3b3b3;font-size:12px;'>" +
                "This OTP is valid for 5 minutes. " +
                "Do not share it with anyone." +
                "</p></div>";

        Map<String, Object> sender = new HashMap<>();
        sender.put("name", senderName);
        sender.put("email", senderEmail);

        Map<String, Object> recipient = new HashMap<>();
        recipient.put("email", toEmail);

        Map<String, Object> body = new HashMap<>();

        body.put("sender", sender);
        body.put("to", Collections.singletonList(recipient));
        body.put("subject",
                "Your StreamSphere Verification Code");
        body.put("htmlContent", htmlBody);

        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(body, headers);

        restTemplate.postForEntity(
                BREVO_API_URL,
                request,
                String.class
        );
    }

    // 4. Common response method
    private Map<String, Object> response(
            boolean success,
            String message) {

        Map<String, Object> result = new HashMap<>();

        result.put("verified", success);
        result.put("message", message);

        return result;
    }
}
