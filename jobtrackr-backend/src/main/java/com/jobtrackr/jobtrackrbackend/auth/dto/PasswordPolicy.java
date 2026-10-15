package com.jobtrackr.jobtrackrbackend.auth.dto;

// The password rules for new accounts, in one place. The frontend's register form mirrors them
// (jobtrackr-frontend: shared/validators/password-strength.ts) for instant feedback, but this is the
// enforcement: a request that skips the UI is still rejected with a 400.
// Only applied at registration. Login deliberately has no rules (see LoginRequest).
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 20;

    // Three lookaheads, each requiring one character class somewhere in the string:
    // a lower-case letter, an upper-case letter, and a special character (anything that is not a
    // letter, a digit or whitespace, e.g. @ # ! _ -). The length is checked separately by @Size.
    public static final String COMPOSITION_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[^A-Za-z0-9\\s]).*$";

    public static final String COMPOSITION_MESSAGE =
            "must contain at least one uppercase letter, one lowercase letter and one special character";

    private PasswordPolicy() {
    }
}
