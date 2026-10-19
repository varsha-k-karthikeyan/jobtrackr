package com.jobtrackr.jobtrackrbackend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// Spring Data generates the implementation at startup and turns method names into queries:
// findByEmail -> "where email = ?". Emails are stored lower-case, so pass an already normalised value.
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // ---- Morning reminder digest (notification/ReminderEmailService); a scheduled job, so all users ----

    // Users with reminder emails on whose digest for `today` has not been handled yet; excludedEmail is the
    // shared demo account
    @Query("""
            select u from User u
             where (u.digestSentOn is null or u.digestSentOn < :today)
               and u.emailRemindersEnabled = true
               and u.email <> :excludedEmail
             order by u.id
            """)
    List<User> findDueForDigest(@Param("today") LocalDate today, @Param("excludedEmail") String excludedEmail);

    @Transactional
    @Modifying
    @Query("update User u set u.digestSentOn = :today where u.id = :userId")
    int markDigestSent(@Param("userId") Long userId, @Param("today") LocalDate today);
}
