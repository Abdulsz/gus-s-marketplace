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
        try {
            System.out.println("=== Initializing AWS S3 Client ===");
            System.out.println("Current working directory: " + System.getProperty("user.dir"));
            
            // Use the same approach as GusMarketplaceApplication
            Dotenv dotenv = Dotenv.configure()
                    .ignoreIfMissing()
                    .load();
            String accessKey = dotenv.get("AWS_ACCESS_KEY_ID");
            String secretKey = dotenv.get("AWS_SECRET_ACCESS_KEY");
            
            System.out.println("Access Key loaded: " + (accessKey != null && !accessKey.isEmpty()));
            System.out.println("Secret Key loaded: " + (secretKey != null && !secretKey.isEmpty()));
            
            if (accessKey != null && !accessKey.isEmpty()) {
                System.out.println("Access Key starts with: " + accessKey.substring(0, Math.min(8, accessKey.length())) + "...");
            }
            
            if (accessKey == null || secretKey == null || accessKey.isEmpty() || secretKey.isEmpty()) {
                System.err.println("WARNING: AWS credentials not found in .env file.");
                System.err.println("Current working directory: " + System.getProperty("user.dir"));
                System.err.println("Image upload will not work. Returning placeholder client.");
                return AmazonS3ClientBuilder.standard()
                        .withRegion("us-east-2")
                        .withCredentials(new AWSStaticCredentialsProvider(
                                new BasicAWSCredentials("placeholder", "placeholder")))
                        .build();
            }
            
            System.out.println("Creating AWS S3 client...");
            BasicAWSCredentials awsCredentials = new BasicAWSCredentials(accessKey, secretKey);
            AmazonS3 client = AmazonS3ClientBuilder.standard()
                    .withRegion("us-east-2")
                    .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
                    .build();
            
            System.out.println("AWS S3 client created successfully!");
            return client;
        } catch (Exception e) {
            System.err.println("WARNING: Failed to initialize AWS S3 client: " + e.getMessage());
            System.err.println("Image upload will not work. Please check your .env file.");
            e.printStackTrace();
            return AmazonS3ClientBuilder.standard()
                    .withRegion("us-east-2")
                    .withCredentials(new AWSStaticCredentialsProvider(
                            new BasicAWSCredentials("placeholder", "placeholder")))
                    .build();
        }
    }
}