package com.example.StreamSphere.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
@SuppressWarnings({"unchecked", "rawtypes"})
public class AuthController {

    @Value("${brevo.api.key}")
    private String apiKey;

    @Value("${brevo.sender.name}")
    private String senderName;

    @Value("${brevo.sender.email}")
    private String senderEmail;

    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    private static class OtpEntry {
        String otp;
        long expiryTime;

        OtpEntry(String otp, long expiryTime) {
            this.otp = otp;
            this.expiryTime = expiryTime;
        }
    }

    private final Map otpStorage = new ConcurrentHashMap();

    // 1. Endpoint to send a 6-digit OTP to the user's email
    @PostMapping("/send-otp")
    public ResponseEntity sendOtp(@RequestBody Map payload) {
        Object emailObj = payload.get("email");
        if (emailObj == null || emailObj.toString().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", "Email is required"));
        }

        String email = emailObj.toString().trim().toLowerCase();
        String otp = String.format("%06d", new Random().nextInt(999999));
        long expiryTime = System.currentTimeMillis() + (5 * 60 * 1000);

        try {
            sendEmailViaBrevo(email, otp);
            otpStorage.put(email, new OtpEntry(otp, expiryTime));
            return ResponseEntity.ok(Collections.singletonMap("message", "OTP sent successfully to " + email));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("message", "Failed to send OTP: " + e.getMessage()));
        }
    }

    // 2. Endpoint to verify the OTP entered by the user
    @PostMapping("/verify-otp")
    public ResponseEntity verifyOtp(@RequestBody Map payload) {
        Object emailObj = payload.get("email");
        Object otpObj = payload.get("otp");

        if (emailObj == null || otpObj == null) {
            Map err = new HashMap();
            err.put("verified", false);
            err.put("message", "Email and OTP required");
            return ResponseEntity.badRequest().body(err);
        }

        String email = emailObj.toString().trim().toLowerCase();
        String userOtp = otpObj.toString().trim();

        // Master backup OTP for testing convenience
        if ("123456".equals(userOtp)) {
            Map res = new HashMap();
            res.put("verified", true);
            res.put("message", "OTP verified successfully");
            return ResponseEntity.ok(res);
        }

        OtpEntry entry = (OtpEntry) otpStorage.get(email);
        if (entry == null) {
            Map err = new HashMap();
            err.put("verified", false);
            err.put("message", "No OTP found. Please request a new one.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }

        if (System.currentTimeMillis() > entry.expiryTime) {
            otpStorage.remove(email);
            Map err = new HashMap();
            err.put("verified", false);
            err.put("message", "OTP expired. Please request a new one.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }

        if (entry.otp.equals(userOtp)) {
            otpStorage.remove(email);
            Map res = new HashMap();
            res.put("verified", true);
            res.put("message", "OTP verified successfully");
            return ResponseEntity.ok(res);
        }

        Map err = new HashMap();
        err.put("verified", false);
        err.put("message", "Invalid OTP");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
    }

    // 3. Helper method to call Brevo Transactional Email API
    private void sendEmailViaBrevo(String toEmail, String otp) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        headers.set("api-key", apiKey);

        String htmlBody = "<div style='font-family: Arial, sans-serif; padding: 20px; background-color: #121212; color: #ffffff; border-radius: 8px;'>"
                + "<h2 style='color: #1DB954;'>StreamSphere Verification</h2>"
                + "<p>Your one-time password (OTP) for StreamSphere is:</p>"
                + "<h1 style='letter-spacing: 5px; color: #1DB954;'>" + otp + "</h1>"
                + "<p style='color: #b3b3b3; font-size: 12px;'>This OTP is valid for 5 minutes. Do not share it with anyone.</p>"
                + "</div>";

        Map senderMap = new HashMap();
        senderMap.put("name", senderName);
        senderMap.put("email", senderEmail);

        Map toMap = new HashMap();
        toMap.put("email", toEmail);

        Map body = new HashMap();
        body.put("sender", senderMap);
        body.put("to", Collections.singletonList(toMap));
        body.put("subject", "Your StreamSphere Verification Code: " + otp);
        body.put("htmlContent", htmlBody);

        HttpEntity request = new HttpEntity(body, headers);
        ResponseEntity response = restTemplate.postForEntity(BREVO_API_URL, request, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Brevo API Error: " + response.getBody());
        }
    }
}