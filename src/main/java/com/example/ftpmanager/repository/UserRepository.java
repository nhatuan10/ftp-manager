package com.example.ftpmanager.repository;

import com.example.ftpmanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    // Hàm này tương đương: SELECT * FROM Users WHERE Username = ?
    User findByUsername(String username);
    
    // Hàm kiểm tra Email có tồn tại chưa (dùng cho Đăng ký)
    boolean existsByEmail(String email);
}