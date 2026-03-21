package com.marketplace.backend.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
//@RequiredArgsConstructor
public class FileStorageService {


    private final Path fileStorageLocation;

    public FileStorageService() {
        this.fileStorageLocation = Paths.get("uploads").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);

        }
    }

    public String storeFile(MultipartFile file, UUID productId) {
        // 1. Validate the file
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        if (originalFileName.contains("..")) {
            throw new IllegalArgumentException("Sorry! Filename contains invalid path sequence " + originalFileName);
        }

        // 2. Enforce Image Types (Security: Prevent uploading executable scripts)
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Only image files are allowed!");
        }

        try {
            // 3. Generate a secure, unique filename to prevent overwriting
            String fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
            String newFileName = productId.toString() + "_" + UUID.randomUUID().toString().substring(0, 8) + fileExtension;

            // 4. Copy file to the target location
            Path targetLocation = this.fileStorageLocation.resolve(newFileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            log.info("Successfully saved image {} for product {}", newFileName, productId);

            // 5. Return the relative URL path that the frontend will use to fetch it
            return "/uploads/" + newFileName;

        } catch (IOException ex) {
            throw new RuntimeException("Could not store file " + originalFileName + ". Please try again!", ex);
        }
    }

}

