package com.jobtrackr.jobtrackrbackend.user;

// The public demo account created by V2/V4. Its password is published in the repository and used by the
// frontend's "Try the demo" button, so actions that would lock other people out of it are refused.
public final class DemoAccount {

    public static final String EMAIL = "demo@jobtrackr.dev";

    private DemoAccount() {
    }

    public static boolean isDemo(User user) {
        return EMAIL.equals(user.getEmail());
    }
}
