package org.example.bloggingplatformi.service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.example.bloggingplatformi.dao.CommentDao;
import org.example.bloggingplatformi.dao.DaoFactory;
import org.example.bloggingplatformi.dao.UserDao;
import org.example.bloggingplatformi.exception.EntityNotFoundException;
import org.example.bloggingplatformi.exception.ValidationException;
import org.example.bloggingplatformi.model.Role;
import org.example.bloggingplatformi.model.User;

/**
 * Business rules for user accounts: validation, uniqueness checks and the
 * cross-store bookkeeping a rename implies.
 */
public class UserService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MIN_USERNAME = 3;
    private static final int MAX_USERNAME = 50;

    private final UserDao userDao;
    private final CommentDao commentDao;

    public UserService() {
        this(DaoFactory.users(), DaoFactory.comments());
    }

    public UserService(UserDao userDao, CommentDao commentDao) {
        this.userDao = userDao;
        this.commentDao = commentDao;
    }

    public Optional<User> findById(long userId) {
        return userDao.findById(userId);
    }

    public Optional<User> findByUsername(String username) {
        return userDao.findByUsername(username);
    }

    public User require(long userId) {
        return userDao.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User", userId));
    }

    public List<User> findAll(int limit, int offset) {
        return userDao.findAll(limit, offset);
    }

    public List<User> findAuthors() {
        return userDao.findAuthorsWithPosts();
    }

    public long count() {
        return userDao.count();
    }

    /**
     * Creates an account.
     *
     * <p>The uniqueness checks here exist to produce a readable message, not
     * to guarantee uniqueness. Between this check and the insert another
     * session can take the same username; {@code uq_users_username} is what
     * actually prevents the duplicate, and the DAO turns that violation into a
     * {@code DuplicateEntityException}.
     */
    public User create(String username, String email, String passwordHash,
                       String displayName, String bio, Role role) {
        ValidationException.Collector errors = new ValidationException.Collector();

        validateUsername(username, errors);
        validateEmail(email, errors);
        errors.rejectIf(passwordHash == null || passwordHash.isBlank(),
                "password", "A password is required.");
        errors.rejectIf(displayName == null || displayName.isBlank(),
                "displayName", "A display name is required.");
        errors.rejectIf(role == null, "role", "A role is required.");
        errors.throwIfInvalid();

        if (userDao.existsByUsername(username)) {
            errors.reject("username", "That username is already taken.");
        }
        if (userDao.existsByEmail(email)) {
            errors.reject("email", "That email address is already registered.");
        }
        errors.throwIfInvalid();

        User user = User.newUser(username.strip(), email.strip(), passwordHash,
                displayName.strip(), bio, role);
        long id = userDao.insert(user);
        return user.withId(id);
    }

    /**
     * Updates a profile.
     *
     * <p>A display-name change also rewrites the denormalized {@code authorName}
     * on every comment that user left. In the relational store that is a no-op,
     * because the name is reached by join; in MongoDB it is a real write across
     * the collection. Doing it here, rather than in the DAO, keeps the rule in
     * one place regardless of which store is active.
     */
    public User updateProfile(long userId, String displayName, String bio) {
        User existing = require(userId);

        ValidationException.Collector errors = new ValidationException.Collector();
        errors.rejectIf(displayName == null || displayName.isBlank(),
                "displayName", "A display name is required.");
        errors.throwIfInvalid();

        boolean nameChanged = !existing.displayName().equals(displayName.strip());

        User updated = existing.withProfile(displayName.strip(), bio);
        if (!userDao.update(updated)) {
            throw new EntityNotFoundException("User", userId);
        }
        if (nameChanged) {
            commentDao.renameAuthor(userId, displayName.strip());
        }
        return updated;
    }

    public User setActive(long userId, boolean active) {
        User updated = require(userId).withActive(active);
        if (!userDao.update(updated)) {
            throw new EntityNotFoundException("User", userId);
        }
        return updated;
    }

    /**
     * Deletes the account. Their posts cascade away with them; their comments
     * survive with a null author, per the schema's FK rules.
     */
    public boolean delete(long userId) {
        return userDao.deleteById(userId);
    }

    private void validateUsername(String username, ValidationException.Collector errors) {
        if (username == null || username.isBlank()) {
            errors.reject("username", "A username is required.");
            return;
        }
        String trimmed = username.strip();
        errors.rejectIf(trimmed.length() < MIN_USERNAME,
                "username", "Usernames must be at least " + MIN_USERNAME + " characters.");
        errors.rejectIf(trimmed.length() > MAX_USERNAME,
                "username", "Usernames must be at most " + MAX_USERNAME + " characters.");
    }

    private void validateEmail(String email, ValidationException.Collector errors) {
        if (email == null || email.isBlank()) {
            errors.reject("email", "An email address is required.");
            return;
        }
        // Mirrors ck_users_email so the message arrives before the constraint does.
        errors.rejectIf(!EMAIL.matcher(email.strip()).matches(),
                "email", "That does not look like an email address.");
    }
}
