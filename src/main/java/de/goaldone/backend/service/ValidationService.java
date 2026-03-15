package de.goaldone.backend.service;

import de.goaldone.backend.exception.ValidationException;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class ValidationService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public void requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldName, "must not be blank");
        }
    }

    public void requireNotNull(Object value, String fieldName) {
        if (value == null) {
            throw new ValidationException(fieldName, "must not be null");
        }
    }

    public void requirePositive(Integer value, String fieldName) {
        if (value == null || value < 1) {
            throw new ValidationException(fieldName, "must be a positive number");
        }
    }

    public void requireValidEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new ValidationException("email", "must be a valid email address");
        }
    }

    public void requireMinLength(String value, String fieldName, int min) {
        if (value == null || value.length() < min) {
            throw new ValidationException(fieldName, "must be at least " + min + " characters");
        }
    }

    public void requireMaxLength(String value, String fieldName, int max) {
        if (value != null && value.length() > max) {
            throw new ValidationException(fieldName, "must not exceed " + max + " characters");
        }
    }

    public void requireAfter(java.time.LocalTime after, java.time.LocalTime before, String fieldName) {
        if (after != null && before != null && !after.isAfter(before)) {
            throw new ValidationException(fieldName, "must be after " + before);
        }
    }

    public void requireNotBefore(java.time.LocalDate date, java.time.LocalDate before, String fieldName) {
        if (date != null && before != null && date.isBefore(before)) {
            throw new ValidationException(fieldName, "must not be before " + before);
        }
    }

    public void requireRange(Integer value, String fieldName, int min, int max) {
        if (value != null && (value < min || value > max)) {
            throw new ValidationException(fieldName, "must be between " + min + " and " + max);
        }
    }
}
