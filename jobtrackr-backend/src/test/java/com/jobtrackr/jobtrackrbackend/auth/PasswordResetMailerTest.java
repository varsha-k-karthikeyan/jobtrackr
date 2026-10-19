package com.jobtrackr.jobtrackrbackend.auth;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// The JavaMailSender is a mock: the message is built for real but never leaves the test (no SMTP server)
class PasswordResetMailerTest {

    private static final String LINK = "http://localhost:4200/reset-password?token=abc";

    @Test
    void sendsOneMessageWithTheLinkInPlainTextAndHtml() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        PasswordResetProperties properties = new PasswordResetProperties("http://localhost:4200", "JobTrackr <no-reply@test.dev>",
                Duration.ofMinutes(30), Duration.ofMinutes(1));

        new PasswordResetMailer(sender, properties)
                .send(new PasswordResetRequested(7L, "varsha@test.dev", "Varsha", LINK, Duration.ofMinutes(30)));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(sent.capture());
        MimeMessage message = sent.getValue();
        assertThat(message.getSubject()).isEqualTo(PasswordResetMailer.SUBJECT);
        assertThat(message.getAllRecipients()).extracting(Object::toString).containsExactly("varsha@test.dev");
        assertThat(message.getFrom()[0].toString()).isEqualTo("JobTrackr <no-reply@test.dev>");
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        message.writeTo(raw);
        assertThat(raw.toString(StandardCharsets.UTF_8)).contains("multipart/alternative", "text/plain", "text/html");
    }

    @Test
    void bodyHasTheLinkAndExpiry_andEscapesTheUserTypedName() {
        PasswordResetRequested request = new PasswordResetRequested(7L, "x@test.dev", "<b>Eve</b>", LINK, Duration.ofMinutes(30));

        assertThat(PasswordResetMailer.plainText(request)).contains(LINK, "expires in 30 minutes");
        assertThat(PasswordResetMailer.html(request))
                .contains("href=\"" + LINK + "\"")
                .contains("&lt;b&gt;Eve&lt;/b&gt;")
                .doesNotContain("<b>Eve</b>");
    }

    @Test
    void greetsGenericallyWithoutAName() {
        assertThat(PasswordResetMailer.plainText(new PasswordResetRequested(7L, "x@test.dev", null, LINK, Duration.ofMinutes(30))))
                .startsWith("Hi,");
    }
}
