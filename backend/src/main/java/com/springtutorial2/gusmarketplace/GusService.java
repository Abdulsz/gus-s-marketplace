package com.springtutorial2.gusmarketplace;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import static com.springtutorial2.gusmarketplace.ContentSafetyService.ContentModerationException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GusService {
    private final AmazonS3 s3Client;
    private final GusRepository gusRepository;
    private final ContentSafetyService contentSafetyService;
    private final Environment environment;

    public GusService(AmazonS3 s3Client, GusRepository gusRepository,
                      ContentSafetyService contentSafetyService, Environment environment) {
        this.s3Client = s3Client;
        this.gusRepository = gusRepository;
        this.contentSafetyService = contentSafetyService;
        this.environment = environment;
    }

    public List<Listing> getAllListings() {
        // Return non-expired listings, newest first
        return gusRepository.findByExpiresAtAfterOrderByCreatedAtDesc(new Date());
    }

    /**
     * Creates a listing with image moderation before S3 upload.
     * This ensures inappropriate images are never uploaded to S3.
     *
     * @param userName    User name
     * @param title       Listing title
     * @param description Listing description
     * @param category    Listing category
     * @param price       Listing price
     * @param condition   Item condition
     * @param groupMeLink GroupMe link
     * @param imageFile   Image file (can be null)
     * @return The created listing
     * @throws ContentModerationException if content moderation fails
     */
    public Listing createListingWithImage(
            String userName,
            String title,
            String description,
            String category,
            String price,
            String condition,
            String groupMeLink,
            MultipartFile imageFile) throws ContentModerationException, IOException {

        String imageUrl = null;

        // If image is provided, moderate it first, then upload to S3
        if (imageFile != null && !imageFile.isEmpty()) {
            // Moderate image before uploading to S3
            byte[] imageBytes = imageFile.getBytes();
            if (!contentSafetyService.moderateImageFromBytes(imageBytes)) {
                throw new ContentModerationException(
                        "Listing contains inappropriate image content and cannot be created.");
            }

            // Upload to S3 only after moderation passes
            imageUrl = uploadImageToS3(imageFile);
        }

        // Moderate text content (title and description)
        String textToModerate = (title != null ? title : "") + " " +
                (description != null ? description : "");

        if (!textToModerate.trim().isEmpty()) {
            if (!contentSafetyService.moderateText(textToModerate)) {
                throw new ContentModerationException(
                        "Listing contains inappropriate text content and cannot be created.");
            }
        }

        // Create and save listing
        Date now = Date.from(Instant.now());
        Date expiresAt = Date.from(Instant.now().plus(30, ChronoUnit.DAYS));
        Listing listing = new Listing(
                null, // ID will be auto-generated
                userName,
                title,
                description,
                category,
                imageUrl,
                price,
                condition,
                groupMeLink,
                now,
                expiresAt);
        
        gusRepository.save(listing);
        return listing;
    }

    /**
     * Uploads an image file directly to S3 and returns the public URL
     */
    private String uploadImageToS3(MultipartFile imageFile) throws IOException {
        String bucketName = "gus-market-listing-imgs";

        // Preserve original file extension or default to .jpg
        String originalFilename = imageFile.getOriginalFilename();
        String extension = ".jpg"; // Default extension
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String fileName = UUID.randomUUID().toString() + extension;

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(imageFile.getContentType());
        metadata.setContentLength(imageFile.getSize());

        try (InputStream inputStream = imageFile.getInputStream()) {
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    bucketName,
                    fileName,
                    inputStream,
                    metadata);

            s3Client.putObject(putObjectRequest);
        }

        return "https://" + bucketName + ".s3.us-east-2.amazonaws.com/" + fileName;
    }

    public List<Listing> getListingsByCategory(String category) {

        List<Listing> listings = gusRepository.findListingByCategory(category);

        if (listings.isEmpty()) {
            throw new RuntimeException("No listings found for category: " + category);
        }

        return listings;
    }

    public List<Listing> getListingsByTitle(String title) {
        List<Listing> listings = gusRepository.findListingByTitle(title);

        if (listings.isEmpty()) {
            throw new RuntimeException("No listings found for title: " + title);
        }

        return listings;
    }

    public void deleteListing(String id) {
        gusRepository.deleteById(id);
    }

    public Map<String, String> generateUploadUrl() {
        String bucketName = "gus-market-listing-imgs";
        String fileName = UUID.randomUUID().toString() + ".jpg";

        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, fileName)
                .withMethod(com.amazonaws.HttpMethod.PUT)
                .withExpiration(Date.from(Instant.now().plus(15, ChronoUnit.HOURS)))
                .withContentType("image/jpeg");

        URL url = s3Client.generatePresignedUrl(request);

        // Return the upload and file URLs
        String uploadUrl = url != null ? url.toString() : "";
        String fileUrl = "https://" + bucketName + ".s3.us-east-2.amazonaws.com/" + fileName;
        return Map.of(
                "uploadUrl", uploadUrl,
                "fileUrl", fileUrl);
    }

    public String contactSeller(String listingId, String buyer, String message) {

        Listing listing = gusRepository.findById(listingId)
                .orElseThrow(() -> new RuntimeException("Listing not found with id: " + listingId));

        String seller = listing.getUserName();
        if (seller == null || seller.isBlank()) {
            throw new IllegalStateException("Listing has no seller email");
        }

        String html = String.format(
                "<p><strong>%s</strong> is interested in your listing: <em>%s</em></p><p>Message:<br/>%s</p>",
                buyer, listing.getTitle(), message);

        String apiKey = System.getenv("RESEND_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            // Fallback to Java system property set from .env in local dev
            apiKey = System.getProperty("RESEND_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("RESEND_API_KEY is not configured");
        }

        Resend resend = new Resend(apiKey);

        CreateEmailOptions params = CreateEmailOptions.builder()
                .from("Gus <system@gusmarketplace.com>")
                .to(new String[]{seller})
                .subject("Buyer request for your item")
                .html(html)
                .build();

        try {
            CreateEmailResponse data = resend.emails().send(params);
            return "Message sent to seller. Email id: " + data.getId();
        } catch (ResendException e) {
            throw new RuntimeException("Failed to send contact email: " + e.getMessage(), e);
        }
    }

    /**
     * Sends a Contact Us feedback email to the configured recipient (default mahatnitai@gmail.com).
     * Uses  Resend setup as contactSeller.
     */
    public void sendContactUsEmail(String name, String email, String message) {
        // #region agent log
        try {
            String line = "{\"location\":\"GusService.sendContactUsEmail:entry\",\"message\":\"service called\",\"data\":{\"hasName\":" + (name != null && !name.isBlank()) + ",\"hasEmail\":" + (email != null && !email.isBlank()) + ",\"messageLen\":" + (message != null ? message.length() : 0) + "},\"hypothesisId\":\"D\",\"timestamp\":" + System.currentTimeMillis() + "}\n";
            Files.write(Paths.get("c:\\Users\\death\\gus-s-marketplace\\.cursor\\debug.log"), line.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception _e) { }
        // #endregion
        String to = environment.getProperty("contact.us.recipient", "mahatnitai@gmail.com");
        if (to == null || to.isBlank()) {
            to = "mahatnitai@gmail.com";
        }

        StringBuilder body = new StringBuilder();
        if (name != null && !name.isBlank()) {
            body.append("<p><strong>From:</strong> ").append(escapeHtml(name)).append("</p>");
        }
        if (email != null && !email.isBlank()) {
            body.append("<p><strong>Email:</strong> ").append(escapeHtml(email)).append("</p>");
        }
        body.append("<p><strong>Message:</strong></p><p>").append(escapeHtml(message != null ? message : "")).append("</p>");
        String html = body.toString();

        String apiKey = System.getenv("RESEND_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getProperty("RESEND_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            // #region agent log
            try {
                String line = "{\"location\":\"GusService.sendContactUsEmail:noApiKey\",\"message\":\"RESEND_API_KEY not configured\",\"hypothesisId\":\"D\",\"timestamp\":" + System.currentTimeMillis() + "}\n";
                Files.write(Paths.get("c:\\Users\\death\\gus-s-marketplace\\.cursor\\debug.log"), line.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception _e) { }
            // #endregion
            throw new IllegalStateException("RESEND_API_KEY is not configured");
        }

        Resend resend = new Resend(apiKey);
        CreateEmailOptions params = CreateEmailOptions.builder()
                .from("Gus <system@gusmarketplace.com>")
                .to(new String[]{to})
                .subject("Gus Marketplace – Contact Us")
                .html(html)
                .build();

        try {
            // #region agent log
            try {
                String line = "{\"location\":\"GusService.sendContactUsEmail:beforeSend\",\"message\":\"about to call Resend\",\"hypothesisId\":\"D\",\"timestamp\":" + System.currentTimeMillis() + "}\n";
                Files.write(Paths.get("c:\\Users\\death\\gus-s-marketplace\\.cursor\\debug.log"), line.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception _e) { }
            // #endregion
            resend.emails().send(params);
        } catch (ResendException e) {
            // #region agent log
            try {
                String err = (e.getMessage() != null ? e.getMessage() : "null").replace("\\", "\\\\").replace("\"", "\\\"");
                String line = "{\"location\":\"GusService.sendContactUsEmail:ResendException\",\"message\":\"Resend failed\",\"data\":{\"errorMessage\":\"" + err + "\"},\"hypothesisId\":\"D\",\"timestamp\":" + System.currentTimeMillis() + "}\n";
                Files.write(Paths.get("c:\\Users\\death\\gus-s-marketplace\\.cursor\\debug.log"), line.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception _e) { }
            // #endregion
            throw new RuntimeException("Failed to send contact us email: " + e.getMessage(), e);
        }
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

}
