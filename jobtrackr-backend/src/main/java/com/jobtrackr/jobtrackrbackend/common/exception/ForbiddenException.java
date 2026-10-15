package com.jobtrackr.jobtrackrbackend.common.exception;

// The caller is signed in but this action is not allowed for them (e.g. changing the public demo account's
// password); GlobalExceptionHandler turns it into a 403
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
