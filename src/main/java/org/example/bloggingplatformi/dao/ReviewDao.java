package org.example.bloggingplatformi.dao;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

import org.example.bloggingplatformi.db.Mongo;
import org.example.bloggingplatformi.model.Review;

/**
 * Reads and writes the "reviews" collection in MongoDB.
 * A review is a document like:
 * { postId: 1, reviewerId: 3, reviewerName: "josephus", rating: 5, text: "Great", createdAt: ... }
 */
public class ReviewDao {

    private MongoCollection<Document> reviews() {
        return Mongo.database().getCollection("reviews");
    }

    /** The reviews of a post, newest first. */
    public List<Review> findByPost(long postId) {
        List<Review> result = new ArrayList<>();
        for (Document document : reviews().find(Filters.eq("postId", postId)).sort(Sorts.descending("createdAt"))) {
            result.add(new Review(
                    document.getObjectId("_id").toHexString(),
                    document.getLong("postId"),
                    document.getLong("reviewerId"),
                    document.getString("reviewerName"),
                    document.getInteger("rating"),
                    document.getString("text"),
                    document.getDate("createdAt").toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()));
        }
        return result;
    }

    /** True if this user already reviewed this post (only one review per user per post). */
    public boolean exists(long postId, long reviewerId) {
        return reviews().countDocuments(Filters.and(
                Filters.eq("postId", postId), Filters.eq("reviewerId", reviewerId))) > 0;
    }

    public void insert(long postId, long reviewerId, String reviewerName, int rating, String text) {
        reviews().insertOne(new Document("postId", postId)
                .append("reviewerId", reviewerId)
                .append("reviewerName", reviewerName)
                .append("rating", rating)
                .append("text", text)
                .append("createdAt", new Date()));
    }

    /** Deletes all reviews of a post (PostgreSQL's cascade cannot reach MongoDB). */
    public void deleteByPost(long postId) {
        reviews().deleteMany(Filters.eq("postId", postId));
    }

    public long count() {
        return reviews().countDocuments();
    }
}
