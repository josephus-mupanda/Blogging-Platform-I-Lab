package org.example.bloggingplatformi.dao;

import java.util.List;
import java.util.Optional;

import org.example.bloggingplatformi.model.User;

/**
 * Data access for {@code users}.
 *
 * <p>Lookups return {@link Optional} rather than null or an exception: not
 * finding a user is an ordinary outcome of a search, and the caller is forced
 * to say what it means in context.
 */
public interface UserDao {

    Optional<User> findById(long userId);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    List<User> findAll(int limit, int offset);

    /** Authors with at least one post, for the author filter dropdown. */
    List<User> findAuthorsWithPosts();

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /** @return the generated {@code user_id} */
    long insert(User user);

    /** @return true if a row was changed */
    boolean update(User user);

    /**
     * Deletes the user. The posts they authored cascade away with them, while
     * their comments survive with a null author, per the FK rules in the schema.
     *
     * @return true if a row was deleted
     */
    boolean deleteById(long userId);

    long count();
}
