package com.marketplace.backend.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3StorageService {

    private final S3Client s3Client;

    @Value("${cloud.s3.bucket-name}")
    private String bucketName;

    @Value("${cloud.s3.public-domain}")
    private String publicDomain;


    public String storeFile(MultipartFile file) {
        // 1. Validate file
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "");
        String contentType = file.getContentType();

        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Only image files are allowed!");
        }

        try {
            // 2. Generate Cloud-Safe Key (Folder structure inside bucket)
            String extension = originalFileName.substring(originalFileName.lastIndexOf("."));
            String objectKey = "products/" + UUID.randomUUID().toString().substring(0, 8) + extension;

            // 3. Build AWS Request
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(contentType)
                    // .acl(ObjectCannedACL.PUBLIC_READ) // Uncomment if your bucket requires explicit ACLs
                    .build();

            // 4. Upload directly to Cloud
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            log.info("Successfully saved image {} to S3 bucket {}", objectKey, bucketName);

            // 5. Return the absolute public URL
            return publicDomain + "/" + objectKey;

        } catch (IOException ex) {
            log.error("Failed to read image byte stream", ex);
            throw new RuntimeException("Could not process image upload.", ex);
        } catch (Exception ex) {
            log.error("S3 Upload Failed", ex);
            throw new RuntimeException("Cloud storage provider rejected the upload.", ex);
        }
    }
}
