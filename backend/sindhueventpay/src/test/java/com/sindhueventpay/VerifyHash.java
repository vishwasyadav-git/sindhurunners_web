package com.sindhueventpay;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class VerifyHash {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = "$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIvi";
        System.out.println("Matches password123? " + encoder.matches("password123", hash));
        System.out.println("Matches admin123? " + encoder.matches("admin123", hash));
    }
}
