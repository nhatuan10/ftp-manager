package com.example.ftpmanager.controller;

import org.mindrot.jbcrypt.BCrypt;
import com.example.ftpmanager.entity.User;
import com.example.ftpmanager.entity.EmailVerification;
import com.example.ftpmanager.repository.UserRepository;
import com.example.ftpmanager.repository.EmailVerificationRepository;
import com.example.ftpmanager.service.EmailService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    // Khai báo thêm Service và Repository để xử lý OTP
    @Autowired
    private EmailService emailService;

    @Autowired
    private EmailVerificationRepository otpRepository;

    // 1. Hiển thị trang đăng nhập khi người dùng gõ /login
    @GetMapping("/login")
    public String showLoginPage() {
        return "login"; // Tương ứng với file login.html trong thư mục templates
    }

    // 2. Xử lý dữ liệu khi người dùng bấm nút Đăng nhập ở form
    @PostMapping("/login")
    public String processLogin(@RequestParam("username") String username,
                               @RequestParam("password") String password,
                               HttpSession session,
                               Model model) {
        
        // Tìm user trong CSDL theo username
        User user = userRepository.findByUsername(username);
        
        // Kiểm tra: Nếu có user và mật khẩu khớp
        if (user != null && BCrypt.checkpw(password, user.getPasswordHash())) {
            
            // Kiểm tra xem tài khoản có bị khóa hoặc chưa xác thực OTP không
            if (!user.getIsActive()) {
                model.addAttribute("error", "Tài khoản chưa kích hoạt hoặc bị khóa!");
                model.addAttribute("showActivationModal", true); // Kích hoạt cờ bật Pop-up trên giao diện login.html
                model.addAttribute("unverifiedEmail", user.getEmail()); // Truyền email của user sang form ngầm trong Modal
                return "login";
            }
            
            // 👉 LƯU THÔNG TIN TÊN ĐẦY ĐỦ VÀO SESSION ĐỂ HIỂN THỊ LÊN DASHBOARD
            session.setAttribute("sessionUser", user.getFullName() != null ? user.getFullName() : user.getUsername());
            
            // Đăng nhập thành công -> Chuyển hướng sang trang Dashboard
            return "redirect:/dashboard"; 
        } else {
            // Đăng nhập thất bại -> Trả về lỗi lên màn hình HTML
            model.addAttribute("error", "Sai tài khoản hoặc mật khẩu!");
            return "login";
        }
    }

    // 4. Hiển thị trang Đăng ký
    @GetMapping("/register")
    public String showRegisterPage() {
        return "register";
    }

    // 5. Xử lý logic Đăng ký
    @PostMapping("/register")
    public String processRegister(
            @RequestParam("username") String username,
            @RequestParam("fullName") String fullName,
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam("confirmPassword") String confirmPassword,
            Model model) {

        // 5.1. Kiểm tra mật khẩu nhập lại có khớp không
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Mật khẩu nhập lại không khớp!");
            return "register";
        }

        // 5.2. Kiểm tra tài khoản hoặc email đã tồn tại chưa
        if (userRepository.findByUsername(username) != null) {
            model.addAttribute("error", "Tên đăng nhập đã được sử dụng!");
            return "register";
        }
        if (userRepository.existsByEmail(email)) {
            model.addAttribute("error", "Email này đã được đăng ký!");
            return "register";
        }

        // 5.3. Mã hóa mật khẩu bằng BCrypt
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

        // 5.4. Lưu vào CSDL
        User newUser = new User();
        newUser.setUsername(username);
        newUser.setFullName(fullName);
        newUser.setEmail(email);
        newUser.setPasswordHash(hashedPassword);
        
        // Đặt thành FALSE để khóa tài khoản, chờ nhập mã OTP
        newUser.setIsActive(false); 
        
        userRepository.save(newUser);

        // 5.5. Tạo mã OTP, lưu vào DB và gửi email
        String otp = emailService.generateOTP();
        EmailVerification verification = new EmailVerification();
        verification.setEmail(email);
        verification.setOtp(otp);
        otpRepository.save(verification);

        emailService.sendOTP(email, otp);

        // 5.6. Chuyển hướng sang trang nhập OTP, truyền theo email trên thanh địa chỉ
        return "redirect:/verify?email=" + email;
    }

    // 6. Hiển thị trang nhập OTP
    @GetMapping("/verify")
    public String showVerifyPage(@RequestParam("email") String email, Model model) {
        model.addAttribute("email", email);
        return "verify";
    }

    // 7. Xử lý khi người dùng bấm Xác nhận OTP
    @PostMapping("/verify")
    public String processVerify(@RequestParam("email") String email, 
                                @RequestParam("otp") String otp, 
                                Model model) {
        // Tìm mã OTP trong DB theo email
        EmailVerification record = otpRepository.findByEmail(email);
        
        if (record != null && record.getOtp().equals(otp)) {
            // Mã OTP đúng -> Tìm User bằng cách duyệt danh sách
            User user = userRepository.findAll().stream()
                        .filter(u -> u.getEmail().equals(email))
                        .findFirst()
                        .orElse(null);
            
            if (user != null) {
                // Kích hoạt tài khoản
                user.setIsActive(true);
                userRepository.save(user);
                
                // Xóa mã OTP sau khi dùng xong để bảo mật database
                otpRepository.delete(record); 
            }
            // Trả về trang login kèm thông báo thành công
            model.addAttribute("success", "Xác thực thành công! Vui lòng đăng nhập.");
            return "login";
        }
        
        // Nếu mã sai, báo lỗi và vẫn ở lại trang verify
        model.addAttribute("error", "Mã OTP không chính xác!");
        model.addAttribute("email", email);
        return "verify";
    }

    // 8. Xử lý khi người dùng bấm nút "Nhận Mã Kích Hoạt Ngay" từ Pop-up Modal ở trang Login
    @PostMapping("/resend-otp")
    public String processResendOtp(@RequestParam("email") String email, Model model) {
        User user = userRepository.findAll().stream()
                    .filter(u -> u.getEmail().equals(email))
                    .findFirst()
                    .orElse(null);

        if (user == null) {
            model.addAttribute("error", "Email không tồn tại trong hệ thống!");
            return "login";
        }

        // Xóa mã OTP cũ nếu có để tạo mã mới
        EmailVerification oldRecord = otpRepository.findByEmail(email);
        if (oldRecord != null) {
            otpRepository.delete(oldRecord);
        }

        // Tạo mã OTP mới, lưu vào CSDL và gửi email
        String otp = emailService.generateOTP();
        EmailVerification verification = new EmailVerification();
        verification.setEmail(email);
        verification.setOtp(otp);
        otpRepository.save(verification);

        emailService.sendOTP(email, otp);

        // Chuyển hướng sang trang nhập OTP để người dùng nhập mã
        return "redirect:/verify?email=" + email;
    }

    // 9. Hiển thị trang nhập Email khôi phục
    @GetMapping("/forgot-password")
    public String showForgotPasswordPage() {
        return "forgot-password";
    }

    // 10. Xử lý gửi OTP khôi phục mật khẩu
    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam("email") String email, Model model, HttpSession session) {
        User user = userRepository.findAll().stream()
                    .filter(u -> u.getEmail().equals(email))
                    .findFirst()
                    .orElse(null);

        if (user == null) {
            model.addAttribute("error", "Email này chưa được đăng ký trong hệ thống!");
            return "forgot-password";
        }

        // Xóa mã OTP cũ nếu có
        EmailVerification oldRecord = otpRepository.findByEmail(email);
        if (oldRecord != null) {
            otpRepository.delete(oldRecord);
        }

        // Tạo mã OTP mới và gửi qua email
        String otp = emailService.generateOTP();
        EmailVerification verification = new EmailVerification();
        verification.setEmail(email);
        verification.setOtp(otp);
        otpRepository.save(verification);

        emailService.sendOTP(email, otp);

        // Lưu email vào Session để chuyển sang bước nhập OTP
        session.setAttribute("resetEmail", email);

        return "redirect:/forgot-verify-otp";
    }

    // 11. Hiển thị trang nhập OTP xác thực quên mật khẩu
    @GetMapping("/forgot-verify-otp")
    public String showForgotVerifyOtpPage(HttpSession session, Model model) {
        String email = (String) session.getAttribute("resetEmail");
        if (email == null) {
            return "redirect:/forgot-password"; // Nếu chưa nhập email thì đá về lại bước đầu
        }
        model.addAttribute("email", email);
        return "forgot-verify-otp";
    }

    // 12. Xử lý kiểm tra mã OTP
    @PostMapping("/forgot-verify-otp")
    public String processForgotVerifyOtp(@RequestParam("otp") String otp, HttpSession session, Model model) {
        String email = (String) session.getAttribute("resetEmail");
        if (email == null) {
            return "redirect:/forgot-password";
        }

        EmailVerification record = otpRepository.findByEmail(email);
        if (record != null && record.getOtp().equals(otp)) {
            // OTP chuẩn xác -> Đánh dấu vào Session cho phép đổi mật khẩu ở bước tiếp theo
            session.setAttribute("allowResetPassword", true);
            otpRepository.delete(record); // Xóa OTP đã dùng
            return "redirect:/reset-password";
        }

        model.addAttribute("error", "Mã OTP không chính xác!");
        model.addAttribute("email", email);
        return "forgot-verify-otp";
    }

    // 13. Hiển thị trang nhập Mật khẩu mới (Bắt buộc phải qua được bước OTP mới vào được)
    @GetMapping("/reset-password")
    public String showResetPasswordPage(HttpSession session, Model model) {
        Boolean allowed = (Boolean) session.getAttribute("allowResetPassword");
        if (allowed == null || !allowed) {
            return "redirect:/forgot-password"; // Chưa xác thực OTP thì chặn cửa
        }
        return "reset-password";
    }

    // 14. Xử lý đổi mật khẩu mới hoàn tất
    @PostMapping("/reset-password")
    public String processResetPassword(@RequestParam("newPassword") String newPassword,
                                       @RequestParam("confirmPassword") String confirmPassword,
                                       HttpSession session,
                                       Model model) {
        
        String email = (String) session.getAttribute("resetEmail");
        Boolean allowed = (Boolean) session.getAttribute("allowResetPassword");

        if (email == null || allowed == null || !allowed) {
            return "redirect:/forgot-password";
        }

        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "Mật khẩu nhập lại không khớp!");
            return "reset-password";
        }

        User user = userRepository.findAll().stream()
                    .filter(u -> u.getEmail().equals(email))
                    .findFirst()
                    .orElse(null);

        if (user != null) {
            user.setPasswordHash(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
            userRepository.save(user);
        }

        // Xóa sạch Session sau khi đổi mật khẩu thành công
        session.removeAttribute("resetEmail");
        session.removeAttribute("allowResetPassword");

        model.addAttribute("success", "Đổi mật khẩu thành công! Vui lòng đăng nhập.");
        return "login";
    }
}