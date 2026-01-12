package com.springtutorial2.gusmarketplace;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.Getter;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ContentSafetyService {

    private static final Logger logger = LoggerFactory.getLogger(ContentSafetyService.class);

    // The version of the Content Safety API to use.
    private static final String API_VERSION = "2024-09-01";
    // The valid threshold values.
    private static final List<Integer> VALID_THRESHOLD_VALUES = Arrays.asList(-1, 0, 2, 4, 6);
    // The media type for JSON data with the UTF-8 character encoding
    private static final okhttp3.MediaType JSON = okhttp3.MediaType.parse("application/json; charset=utf-8");
    // The HTTP client.
    private static final OkHttpClient client = new OkHttpClient();
    // The instance of ObjectMapper class used to serialize and deserialize JSON
    // data
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String endpoint;
    private final String subscriptionKey;

    public ContentSafetyService(
            @Value("${azure.content-safety.endpoint:}") String endpoint,
            @Value("${azure.content-safety.subscription-key:}") String subscriptionKey) {
        this.endpoint = endpoint;
        this.subscriptionKey = subscriptionKey;
    }

    /**
     * Checks if content moderation is enabled (endpoint and key are configured)
     */
    public boolean isEnabled() {
        boolean enabled = endpoint != null && !endpoint.isEmpty() &&
                subscriptionKey != null && !subscriptionKey.isEmpty();
        if (!enabled) {
            logger.debug(
                    "Content Safety Service disabled (missing endpoint or key). Endpoint configured: {}, Key configured: {}",
                    endpoint != null && !endpoint.isEmpty(),
                    subscriptionKey != null && !subscriptionKey.isEmpty());
        } else {
            logger.debug("Content Safety Service is ENABLED");
        }
        return enabled;
    }

    /**
     * Moderates text content and returns true if content is acceptable
     */
    public boolean moderateText(String text) throws ContentModerationException {
        if (!isEnabled()) {
            return true; // Skip moderation if not configured
        }

        try {
            DetectionResult result = detect(MediaType.Text, text, new String[] {});
            Map<Category, Integer> rejectThresholds = getDefaultRejectThresholds();
            Decision decision = makeDecision(result, rejectThresholds);
            return decision.getSuggestedAction() == Action.Accept;
        } catch (DetectionException e) {
            throw new ContentModerationException("Failed to moderate text content: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new ContentModerationException("Failed to moderate text content", e);
        }
    }

    /**
     * Moderates image content (base64 encoded) and returns true if content is
     * acceptable
     */
    public boolean moderateImage(String base64Image) throws ContentModerationException {
        logger.debug("Starting image moderation (base64 length: {})", base64Image != null ? base64Image.length() : 0);

        if (!isEnabled()) {
            logger.debug(
                    "Image moderation skipped - Content Safety disabled. Accepting without moderation.");
            return true; // Skip moderation if not configured
        }

        try {
            logger.debug("Calling Azure Content Safety API for image detection");
            DetectionResult result = detect(MediaType.Image, base64Image, new String[] {});

            // Log detailed detection results at debug only
            if (result != null && result.getCategoriesAnalysis() != null) {
                logger.debug("Image detection results received with {} categories",
                        result.getCategoriesAnalysis().size());
            } else {
                logger.debug("Image detection result is null or has no categories analysis");
            }

            Map<Category, Integer> rejectThresholds = getDefaultRejectThresholds();
            logger.debug("Using reject thresholds: {}", rejectThresholds);

            Decision decision = makeDecision(result, rejectThresholds);
            boolean accepted = decision.getSuggestedAction() == Action.Accept;

            logger.info("Image moderation {}", accepted ? "ACCEPTED" : "REJECTED");
            logger.debug("Per-category decisions: {}", decision.getActionByCategory());

            return accepted;
        } catch (DetectionException e) {
            logger.error("DetectionException during image moderation - Code: {}, Message: {}", e.getCode(),
                    e.getMessage(), e);
            throw new ContentModerationException("Failed to moderate image content: " + e.getMessage(), e);
        } catch (IOException e) {
            logger.error("IOException during image moderation", e);
            throw new ContentModerationException("Failed to moderate image content", e);
        }
    }

    /**
     * Moderates image from byte array
     */
    public boolean moderateImageFromBytes(byte[] imageBytes) throws ContentModerationException {
        logger.debug("Starting image moderation from bytes (size: {} bytes)",
                imageBytes != null ? imageBytes.length : 0);

        if (!isEnabled()) {
            logger.debug(
                    "Image moderation from bytes skipped - Content Safety disabled. Accepting without moderation.");
            return true; // Skip moderation if not configured
        }

        if (imageBytes == null || imageBytes.length == 0) {
            logger.debug("Image bytes are null or empty - accepting without moderation");
            return true; // No image to moderate
        }

        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        logger.debug("Converted image bytes to base64 (length: {})", base64Image.length());
        return moderateImage(base64Image);
    }

    /**
     * Moderates image from URL by downloading and converting to base64
     */
    public boolean moderateImageFromUrl(String imageUrl) throws ContentModerationException {
        logger.debug("Starting image moderation from URL: {}", imageUrl);

        if (!isEnabled()) {
            logger.debug(
                    "Image moderation from URL skipped - Content Safety disabled. Accepting without moderation.");
            return true; // Skip moderation if not configured
        }

        if (imageUrl == null || imageUrl.isEmpty()) {
            logger.debug("Image URL is null or empty - accepting without moderation");
            return true; // No image to moderate
        }

        try {
            logger.debug("Downloading image from URL: {}", imageUrl);
            // Download image from URL
            Request request = new Request.Builder()
                    .url(imageUrl)
                    .get()
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    logger.error("Failed to download image from URL: {} - Response code: {}, Body is null: {}",
                            imageUrl, response.code(), response.body() == null);
                    throw new ContentModerationException("Failed to download image from URL: " + imageUrl);
                }

                byte[] imageBytes = response.body().bytes();
                logger.debug("Successfully downloaded image from URL (size: {} bytes)", imageBytes.length);
                return moderateImageFromBytes(imageBytes);
            }
        } catch (IOException e) {
            logger.error("IOException while downloading or moderating image from URL: {}", imageUrl, e);
            throw new ContentModerationException("Failed to download or moderate image from URL: " + imageUrl, e);
        }
    }

    /**
     * Gets default reject thresholds for content moderation
     * Thresholds: -1 (disabled), 0 (reject all), 2 (reject low+), 4 (reject
     * medium+), 6 (reject high only)
     * For strict moderation, use 2 to reject low, medium, and high severity content
     */
    private Map<Category, Integer> getDefaultRejectThresholds() {
        Map<Category, Integer> rejectThresholds = new HashMap<>();
        rejectThresholds.put(Category.Hate, 4); // Reject low, medium, and high severity hate content
        rejectThresholds.put(Category.SelfHarm, 4); // Reject low, medium, and high severity self-harm content
        rejectThresholds.put(Category.Sexual, 4); // Reject low, medium, and high severity sexual content (strict)
        rejectThresholds.put(Category.Violence, 4); // Reject low, medium, and high severity violent content
        logger.debug("Default reject thresholds configured");
        return rejectThresholds;
    }

    /**
     * Enumeration for media types
     */
    public enum MediaType {
        Text,
        Image
    }

    /**
     * Enumeration for categories
     */
    public enum Category {
        Hate,
        SelfHarm,
        Sexual,
        Violence
    }

    /**
     * Enumeration for actions
     */
    public enum Action {
        Accept,
        Reject
    }

    /**
     * Custom exception for content moderation failures
     */
    public static class ContentModerationException extends Exception {
        public ContentModerationException(String message) {
            super(message);
        }

        public ContentModerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Represents an exception raised when there is an error in detecting the
     * content.
     */
    @Getter
    public static class DetectionException extends Exception {
        private final String code;
        private final String message;

        /**
         * Constructs a new detection exception.
         *
         * @param code    The error code.
         * @param message The error message.
         */
        public DetectionException(String code, String message) {
            super(String.format("Error Code: %s, Message: %s", code, message));
            this.code = code;
            this.message = message;
        }
    }

    /**
     * Represents the decision made by the content moderation system.
     */
    @Getter
    public static class Decision {
        private final Action suggestedAction;
        private final Map<Category, Action> actionByCategory;

        /**
         * Constructs a new decision.
         *
         * @param suggestedAction  The overall action suggested by the system.
         * @param actionByCategory The actions suggested by the system for each
         *                         category.
         */
        public Decision(Action suggestedAction, Map<Category, Action> actionByCategory) {
            this.suggestedAction = suggestedAction;
            this.actionByCategory = actionByCategory;
        }
    }

    /**
     * Base class for detection requests.
     */
    @Data
    @lombok.EqualsAndHashCode(callSuper = false)
    public static class DetectionRequest {
    }

    /**
     * Represents an image to be detected.
     */
    @Data
    public static class Image {
        private String content;

        /**
         * Constructs a new image.
         *
         * @param content The base64-encoded content of the image.
         */
        public Image(String content) {
            this.content = content;
        }
    }

    /**
     * Represents an image detection request.
     */
    @Data
    @lombok.EqualsAndHashCode(callSuper = false)
    public static class ImageDetectionRequest extends DetectionRequest {
        private Image image;

        /**
         * Constructs a new image detection request.
         *
         * @param content The base64-encoded content of the image.
         */
        public ImageDetectionRequest(String content) {
            this.image = new Image(content);
        }
    }

    /**
     * Represents a text detection request.
     */
    @Data
    @lombok.EqualsAndHashCode(callSuper = false)
    public static class TextDetectionRequest extends DetectionRequest {
        private String text;
        private String[] blocklistNames;

        /**
         * Constructs a new text detection request.
         *
         * @param text           The text to be detected.
         * @param blocklistNames The names of the blocklists to use for detecting the
         *                       text.
         */
        public TextDetectionRequest(String text, String[] blocklistNames) {
            this.text = text;
            this.blocklistNames = blocklistNames;
        }
    }

    /**
     * Represents a detailed detection result for a specific category.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class DetailedResult {
        private Category category; // The category of the detection result.
        private Integer severity; // The severity of the detection result.

        public DetailedResult() {
        }

        public DetailedResult(Category category, Integer severity) {
            this.category = category;
            this.severity = severity;
        }
    }

    /**
     * Base class for detection result.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    @lombok.EqualsAndHashCode(callSuper = false)
    public static class DetectionResult {
        private List<DetailedResult> categoriesAnalysis; // The detailed result for detection.
    }

    /**
     * Represents an image detection result.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    @lombok.EqualsAndHashCode(callSuper = false)
    public static class ImageDetectionResult extends DetectionResult {
    }

    /**
     * Represents a detailed detection result for a blocklist match.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class BlocklistDetailedResult {
        private String blocklistName; // The name of the blocklist.
        private String blocklistItemId; // The ID of the block item that matched.
        private String blocklistItemText; // The text of the block item that matched.
    }

    /**
     * Represents a text detection result.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    @lombok.EqualsAndHashCode(callSuper = false)
    public static class TextDetectionResult extends DetectionResult {
        // The list of detailed results for blocklist matches.
        private List<BlocklistDetailedResult> blocklistsMatch;
    }

    /**
     * Represents a detection error response.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class DetectionErrorResponse {
        private DetectionError error; // The detection error.
    }

    /**
     * Represents a detection error.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class DetectionError {
        private String code; // The error code.
        private String message; // The error message.
        private String target; // The error target.
        private String[] details; // The error details.
        private DetectionInnerError innererror; // The inner error.
    }

    /**
     * Represents a detection inner error.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class DetectionInnerError {
        private String code; // The inner error code.
        private String innererror; // The inner error message.
    }

    /**
     * Builds the URL for the Content Safety API based on the media type.
     *
     * @param mediaType The media type.
     * @return The URL for the Content Safety API.
     */
    public String buildUrl(MediaType mediaType) {
        return switch (mediaType) {
            case Text ->
                String.format("%s/contentsafety/text:analyze?api-version=%s", this.endpoint, API_VERSION);
            case Image ->
                String.format("%s/contentsafety/image:analyze?api-version=%s", this.endpoint, API_VERSION);
            default -> throw new IllegalArgumentException(String.format("Invalid Media Type %s", mediaType));
        };
    }

    /**
     * Builds the request body for the Content Safety API.
     *
     * @param mediaType  The media type.
     * @param content    The content to be analyzed.
     * @param blocklists The blocklists to be used for analysis.
     * @return The request body for the Content Safety API.
     */
    public DetectionRequest buildRequestBody(MediaType mediaType, String content, String[] blocklists) {
        return switch (mediaType) {
            case Text -> new TextDetectionRequest(content, blocklists);
            case Image -> new ImageDetectionRequest(content);
            default -> throw new IllegalArgumentException(String.format("Invalid Media Type %s", mediaType));
        };
    }

    /**
     * Deserializes the detection result from JSON based on the media type.
     *
     * @param json      The JSON string to be deserialized.
     * @param mediaType The media type.
     * @return The detection result object.
     * @throws JsonProcessingException If an error occurs during deserialization.
     */
    public DetectionResult deserializeDetectionResult(String json, MediaType mediaType) throws JsonProcessingException {
        return switch (mediaType) {
            case Text -> MAPPER.readValue(json, TextDetectionResult.class);
            case Image -> MAPPER.readValue(json, ImageDetectionResult.class);
            default -> throw new IllegalArgumentException(String.format("Invalid Media Type %s", mediaType));
        };
    }

    /**
     * Detects unsafe content using the Content Safety API.
     *
     * @param mediaType  The media type of the content to detect.
     * @param content    The content to detect.
     * @param blocklists The blocklists to use for text detection.
     * @return The response from the Content Safety API.
     * @throws IOException        if an error occurs while reading the response from
     *                            the API
     * @throws DetectionException if the API response cannot be correctly
     *                            deserialized
     */
    public DetectionResult detect(MediaType mediaType, String content,
            String[] blocklists) throws IOException, DetectionException {
        String url = buildUrl(mediaType);
        logger.debug("Building detection request - MediaType: {}, URL: {}", mediaType, url);

        Headers.Builder headersBuilder = new Headers.Builder();
        headersBuilder.add("Ocp-Apim-Subscription-Key", this.subscriptionKey);
        DetectionRequest requestBody = buildRequestBody(mediaType, content, blocklists);
        String payload = MAPPER.writeValueAsString(requestBody);

        logger.debug("Sending request to Azure Content Safety API - Payload size: {} chars", payload.length());

        Request request = new Request.Builder()
                .url(url)
                .headers(headersBuilder.build())
                .post(RequestBody.create(payload, JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            logger.debug("Received response from Azure Content Safety API - Status code: {}", response.code());

            if (response.body() == null) {
                logger.error("Azure Content Safety API response body is null - Status code: {}", response.code());
                throw new DetectionException(String.valueOf(response.code()), "Response body is null.");
            }

            String responseText = response.body().string();
            logger.debug("Azure Content Safety API response text: {}", responseText);

            if (!response.isSuccessful()) {
                logger.error("Azure Content Safety API returned error - Status code: {}, Response: {}", response.code(),
                        responseText);
                DetectionErrorResponse error = MAPPER.readValue(responseText, DetectionErrorResponse.class);
                if (error == null || error.getError() == null ||
                        error.getError().getCode() == null || error.getError().getMessage() == null) {
                    logger.error("Failed to parse error response - Response text: {}", responseText);
                    throw new DetectionException(String.valueOf(response.code()),
                            String.format("Error is null. Response text is %s", responseText));
                }
                logger.error("Azure Content Safety API error - Code: {}, Message: {}",
                        error.getError().getCode(), error.getError().getMessage());
                throw new DetectionException(error.getError().getCode(), error.getError().getMessage());
            }

            DetectionResult result = deserializeDetectionResult(responseText, mediaType);
            if (result == null) {
                logger.error("Failed to deserialize detection result - Response text: {}", responseText);
                throw new DetectionException(String.valueOf(response.code()),
                        String.format("HttpResponse is null. Response text is %s", responseText));
            }

            logger.debug("Successfully deserialized detection result");
            return result;
        }
    }

    /**
     * Gets the severity score of the specified category from the given detection
     * result.
     *
     * @param category        The category to get the severity score for.
     * @param detectionResult The detection result object to retrieve the severity
     *                        score from.
     * @return The severity score of the specified category.
     */
    public Integer getDetectionResultByCategory(Category category, DetectionResult detectionResult) {
        if (detectionResult == null || detectionResult.categoriesAnalysis == null) {
            logger.error("DetectionResult or categoriesAnalysis is null - cannot get severity for category: {}",
                    category);
            throw new IllegalArgumentException("DetectionResult or categoriesAnalysis is null");
        }

        for (DetailedResult result : detectionResult.categoriesAnalysis) {
            if (category.equals(result.getCategory())) {
                logger.debug("Found severity for category {}: {}", category, result.getSeverity());
                return result.getSeverity();
            }
        }
        String availableCategories = detectionResult.categoriesAnalysis.stream()
                .map(r -> r.getCategory().toString())
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");
        logger.error("Category {} not found in detection result. Available categories: {}",
                category, availableCategories);
        throw new IllegalArgumentException(String.format("Invalid Category %s", category));
    }

    /**
     * Makes a decision based on the detection result and the specified reject
     * thresholds.
     * Users can customize their decision-making method.
     *
     * @param detectionResult  The detection result object to make the decision on.
     * @param rejectThresholds The reject thresholds for each category.
     * @return The decision made based on the detection result and the specified
     *         reject thresholds.
     */
    public Decision makeDecision(DetectionResult detectionResult, Map<Category, Integer> rejectThresholds) {
        logger.debug("Making moderation decision - Evaluating {} categories", rejectThresholds.size());

        Map<Category, Action> actionResult = new HashMap<>();
        Action finalAction = Action.Accept;

        for (Category category : rejectThresholds.keySet()) {
            Integer threshold = rejectThresholds.get(category);
            if (threshold == null || !VALID_THRESHOLD_VALUES.contains(threshold)) {
                logger.error("Invalid reject threshold for category {}: {} (valid values: {})",
                        category, threshold, VALID_THRESHOLD_VALUES);
                throw new IllegalArgumentException("RejectThreshold can only be in (-1, 0, 2, 4, 6)");
            }

            Integer severity = getDetectionResultByCategory(category, detectionResult);
            if (severity == null) {
                logger.error("Severity is null for category: {}", category);
                throw new IllegalArgumentException(String.format("Can not find detection result for %s", category));
            }

            Action action;
            if (threshold != -1 && severity >= threshold) {
                action = Action.Reject;
            } else {
                action = Action.Accept;
            }
            actionResult.put(category, action);

            // Log Sexual category decision prominently
            // Reduced logging: no per-category info-level logs

            if (action.compareTo(finalAction) > 0) {
                finalAction = action;
                logger.debug("Final action updated to: {} (due to category: {})", finalAction, category);
            }
        }

        if (detectionResult instanceof TextDetectionResult textDetectionResult) {
            if (textDetectionResult.getBlocklistsMatch() != null
                    && textDetectionResult.getBlocklistsMatch().size() > 0) {
                logger.debug("Text blocklist match detected - rejecting content");
                finalAction = Action.Reject;
            }
        }

        logger.info("Moderation {}", finalAction == Action.Accept ? "ACCEPTED" : "REJECTED");
        return new Decision(finalAction, actionResult);
    }
}
