package com.marketplace.backend.configs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
public class S3Config {

    @Value("${cloud.s3.access-key}")
    private String accessKey;

    @Value("${cloud.s3.secret-key}")
    private String secretKey;

    @Value("${cloud.s3.region}")
    private String region;

    @Value("${cloud.s3.endpoint:}")
    private String endpoint;

    @Bean
    public S3Client s3Client() {
        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
        );

        var builder = S3Client.builder()
                .credentialsProvider(credentials)
                .region(Region.of(region));

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        return builder.build();
    }
}
