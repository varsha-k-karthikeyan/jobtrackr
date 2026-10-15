package com.jobtrackr.jobtrackrbackend.common.exception;

// A validation failure only the service can detect (it needs the database), e.g. "current password is
// incorrect". GlobalExceptionHandler turns it into the same 400 + "errors" map as Bean Validation, so the
// client shows it under the right form field with no special handling.
public class FieldValidationException extends RuntimeException {

    private final String field;

    public FieldValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
