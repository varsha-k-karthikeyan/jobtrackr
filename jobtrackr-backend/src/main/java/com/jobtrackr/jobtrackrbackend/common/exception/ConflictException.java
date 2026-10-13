package com.jobtrackr.jobtrackrbackend.common.exception;

// Thrown by services when the request clashes with existing data; GlobalExceptionHandler turns it into a 409
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
