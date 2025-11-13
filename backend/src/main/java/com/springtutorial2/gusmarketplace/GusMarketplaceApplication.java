package com.springtutorial2.gusmarketplace;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GusMarketplaceApplication {

    public static void main(String[] args) {
        // Load .env file for local development (optional - won't exist in Cloud Run)
        // In Cloud Run, environment variables are set directly and Spring Boot reads
        // them automatically
        // Environment variables take precedence over .env file values
        try {
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

            // Load MONGO_URI - prioritize environment variable (Cloud Run), fall back to
            // .env file (local dev)
            if (System.getenv("MONGO_URI") == null) {
                String mongoUri = dotenv.get("MONGO_URI");
                if (mongoUri != null && !mongoUri.isEmpty()) {
                    System.setProperty("MONGO_URI", mongoUri);
                }
            }
            // If MONGO_URI is already set as environment variable, Spring Boot will use it
            // automatically

            // Load Azure Content Safety configuration - prioritize environment variable,
            // fall back to .env
            if (System.getenv("AZURE_CONTENT_SAFETY_ENDPOINT") == null) {
                String endpoint = dotenv.get("AZURE_CONTENT_SAFETY_ENDPOINT", "");
                if (!endpoint.isEmpty()) {
                    System.setProperty("AZURE_CONTENT_SAFETY_ENDPOINT", endpoint);
                }
            }

            if (System.getenv("AZURE_CONTENT_SAFETY_SUBSCRIPTION_KEY") == null) {
                String key = dotenv.get("AZURE_CONTENT_SAFETY_SUBSCRIPTION_KEY", "");
                if (!key.isEmpty()) {
                    System.setProperty("AZURE_CONTENT_SAFETY_SUBSCRIPTION_KEY", key);
                }
            }
        } catch (Exception e) {
            // If Dotenv fails, environment variables (Cloud Run) or application.properties
            // will be used
            // This is expected in Cloud Run where .env file doesn't exist
            // No error needed - environment variables will be used instead
        }

        SpringApplication.run(GusMarketplaceApplication.class, args);
    }

    // @Bean
    // CommandLineRunner runner(GusService gusService) {
    // return args -> {
    // // You can initialize some data here if needed
    // // For example, you could create some sample listings
    // // gusService.createListing(new Listing("Sample Title", "Sample Description",
    // "Sample Category", "http://example.com/image.jpg", "10.00", "New",
    // "http://groupme.link"));
    // Listing sampleListing = new Listing(
    // "1",
    // "Gus",
    // "Sample Title",
    // "Sample Description",
    // "Sample Category",
    // "http://example.com/image.jpg",
    // "10.00",
    // "New",
    // "http://groupme.link"
    // );
    // gusService.createListing(sampleListing);
    // };
    // }
}
