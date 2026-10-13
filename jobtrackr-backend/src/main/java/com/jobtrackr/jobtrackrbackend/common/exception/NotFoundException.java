package com.jobtrackr.jobtrackrbackend.common.exception;

// Thrown by services; GlobalExceptionHandler turns it into a 404
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
