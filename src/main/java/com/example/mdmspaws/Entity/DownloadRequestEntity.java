package com.example.mdmspaws.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "download_requests")
@Setter
@Getter
public class DownloadRequestEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "presigned_url", length = 2000)
    private String url;

    @Column(name = "is_completed", nullable = false, columnDefinition="tinyint(1) default 0")
    private boolean isCompleted;


    public Long getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }
}