package com.example.mdmspaws.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class DownloadFileRequestDto {

    private String bucketName;
    private List<String> objectKeys;


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

