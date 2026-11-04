package org.example.bloggingplatformi.service;

import java.util.List;

import org.example.bloggingplatformi.dao.UserDao;
import org.example.bloggingplatformi.exception.ValidationException;
import org.example.bloggingplatformi.model.Role;
import org.example.bloggingplatformi.model.User;
import org.example.bloggingplatformi.util.Passwords;

/** Sign up, sign in, and the list of accounts. */
public class UserService {

    private final UserDao userDao = new UserDao();

    /**
     * Sign up. Checks the input, then saves the account with the hash of the password.
     * Nobody can sign up as ADMIN: the admin account comes from db/postgres/seed.sql.
     */
    public void register(String username, String email, String password, String displayName, Role role) {
        if (displayName == null || displayName.isBlank()) {
            throw new ValidationException("Please enter your name.");
        }
        if (username == null || username.strip().length() < 3) {
            throw new ValidationException("The username needs at least 3 characters.");
        }
        if (email == null || !email.contains("@")) {
            throw new ValidationException("Please enter a valid email address.");
        }
        if (password == null || password.length() < 6) {
            throw new ValidationException("The password needs at least 6 characters.");
        }
        if (role == Role.ADMIN) {
            throw new ValidationException("You can only sign up as a blogger or a reader.");
        }
        if (userDao.usernameExists(username.strip())) {
            throw new ValidationException("This username is already taken.");
        }
        if (userDao.emailExists(email.strip())) {
            throw new ValidationException("This email is already registered.");
        }
        userDao.insert(username.strip(), email.strip(), Passwords.hash(password), displayName.strip(), role);
    }

    /**
     * Sign in. Returns the user if the username and password are right.
     * The message is the same for a wrong username and a wrong password, so a
     * stranger cannot find out which usernames exist.
     */
    public User login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new ValidationException("Please enter your username and password.");
        }
        User user = userDao.findByUsername(username.strip());
        if (user == null || !Passwords.matches(password, user.passwordHash())) {
            throw new ValidationException("Wrong username or password.");
        }
        return user;
    }

    public List<User> findAll() {
        return userDao.findAll();
    }

    public List<User> findAuthors() {
        return userDao.findAuthors();
    }

    public int count() {
        return userDao.count();
    }
}
