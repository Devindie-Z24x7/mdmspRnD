package com.example.mdmspaws.controller;

import com.example.mdmspaws.dto.DownloadFileRequestDto;
import com.example.mdmspaws.service.S3Service;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class S3Controller {

    private final S3Service s3Service;

    public S3Controller(S3Service s3Service) {
        this.s3Service = s3Service;
    }

    @PostMapping("/download")
    public ResponseEntity<?> downloadMultipleFiles(@RequestBody DownloadFileRequestDto request) {
        try {
            Long requestId = s3Service.download(request);

            return ResponseEntity.ok(requestId);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }


}
