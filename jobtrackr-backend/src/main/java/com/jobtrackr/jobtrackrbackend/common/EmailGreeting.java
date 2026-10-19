package com.jobtrackr.jobtrackrbackend.common;

// The first line of every email: "Hi Varsha," or, without a name (it is optional at registration), the part of
// the address before the @: "Hi varsha.k.karthikeyan,". Plain text; the HTML versions escape it.
public final class EmailGreeting {

    private EmailGreeting() {
    }

    public static String of(String recipientName, String email) {
        String name = recipientName == null || recipientName.isBlank() ? email.substring(0, email.indexOf('@')) : recipientName;
        return "Hi " + name + ",";
    }
}
