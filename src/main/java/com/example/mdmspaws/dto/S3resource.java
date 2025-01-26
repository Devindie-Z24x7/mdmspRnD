package com.example.mdmspaws.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class S3resource {
    private String uri;
    private Boolean isAvailable;

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public Boolean getAvailable() {
        return isAvailable;
    }

    public void setAvailable(Boolean available) {
        isAvailable = available;
    }
}
