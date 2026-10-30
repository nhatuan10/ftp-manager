package com.example.ftpmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

@SpringBootApplication
public class FtpmanagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FtpmanagerApplication.class, args);
    }

    // ⚠️ THÊM BEAN NÀY VÀO ĐỂ FIX TRIỆT ĐỂ LỖI KHÔNG NHẬN FILE UPLOAD
    @Bean
    public StandardServletMultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver();
    }
}