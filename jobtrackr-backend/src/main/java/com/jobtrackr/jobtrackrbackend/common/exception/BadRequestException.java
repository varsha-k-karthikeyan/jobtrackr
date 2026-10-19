package com.jobtrackr.jobtrackrbackend.common.exception;

// A request that is well-formed but cannot be carried out as sent, and that is not about one form field
// (e.g. an expired password-reset link); GlobalExceptionHandler turns it into a 400 with this detail
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
