package com.springtutorial2.gusmarketplace;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AwsConfig {

    @Bean
    public AmazonS3 amazonS3() {
        // Load from .env file (local dev) or environment variables (Cloud Run)
        String accessKeyId = null;
        String secretAccessKey = null;

        // Try environment variables first (Cloud Run)
        accessKeyId = System.getenv("AWS_ACCESS_KEY_ID");
        secretAccessKey = System.getenv("AWS_SECRET_ACCESS_KEY");

        // If not found, try .env file (local development)
        if (accessKeyId == null || secretAccessKey == null) {
            try {
                Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
                if (accessKeyId == null) {
                    accessKeyId = dotenv.get("AWS_ACCESS_KEY_ID");
                }
                if (secretAccessKey == null) {
                    secretAccessKey = dotenv.get("AWS_SECRET_ACCESS_KEY");
                }
            } catch (Exception e) {
                // If Dotenv fails, use environment variables (expected in Cloud Run)
            }
        }

        if (accessKeyId == null || secretAccessKey == null) {
            throw new IllegalStateException(
                    "AWS credentials (AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY) must be set via environment variables or .env file");
        }

        BasicAWSCredentials awsCredentials = new BasicAWSCredentials(accessKeyId, secretAccessKey);
        return AmazonS3ClientBuilder.standard()
                .withRegion("us-east-2")
                .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
                .build();
    }
}