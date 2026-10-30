package com.example.ftpmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

@SpringBootApplication (exclude = {DataSourceAutoConfiguration.class})
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