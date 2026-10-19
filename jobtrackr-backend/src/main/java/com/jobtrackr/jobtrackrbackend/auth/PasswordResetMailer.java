package com.jobtrackr.jobtrackrbackend.auth;

import com.jobtrackr.jobtrackrbackend.common.EmailGreeting;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;

// Builds and sends the "reset your password" email, with a plain-text and an HTML version.
// Locally JavaMailSender talks to Mailpit (spring.mail.* in application.yml); tests mock this class.
@Component
@RequiredArgsConstructor
public class PasswordResetMailer {

    static final String SUBJECT = "Reset your JobTrackr password";

    private final JavaMailSender mailSender;
    private final PasswordResetProperties properties;

    public void send(PasswordResetRequested request) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(properties.from());
        helper.setTo(request.email());
        helper.setSubject(SUBJECT);
        helper.setText(plainText(request), html(request));
        mailSender.send(message);
    }

    static String plainText(PasswordResetRequested request) {
        return """
                %s

                Someone asked to reset the password for your JobTrackr account. To choose a new password, open:

                %s

                The link works once and expires in %d minutes. If you didn't ask for this, ignore this email:
                your password stays the same.
                """.formatted(greeting(request), request.resetLink(), request.validFor().toMinutes());
    }

    // The name is user-typed, so it is HTML-escaped; the link is ours (config URL + URL-safe token)
    static String html(PasswordResetRequested request) {
        return """
                <p>%s</p>
                <p>Someone asked to reset the password for your JobTrackr account.</p>
                <p><a href="%s">Choose a new password</a></p>
                <p>The link works once and expires in %d minutes. If you didn't ask for this, ignore this email:
                your password stays the same.</p>
                """.formatted(HtmlUtils.htmlEscape(greeting(request)), request.resetLink(), request.validFor().toMinutes());
    }

    private static String greeting(PasswordResetRequested request) {
        return EmailGreeting.of(request.recipientName(), request.email());
    }
}
