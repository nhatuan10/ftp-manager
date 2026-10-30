package com.example.ftpmanager.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import java.util.Random;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    // Hàm tạo 6 số ngẫu nhiên
    public String generateOTP() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000); 
        return String.valueOf(otp);
    }

    // Hàm gửi email thật qua Google SMTP
    public void sendOTP(String toEmail, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Mã xác nhận đăng ký tài khoản FTP");
        message.setText("Chào bạn,\n\nMã xác nhận (OTP) của bạn là: " + otp + "\n\nVui lòng nhập mã này để kích hoạt tài khoản.\nTrân trọng!");
        
        mailSender.send(message);
    }
}