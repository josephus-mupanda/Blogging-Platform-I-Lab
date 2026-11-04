package org.example.bloggingplatformi.dao;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.types.ObjectId;

import org.example.bloggingplatformi.db.Mongo;
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
        for (Document document : comments().find(Filters.eq("postId", postId)).sort(Sorts.ascending("createdAt"))) {
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
        comments().updateOne(Filters.eq("_id", new ObjectId(commentId)), Updates.set("body", body));
    }

    public void delete(String commentId) {
        comments().deleteOne(Filters.eq("_id", new ObjectId(commentId)));
    }

    /**
     * Deletes all comments of a post. Needed when a post is deleted, because
     * PostgreSQL's ON DELETE CASCADE cannot reach MongoDB.
     */
    public void deleteByPost(long postId) {
        comments().deleteMany(Filters.eq("postId", postId));
    }

    public long count() {
        return comments().countDocuments();
    }
}
