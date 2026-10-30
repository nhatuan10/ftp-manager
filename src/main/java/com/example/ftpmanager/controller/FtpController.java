package com.example.ftpmanager.controller;

import jakarta.servlet.http.HttpSession;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.example.ftpmanager.model.FtpFileDTO;
import com.example.ftpmanager.model.TransferItemDTO;

import java.util.List;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

@Controller
public class FtpController {

    // 1. Hiển thị Dashboard & Tự động tính dung lượng + Hàng đợi
    @GetMapping("/dashboard")
    public String showDashboard(@RequestParam(value = "path", required = false) String path,
                                HttpSession session, Model model) {
        String fullName = (String) session.getAttribute("sessionUser");
        model.addAttribute("username", fullName != null ? fullName : "Admin");

        String currentPath = path;
        if (currentPath == null) {
            currentPath = (String) session.getAttribute("currentPath");
            if (currentPath == null) {
                currentPath = "/";
            }
        }
        session.setAttribute("currentPath", currentPath);
        model.addAttribute("currentPath", currentPath);

        // Lấy thông báo lỗi/thành công từ Session
        String ftpError = (String) session.getAttribute("ftpError");
        if (ftpError != null) {
            model.addAttribute("error", ftpError);
            session.removeAttribute("ftpError");
        }

        String ftpSuccess = (String) session.getAttribute("ftpSuccess");
        if (ftpSuccess != null) {
            model.addAttribute("success", ftpSuccess);
            session.removeAttribute("ftpSuccess");
        }

        Boolean isConnected = (Boolean) session.getAttribute("isConnected");
        if (isConnected == null) isConnected = false;
        model.addAttribute("isConnected", isConnected);
        
        if (isConnected) {
            String host = (String) session.getAttribute("ftpHost");
            String username = (String) session.getAttribute("ftpUser");
            String password = (String) session.getAttribute("ftpPass");

            List<FtpFileDTO> fileList = new ArrayList<>();
            FTPClient ftpClient = new FTPClient();
            long totalBytesUsed = 0;

            try {
                ftpClient.connect(host, 21);
                if (ftpClient.login(username, password)) {
                    ftpClient.enterLocalPassiveMode();
                    
                    // Tính tổng dung lượng toàn bộ server
                    totalBytesUsed = calculateTotalStorage(ftpClient, "/");

                    // Chuyển về đúng thư mục hiện tại để lấy danh sách file
                    ftpClient.changeWorkingDirectory(currentPath);
                    FTPFile[] ftpFiles = ftpClient.listFiles();
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                    if (ftpFiles != null) {
                        for (FTPFile file : ftpFiles) {
                            if (file.getName().equals(".") || (file.getName().equals("..") && currentPath.equals("/"))) {
                                continue;
                            }

                            String sizeStr = file.isDirectory() ? "--" : formatFileSize(file.getSize());
                            String timeStr = file.getTimestamp() != null ? sdf.format(file.getTimestamp().getTime()) : "--";

                            fileList.add(new FtpFileDTO(
                                file.getName(),
                                sizeStr,
                                timeStr,
                                file.isDirectory()
                            ));
                        }
                    }
                    
                    ftpClient.logout();
                    ftpClient.disconnect();
                }
            } catch (IOException e) {
                model.addAttribute("error", "Lỗi đọc dữ liệu FTP: " + e.getMessage());
            }

            String storageFormatted = formatFileSize(totalBytesUsed);
            model.addAttribute("usedStorage", storageFormatted);
            model.addAttribute("fileList", fileList);
            model.addAttribute("ftpHost", host);
        }

        // Lấy danh sách hàng đợi từ Session truyền ra giao diện (Luôn có để không bị null)
        List<TransferItemDTO> queueList = (List<TransferItemDTO>) session.getAttribute("transferQueue");
        if (queueList == null) {
            queueList = new ArrayList<>();
        }
        model.addAttribute("transferQueue", queueList);

        return "dashboard";
    }

