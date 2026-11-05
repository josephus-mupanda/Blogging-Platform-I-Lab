package org.example.bloggingplatformi.dao;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;
import org.bson.Document;
import org.bson.types.ObjectId;

import org.example.bloggingplatformi.db.Mongo;
import org.example.bloggingplatformi.exception.ValidationException;
import org.example.bloggingplatformi.model.ArchivedItem;
import org.example.bloggingplatformi.model.Comment;

/**
 * Reads and writes the "comments" collection in MongoDB.
 * A comment is a document like:
 * { postId: 1, authorId: 2, authorName: "Serge", body: "Nice post", createdAt: ... }
 */
public class CommentDao {

    private MongoCollection<Document> comments() {
        return Mongo.database().getCollection("comments");
    }

    /** The comments of a post, oldest first. */
    public List<Comment> findByPost(long postId) {
        List<Comment> result = new ArrayList<>();
        for (Document document : comments().find(Filters.and(Filters.eq("postId", postId), visible()))
                .sort(Sorts.ascending("createdAt"))) {
            result.add(new Comment(
                    document.getObjectId("_id").toHexString(),
                    document.getLong("postId"),
                    document.getLong("authorId"),
                    document.getString("authorName"),
                    document.getString("body"),
                    document.getDate("createdAt").toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()));
        }
        return result;
    }

    public void insert(long postId, long authorId, String authorName, String body) {
        comments().insertOne(new Document("postId", postId)
                .append("authorId", authorId)
                .append("authorName", authorName)
                .append("body", body)
                .append("createdAt", new Date()));
    }

    public void updateBody(String commentId, String body) {
        if (comments().updateOne(Filters.and(id(commentId), visible()), Updates.set("body", body)).getMatchedCount() == 0) {
            throw new ValidationException("This comment is no longer available.");
        }
    }

    public boolean archive(String commentId) {
        return comments().updateOne(Filters.and(id(commentId), visible()), Updates.currentDate("deletedAt"))
                .getMatchedCount() == 1;
    }

    public boolean restore(String commentId) {
        return comments().updateOne(Filters.and(id(commentId), Filters.ne("deletedAt", null)), Updates.unset("deletedAt"))
                .getMatchedCount() == 1;
    }

    public List<ArchivedItem> archived() {
        List<ArchivedItem> items = new ArrayList<>();
        for (Document document : comments().find(Filters.ne("deletedAt", null)).sort(Sorts.descending("deletedAt"))) {
            long postId = document.getLong("postId");
            items.add(new ArchivedItem(ArchivedItem.Kind.COMMENT, document.getObjectId("_id").toHexString(),
                    document.getString("authorName") + " · Post #" + postId, document.getString("body"),
                    document.getDate("deletedAt").toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime(), postId));
        }
        return items;
    }

    public long count(List<Long> archivedPostIds) {
        return comments().countDocuments(archivedPostIds.isEmpty() ? visible()
                : Filters.and(visible(), Filters.nin("postId", archivedPostIds)));
    }

    private Bson visible() {
        return Filters.eq("deletedAt", null); // also matches documents written before soft deletion
    }

    private Bson id(String commentId) {
        return Filters.eq("_id", new ObjectId(commentId));
    }
}
