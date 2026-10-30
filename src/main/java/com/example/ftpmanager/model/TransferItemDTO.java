package com.example.ftpmanager.model;

public class TransferItemDTO {
    private String fileName;
    private String actionType; // UPLOAD, DOWNLOAD, DELETE, MOVE
    private String status;     // Thành công, Thất bại
    private String time;       // Thời gian thực hiện

    public TransferItemDTO(String fileName, String actionType, String status, String time) {
        this.fileName = fileName;
        this.actionType = actionType;
        this.status = status;
        this.time = time;
    }

    // Getters và Setters
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
}