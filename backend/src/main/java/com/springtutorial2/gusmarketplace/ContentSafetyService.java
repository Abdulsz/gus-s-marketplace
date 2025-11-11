package com.springtutorial2.gusmarketplace;

import org.springframework.stereotype.Service;

/**
 * Service for content moderation (text and images).
 * This is a stub implementation that allows all content.
 * In production, this should integrate with a real content moderation service.
 */
@Service
public class ContentSafetyService {

    /**
     * Moderates text content.
     * 
     * @param text The text to moderate
     * @return true if content is appropriate, false otherwise
     */
    public boolean moderateText(String text) {
        // Stub implementation - allows all text
        // In production, integrate with Azure Content Safety, AWS Rekognition, or similar
        return true;
    }

    /**
     * Moderates image content from byte array.
     * 
     * @param imageBytes The image bytes to moderate
     * @return true if content is appropriate, false otherwise
     */
    public boolean moderateImageFromBytes(byte[] imageBytes) {
        // Stub implementation - allows all images
        // In production, integrate with Azure Content Safety, AWS Rekognition, or similar
        return true;
    }

    /**
     * Exception thrown when content moderation fails.
     */
    public static class ContentModerationException extends Exception {
        public ContentModerationException(String message) {
            super(message);
        }
    }
}
