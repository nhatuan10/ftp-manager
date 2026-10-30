package com.example.ftpmanager.model; // (Đổi package cho khớp với dự án của bạn nếu cần)

public class FtpFileDTO {
    private String name;
    private String size;
    private String timestamp;
    private boolean isDirectory;

    public FtpFileDTO(String name, String size, String timestamp, boolean isDirectory) {
        this.name = name;
        this.size = size;
        this.timestamp = timestamp;
        this.isDirectory = isDirectory;
    }

    // Getter và Setter
    public String getName() { return name; }
    public String getSize() { return size; }
    public String getTimestamp() { return timestamp; }
    public boolean isDirectory() { return isDirectory; }
}