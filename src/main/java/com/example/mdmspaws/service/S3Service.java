package com.example.mdmspaws.service;

import com.example.mdmspaws.Entity.DownloadRequestEntity;
import com.example.mdmspaws.Repository.DownloadRequestRepository;
import com.example.mdmspaws.dto.DownloadFileRequestDto;
import com.example.mdmspaws.dto.DownloadableS3Request;
import com.example.mdmspaws.dto.S3resource;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
public class S3Service {

    private final S3Client s3Client;
    private final DownloadRequestRepository downloadRequestRepository;

    @Autowired
    public S3Service(S3Client s3Client, DownloadRequestRepository downloadRequestRepository) {
        this.s3Client = s3Client;
        this.downloadRequestRepository = downloadRequestRepository;
    }

    public Long download(DownloadFileRequestDto request){

        System.out.println("raw object Keys: " + request.getObjectKeys());

        if (request.getObjectKeys() == null || request.getObjectKeys().isEmpty()) {
            throw new IllegalArgumentException("No object keys provided in the request.");
        }

        DownloadableS3Request downloadableS3Request = validateDownloadRequest(request);

        Long id = createDBEntry(request).getId();

        System.out.println("converted object Keys: " + downloadableS3Request.getObjectKeys());

        copyAndGeneratePresignedUrl(downloadableS3Request,id );

        return id;
    }

    private DownloadRequestEntity createDBEntry(DownloadFileRequestDto downloadableS3Request){
        DownloadRequestEntity downloadRequestEntity = new DownloadRequestEntity();
        downloadRequestEntity.setCompleted(false);

        return downloadRequestRepository.save(downloadRequestEntity);

    }

    private DownloadableS3Request validateDownloadRequest(DownloadFileRequestDto request){

        List<String> requestObjectKeys= request.getObjectKeys();
        System.out.println("Request object keys: " + requestObjectKeys);

        List<S3resource> s3Resources = requestObjectKeys.stream()
                .map(this::checkResourceAvailabilityInS3)
                .toList();

        System.out.println("Request object keys (URIs): " +
                s3Resources.stream()
                        .map(S3resource::getUri) // Extract the URI from each S3resource
                        .toList() // Collect the URIs into a list
        );

        System.out.println("Request object keys (aval): " +
                s3Resources.stream()
                        .map(S3resource::getAvailable) // Extract the URI from each S3resource
                        .toList() // Collect the URIs into a list
        );

        List<String> availableObjectKeys = new ArrayList<>();

        s3Resources.stream()
                .filter(S3resource::getAvailable)
                .forEach(s3resource -> availableObjectKeys.add(s3resource.getUri()));


        if (availableObjectKeys.isEmpty()) {
            throw new RuntimeException("No valid S3 objects available for download.");
        }

        DownloadableS3Request downloadableS3Request = new DownloadableS3Request();
        downloadableS3Request.setObjectKeys(availableObjectKeys);

        return downloadableS3Request;

    }



    private S3resource checkResourceAvailabilityInS3(String objectKey) {

        S3resource resource = new S3resource();

        resource.setUri(objectKey);

        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket("mdmsp")
                    .key(objectKey)
                    .build();

            s3Client.headObject(headObjectRequest);
            resource.setAvailable(true);

        } catch (NoSuchKeyException e) {
            resource.setAvailable(false);
        }

        return resource;
    }

    
    public void copyAndGeneratePresignedUrl(DownloadableS3Request downloadableRequest, Long downloadableRequestId) {
        String bucketName = "mdmsp";
        String tempFolderPrefix = "temporary/" + UUID.randomUUID() + "/";
        String zipFileKey = tempFolderPrefix + "files.zip";

        System.out.println("Object Keys: " + downloadableRequest.getObjectKeys());

        try {
            // Step 1: Create ZIP in S3 and upload it directly to S3
            createZipInS3(bucketName, zipFileKey, downloadableRequest.getObjectKeys());

            // Step 2: Generate Pre-Signed URL for ZIP File
            String presignedUrl = generatePresignedUrl(bucketName, zipFileKey);

            System.out.println("Generated Pre-Signed URL: " + presignedUrl);

            updateDB(presignedUrl, downloadableRequestId);

        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void updateDB(String presignedUrl,Long downloadableRequestId) {
        DownloadRequestEntity entity = downloadRequestRepository.findById(downloadableRequestId)
                .orElseThrow(() -> new EntityNotFoundException("DownloadRequestEntity not found for ID: " + downloadableRequestId));

        entity.setUrl(presignedUrl);
        entity.setCompleted(true);// Assuming your entity has a setter for presignedUrl
        downloadRequestRepository.save(entity); // Save the updated entity back to the database
    }

    private InputStream getFileAsStream(String bucketName, String objectKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();
        return s3Client.getObject(getObjectRequest);
    }

    private void createZipInS3(String bucketName, String zipFileKey, List<String> objectKeys) throws IOException {
        if (objectKeys == null || objectKeys.isEmpty()) {
            throw new RuntimeException("No object keys provided for ZIP creation.");
        }
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
                    throw new RuntimeException(e.getMessage(), e);
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
