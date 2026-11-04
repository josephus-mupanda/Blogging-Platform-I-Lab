package org.example.bloggingplatformi.service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.example.bloggingplatformi.dao.DaoFactory;
import org.example.bloggingplatformi.dao.UserDao;
import org.example.bloggingplatformi.exception.EntityNotFoundException;
import org.example.bloggingplatformi.exception.ValidationException;
import org.example.bloggingplatformi.model.Role;
import org.example.bloggingplatformi.model.User;
import org.example.bloggingplatformi.util.Passwords;

/**
 * Business rules for user accounts: sign up, sign in, and the checks that
 * usernames and emails are valid and not already taken.
 */
public class UserService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MIN_USERNAME = 3;
    private static final int MAX_USERNAME = 50;
    private static final int MIN_PASSWORD = 6;

    private final UserDao userDao;

    public UserService() {
        this(DaoFactory.users());
    }

    public UserService(UserDao userDao) {
        this.userDao = userDao;
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
     * Creates an account. The checks here give the user a clear message.
     * The unique constraint in the database still blocks a duplicate that slips past them.
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
     * Sign up: creates a blogger (AUTHOR) or READER account with a hashed password.
     * Nobody can sign up as ADMIN; the admin account comes from the seed script.
     */
    public User register(String username, String email, String password,
                         String displayName, Role role) {
        ValidationException.Collector errors = new ValidationException.Collector();
        errors.rejectIf(password == null || password.length() < MIN_PASSWORD,
                "password", "Passwords must be at least " + MIN_PASSWORD + " characters.");
        errors.rejectIf(role == Role.ADMIN,
                "role", "You can only sign up as a blogger or a reader.");
        errors.throwIfInvalid();

        return create(username, email, Passwords.hash(password), displayName, null, role);
    }

    /**
     * Sign in: returns the user if the username and password are correct.
     * The same message is used for a wrong username and a wrong password,
     * so the screen does not tell a stranger which usernames exist.
     */
    public User login(String username, String password) {
        ValidationException.Collector errors = new ValidationException.Collector();
        errors.rejectIf(username == null || username.isBlank(), "username", "Enter your username.");
        errors.rejectIf(password == null || password.isEmpty(), "password", "Enter your password.");
        errors.throwIfInvalid();

        Optional<User> found = userDao.findByUsername(username.strip());
        if (found.isEmpty() || !Passwords.matches(password, found.get().passwordHash())) {
            errors.reject("password", "Wrong username or password.");
            errors.throwIfInvalid();
        }
        User user = found.get();
        if (!user.active()) {
            errors.reject("username", "This account has been deactivated by an admin.");
            errors.throwIfInvalid();
        }
        return user;
    }

    public User setActive(long userId, boolean active) {
        User updated = require(userId).withActive(active);
        if (!userDao.update(updated)) {
            throw new EntityNotFoundException("User", userId);
        }
        return updated;
    }

    /**
     * Deletes the account. The database also deletes the user's posts.
     * Their comments stay, with no author.
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
        // Same rule as the ck_users_email check in the database, but with a friendly message.
        errors.rejectIf(!EMAIL.matcher(email.strip()).matches(),
                "email", "That does not look like an email address.");
    }
}
