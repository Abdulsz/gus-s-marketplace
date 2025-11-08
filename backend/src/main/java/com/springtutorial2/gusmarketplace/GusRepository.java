package com.springtutorial2.gusmarketplace;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface GusRepository extends MongoRepository<Listing, String> {
    // This interface extends MongoRepository to provide CRUD operations for Listing entities.
    // It uses String as the type of the ID field.
    // Additional query methods can be defined here if needed.
    // For example, you could add methods to find listings by userName, category, etc.

    List<Listing> findListingByCategory(String category);
    List<Listing> findListingByTitle(String title);
    List<Listing> findByUserId(String userId);

}
