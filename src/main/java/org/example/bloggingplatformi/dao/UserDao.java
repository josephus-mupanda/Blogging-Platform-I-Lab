package org.example.bloggingplatformi.dao;

import java.util.List;
import java.util.Optional;

import org.example.bloggingplatformi.model.User;

/**
 * Reads and writes rows in the users table.
 * Lookups return Optional because "no such user" is a normal result, not an error.
 */
public interface UserDao {

    Optional<User> findById(long userId);

    Optional<User> findByUsername(String username);

    List<User> findAll(int limit, int offset);

    /** Users who have written at least one post. Used by the author filter. */
    List<User> findAuthorsWithPosts();

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /** @return the generated {@code user_id} */
    long insert(User user);

    /** @return true if a row was changed */
    boolean update(User user);

    /**
     * Deletes the user. Their posts are deleted too (cascade).
     * Their comments stay, with the author set to null.
     */
    boolean deleteById(long userId);

    long count();
}
