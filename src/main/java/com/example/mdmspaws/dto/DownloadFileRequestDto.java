package com.example.mdmspaws.dto;

import java.util.List;

public class DownloadFileRequestDto {

    private String bucketName;
    private List<String> objectKeys;

    // Getters and Setters

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public List<String> getObjectKeys() {
        return objectKeys;
    }

    public void setObjectKeys(List<String> objectKeys) {
        this.objectKeys = objectKeys;
    }
}

