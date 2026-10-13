package com.jobtrackr.jobtrackrbackend.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// Spring Data generates the implementation at startup and turns method names into queries:
// findByEmail -> "where email = ?". Emails are stored lower-case, so pass an already normalised value.
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