    // --- HÀM PHỤ TRỢ 1: Đệ quy quét dung lượng ---
    private long calculateTotalStorage(FTPClient ftpClient, String dirPath) throws IOException {
        long size = 0;
        FTPFile[] files = ftpClient.listFiles(dirPath);
        if (files != null) {
            for (FTPFile file : files) {
                String name = file.getName();
                if (name.equals(".") || name.equals("..")) continue;

                if (file.isFile()) {
                    size += file.getSize();
                } else if (file.isDirectory()) {
                    String subPath = dirPath.endsWith("/") ? dirPath + name : dirPath + "/" + name;
                    size += calculateTotalStorage(ftpClient, subPath);
                }
            }
        }
        return size;
    }

    // --- HÀM PHỤ TRỢ 2: Format dung lượng ---
    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 MB";
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        return String.format("%.2f MB", mb);
    }

    // 2. Xử lý kết nối Server FTP
    @PostMapping("/ftp/connect")
    public String processConnectFTP(@RequestParam("host") String host,
                                    @RequestParam("port") int port,
                                    @RequestParam("username") String username,
                                    @RequestParam("password") String password,
                                    HttpSession session) {
        
        FTPClient ftpClient = new FTPClient();
        try {
            ftpClient.connect(host, port);
            boolean success = ftpClient.login(username, password);

            if (success) {
                session.setAttribute("isConnected", true);
                session.setAttribute("ftpHost", host);
                session.setAttribute("ftpUser", username);
                session.setAttribute("ftpPass", password);
                session.removeAttribute("ftpError");
                
                ftpClient.logout();
                ftpClient.disconnect();
            } else {
                session.setAttribute("isConnected", false);
                session.setAttribute("ftpError", "Kết nối thất bại: Sai tên đăng nhập hoặc mật khẩu FTP!");
            }

        } catch (IOException e) {
            session.setAttribute("isConnected", false);
            session.setAttribute("ftpError", "Không thể kết nối đến máy chủ FTP. Vui lòng kiểm tra lại IP và Port!");
        } finally {
            try {
                if (ftpClient.isConnected()) {
                    ftpClient.disconnect();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        return "redirect:/dashboard";
    }

    // 3. Ngắt kết nối
    @PostMapping("/ftp/disconnect")
    public String processDisconnectFTP(HttpSession session) {
        session.removeAttribute("isConnected");
        session.removeAttribute("ftpHost");
        session.removeAttribute("ftpError");
        session.removeAttribute("ftpUser");
        session.removeAttribute("ftpPass");
        session.removeAttribute("ftpSuccess");
        return "redirect:/dashboard";
    }

    // 4. Tạo thư mục mới
    @PostMapping("/ftp/create-folder")
    public String processCreateFolder(@RequestParam("folderName") String folderName,
                                      HttpSession session) {
        String host = (String) session.getAttribute("ftpHost");
        String username = (String) session.getAttribute("ftpUser");
        String password = (String) session.getAttribute("ftpPass");

        FTPClient ftpClient = new FTPClient();
        try {
            ftpClient.connect(host, 21);
            if (ftpClient.login(username, password)) {
                ftpClient.enterLocalPassiveMode();
                
                boolean created = ftpClient.makeDirectory(folderName);
                if (created) {
                    session.setAttribute("ftpSuccess", "Tạo thư mục '" + folderName + "' thành công!");
                    addTransferLog(session, folderName, "CREATE_FOLDER", "Thành công");
                } else {
                    session.setAttribute("ftpError", "Không thể tạo thư mục (Có thể tên đã tồn tại hoặc thiếu quyền).");
                    addTransferLog(session, folderName, "CREATE_FOLDER", "Thất bại");
                }
                
                ftpClient.logout();
                ftpClient.disconnect();
            }
        } catch (IOException e) {
            session.setAttribute("ftpError", "Lỗi kết nối FTP khi tạo thư mục: " + e.getMessage());
            addTransferLog(session, folderName, "CREATE_FOLDER", "Thất bại");
        }

        return "redirect:/dashboard";
    }

    // 5. Upload file đơn
    @PostMapping("/ftp/upload")
    public String processUploadFile(@RequestParam(value = "file", required = false) org.springframework.web.multipart.MultipartFile file,
                                    HttpSession session) {
        if (file == null || file.isEmpty()) {
            session.setAttribute("ftpError", "Vui lòng chọn một file hợp lệ để tải lên!");
            return "redirect:/dashboard";
        }

        String host = (String) session.getAttribute("ftpHost");
        String username = (String) session.getAttribute("ftpUser");
        String password = (String) session.getAttribute("ftpPass");
        String fileName = file.getOriginalFilename();

        FTPClient ftpClient = new FTPClient();
        try {
            ftpClient.connect(host, 21);
            if (ftpClient.login(username, password)) {
                ftpClient.enterLocalPassiveMode();
                ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);

                boolean uploaded = ftpClient.storeFile(fileName, file.getInputStream());
                if (uploaded) {
                    session.setAttribute("ftpSuccess", "Tải lên file '" + fileName + "' thành công!");
                    addTransferLog(session, fileName, "UPLOAD", "Thành công");
                } else {
                    session.setAttribute("ftpError", "Tải lên thất bại do FTP Server từ chối lưu file!");
                    addTransferLog(session, fileName, "UPLOAD", "Thất bại");
                }

                ftpClient.logout();
                ftpClient.disconnect();
            }
        } catch (IOException e) {
            session.setAttribute("ftpError", "Lỗi truyền nhận file: " + e.getMessage());
            addTransferLog(session, fileName != null ? fileName : "Unknown", "UPLOAD", "Thất bại");
        }

        return "redirect:/dashboard";
    }

    // 6. Download file
    @GetMapping("/ftp/download")
    public void processDownloadFile(@RequestParam("fileName") String fileName,
                                    HttpSession session,
                                    jakarta.servlet.http.HttpServletResponse response) {
        String host = (String) session.getAttribute("ftpHost");
        String username = (String) session.getAttribute("ftpUser");
        String password = (String) session.getAttribute("ftpPass");

        FTPClient ftpClient = new FTPClient();
        try {
            ftpClient.connect(host, 21);
            if (ftpClient.login(username, password)) {
                ftpClient.enterLocalPassiveMode();
                ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);

                response.setContentType("application/octet-stream");
                response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

                boolean success = ftpClient.retrieveFile(fileName, response.getOutputStream());
                response.getOutputStream().flush();

                if (success) {
                    addTransferLog(session, fileName, "DOWNLOAD", "Thành công");
                } else {
                    addTransferLog(session, fileName, "DOWNLOAD", "Thất bại");
                }

                ftpClient.logout();
                ftpClient.disconnect();
            }
        } catch (IOException e) {
            e.printStackTrace();
            addTransferLog(session, fileName, "DOWNLOAD", "Thất bại");
        }
    }

    // 7. Xóa File hoặc Thư mục (Đệ quy)
    @PostMapping("/ftp/delete")
    public String processDeleteFile(@RequestParam("fileName") String fileName,
                                    @RequestParam("isDirectory") boolean isDirectory,
                                    HttpSession session) {
        String currentPath = (String) session.getAttribute("currentPath");
        if (currentPath == null) currentPath = "/";

        String host = (String) session.getAttribute("ftpHost");
        String username = (String) session.getAttribute("ftpUser");
        String password = (String) session.getAttribute("ftpPass");

        FTPClient ftpClient = new FTPClient();
        try {
            ftpClient.connect(host, 21);
            if (ftpClient.login(username, password)) {
                ftpClient.enterLocalPassiveMode();
                ftpClient.changeWorkingDirectory(currentPath);

                boolean deleted = false;
                if (!isDirectory) {
                    deleted = ftpClient.deleteFile(fileName);
                } else {
                    deleted = deleteDirectoryRecursive(ftpClient, fileName);
                }

                if (deleted) {
                    session.setAttribute("ftpSuccess", "Đã xóa thành công '" + fileName + "'!");
                    addTransferLog(session, fileName, "DELETE", "Thành công");
                } else {
                    session.setAttribute("ftpError", "Xóa thất bại (Thư mục có thể chứa dữ liệu bên trong hoặc không có quyền).");
                    addTransferLog(session, fileName, "DELETE", "Thất bại");
                }

                ftpClient.logout();
                ftpClient.disconnect();
            }
        } catch (IOException e) {
            session.setAttribute("ftpError", "Lỗi khi xóa: " + e.getMessage());
            addTransferLog(session, fileName, "DELETE", "Thất bại");
        }

        return "redirect:/dashboard";
    }

    // Hàm phụ trợ: Xóa đệ quy thư mục
    private boolean deleteDirectoryRecursive(FTPClient ftpClient, String dirName) throws IOException {
        if (!ftpClient.changeWorkingDirectory(dirName)) {
            return false;
        }

        FTPFile[] files = ftpClient.listFiles();
        if (files != null) {
            for (FTPFile file : files) {
                String name = file.getName();
                if (name.equals(".") || name.equals("..")) {
                    continue;
                }

                if (file.isDirectory()) {
                    deleteDirectoryRecursive(ftpClient, name);
                } else {
                    ftpClient.deleteFile(name);
                }
            }
        }

        ftpClient.changeToParentDirectory();
        return ftpClient.removeDirectory(dirName);
    }

    // 8. Duyệt thư mục con
    @GetMapping("/ftp/browse")
    public String browseFolder(@RequestParam("folderName") String folderName,
                               HttpSession session) {
        String currentPath = (String) session.getAttribute("currentPath");
        if (currentPath == null || currentPath.isEmpty()) {
            currentPath = "/";
        }
        
        String newPath = currentPath.endsWith("/") ? currentPath + folderName : currentPath + "/" + folderName;
        session.setAttribute("currentPath", newPath);
        return "redirect:/dashboard";
    }

    // 9. Quay lại thư mục cha
    @GetMapping("/ftp/back")
    public String backFolder(HttpSession session) {
        String currentPath = (String) session.getAttribute("currentPath");
        if (currentPath == null || currentPath.equals("/")) {
            session.setAttribute("currentPath", "/");
            return "redirect:/dashboard";
        }

        int lastSlash = currentPath.lastIndexOf('/');
        String parentPath = (lastSlash > 0) ? currentPath.substring(0, lastSlash) : "/";
        session.setAttribute("currentPath", parentPath);
        
        return "redirect:/dashboard";
    }

    // 10. Upload đa năng (File lẻ hoặc Thư mục)
    @PostMapping("/ftp/upload-any")
    public String processUploadAny(@RequestParam("files") org.springframework.web.multipart.MultipartFile[] files,
                                   HttpSession session) {
        if (files == null || files.length == 0) {
            session.setAttribute("ftpError", "Vui lòng chọn ít nhất một tệp hoặc thư mục!");
            return "redirect:/dashboard";
        }

        String host = (String) session.getAttribute("ftpHost");
        String username = (String) session.getAttribute("ftpUser");
        String password = (String) session.getAttribute("ftpPass");
        String currentPath = (String) session.getAttribute("currentPath");
        if (currentPath == null) currentPath = "/";

        FTPClient ftpClient = new FTPClient();
        try {
            ftpClient.connect(host, 21);
            if (ftpClient.login(username, password)) {
                ftpClient.enterLocalPassiveMode();
                ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);
                ftpClient.changeWorkingDirectory(currentPath);

                int successCount = 0;
                for (org.springframework.web.multipart.MultipartFile file : files) {
                    String relativePath = file.getOriginalFilename(); 
                    if (relativePath != null && !relativePath.isEmpty()) {
                        String[] parts = relativePath.split("/");
                        
                        for (int i = 0; i < parts.length - 1; i++) {
                            String folder = parts[i];
                            boolean dirExists = ftpClient.changeWorkingDirectory(folder);
                            if (!dirExists) {
                                ftpClient.makeDirectory(folder);
                                ftpClient.changeWorkingDirectory(folder);
                            }
                        }

                        String fileName = parts[parts.length - 1];
                        if (ftpClient.storeFile(fileName, file.getInputStream())) {
                            successCount++;
                            
                            // ==================================================
                            // 📌 GHI LOG RING CHO TỪNG FILE ĐƯỢC UPLOAD THÀNH CÔNG
                            // ==================================================
                            addTransferLog(session, relativePath, "UPLOAD", "Thành công");
                            
                        }
                        ftpClient.changeWorkingDirectory(currentPath);
                    }
                }

                ftpClient.logout();
                ftpClient.disconnect();

                if (successCount > 0) {
                    session.setAttribute("ftpSuccess", "Tải lên thành công " + successCount + " mục!");
                } else {
                    session.setAttribute("ftpError", "Không thể tải lên các tệp đã chọn.");
                }

            }
        } catch (IOException e) {
            session.setAttribute("ftpError", "Lỗi upload: " + e.getMessage());
            addTransferLog(session, "Tiến trình Upload", "UPLOAD", "Thất bại");
        }

        return "redirect:/dashboard";
    }

    // 11. Di chuyển file/thư mục (Kéo thả)
    @PostMapping("/ftp/move")
    public String processMoveItem(@RequestParam("sourceItem") String sourceItem,
                                  @RequestParam("targetFolder") String targetFolder,
                                  HttpSession session) {
        String currentPath = (String) session.getAttribute("currentPath");
        if (currentPath == null) currentPath = "/";

        String host = (String) session.getAttribute("ftpHost");
        String username = (String) session.getAttribute("ftpUser");
        String password = (String) session.getAttribute("ftpPass");

        FTPClient ftpClient = new FTPClient();
        try {
            ftpClient.connect(host, 21);
            if (ftpClient.login(username, password)) {
                ftpClient.enterLocalPassiveMode();
                ftpClient.changeWorkingDirectory(currentPath);

                String from = sourceItem;
                String to = targetFolder + "/" + sourceItem;

                boolean moved = ftpClient.rename(from, to);
                if (moved) {
                    session.setAttribute("ftpSuccess", "Đã di chuyển '" + sourceItem + "' vào '" + targetFolder + "' thành công!");
                    addTransferLog(session, sourceItem, "MOVE", "Thành công");
                } else {
                    session.setAttribute("ftpError", "Di chuyển thất bại! Kiểm tra lại quyền hoặc đường dẫn.");
                    addTransferLog(session, sourceItem, "MOVE", "Thất bại");
                }

                ftpClient.logout();
                ftpClient.disconnect();
            }
        } catch (IOException e) {
            session.setAttribute("ftpError", "Lỗi truyền nhận khi di chuyển: " + e.getMessage());
            addTransferLog(session, sourceItem, "MOVE", "Thất bại");
        }

        return "redirect:/dashboard";
    }

    // --- HÀM HỖ TRỢ GHI NHẬN HÀNG ĐỢI (TRANSFER QUEUE) ---
    private void addTransferLog(HttpSession session, String fileName, String action, String status) {
        List<TransferItemDTO> queueList = (List<TransferItemDTO>) session.getAttribute("transferQueue");
        if (queueList == null) {
            queueList = new ArrayList<>();
        }
        String timeNow = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy").format(new java.util.Date());
        
        // Thêm vào đầu danh sách để hiển thị tiến trình mới nhất lên trên
        queueList.add(0, new TransferItemDTO(fileName, action, status, timeNow));
        
        // Giới hạn lưu tối đa 10 tiến trình gần nhất
        if (queueList.size() > 10) {
            queueList.remove(queueList.size() - 1);
        }
        
        session.setAttribute("transferQueue", queueList);
        System.out.println(">>> [LOG HÀNG ĐỢI] Đã thêm thành công: " + fileName + " | Hành động: " + action + " | Trạng thái: " + status);
    }
}