package com.example.ftpmanager.repository;

import com.example.ftpmanager.entity.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Integer> {
    EmailVerification findByEmail(String email);
}