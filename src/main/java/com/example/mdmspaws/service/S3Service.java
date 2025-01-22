package com.example.mdmspaws.service;

import com.example.mdmspaws.dto.DownloadFileRequestDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@EnableAsync
@Service
@Slf4j
public class S3Service {

    private final S3Client s3Client;

    @Autowired
    public S3Service(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public String copyAndGeneratePresignedUrl(DownloadFileRequestDto request) {
        String bucketName = request.getBucketName();
        String tempFolderPrefix = "temporary/" + UUID.randomUUID() + "/";
        String zipFileKey = tempFolderPrefix + "files.zip";

        try {
            // Step 1: Create ZIP in S3 and upload it directly to S3
            createZipInS3(bucketName, zipFileKey, request.getObjectKeys());

            // Step 2: Generate Pre-Signed URL for ZIP File
            return generatePresignedUrl(bucketName, zipFileKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate presigned URL for zipped content.", e);
        }
    }

    private InputStream getFileAsStream(String bucketName, String objectKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();
        return s3Client.getObject(getObjectRequest);
    }

    private void createZipInS3(String bucketName, String zipFileKey, List<String> objectKeys) throws IOException {
        // Use a custom S3OutputStream to stream ZIP content directly to S3
        try (ZipOutputStream zipOut = new ZipOutputStream(new S3OutputStream(s3Client, bucketName, zipFileKey))) {
            for (String objectKey : objectKeys) {
                try (InputStream fileStream = getFileAsStream(bucketName, objectKey)) {
                    String fileName = objectKey.substring(objectKey.lastIndexOf("/") + 1); // Extract file name
                    zipOut.putNextEntry(new ZipEntry(fileName));

                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = fileStream.read(buffer)) > 0) {
                        zipOut.write(buffer, 0, length); // Write data to the ZIP
                    }
                    zipOut.closeEntry();
                } catch (Exception e) {
                }
            }
        }
    }

    private String generatePresignedUrl(String bucketName, String zipFileKey) {
        try (S3Presigner presigner = S3Presigner.builder()
                .region(Region.of("eu-north-1"))
                .build()) {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(zipFileKey)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(java.time.Duration.ofHours(1)) // URL validity duration
                    .getObjectRequest(getRequest)
                    .build();

            return presigner.presignGetObject(presignRequest).url().toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate presigned URL: " + e.getMessage(), e);
        }
    }

    // Custom OutputStream to handle streaming ZIP data directly to S3
    private static class S3OutputStream extends OutputStream {

        private final S3Client s3Client;
        private final String bucketName;
        private final String key;
        private final ByteArrayOutputStream buffer;

        public S3OutputStream(S3Client s3Client, String bucketName, String key) {
            this.s3Client = s3Client;
            this.bucketName = bucketName;
            this.key = key;
            this.buffer = new ByteArrayOutputStream();
        }

        @Override
        public void write(int b) {
            buffer.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            buffer.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            super.close();
            // Upload buffered content to S3
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.putObject(putRequest, RequestBody.fromBytes(buffer.toByteArray()));
        }
    }
}
