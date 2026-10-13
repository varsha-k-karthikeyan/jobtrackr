package com.jobtrackr.jobtrackrbackend.application;

import com.jobtrackr.jobtrackrbackend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

// One job application = one card on the Kanban board. Maps the "applications" table.
// A card's place on the board is (user, status, position); ApplicationService.move keeps positions
// gap-free. Deleting a user deletes their applications in the database (ON DELETE CASCADE).
@Entity
@Table(name = "applications")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // LAZY: loading an application must not drag the user row along with it
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false, length = 150)
    private String company;

    @Column(nullable = false, length = 150)
    private String role;

    // STRING stores 'APPLIED' etc.; the default ORDINAL would store 0,1,2 and break if the enum is reordered
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status;

    // Order of the card inside its status column on the board (0 = top)
    @Column(nullable = false)
    private int position;

    @Column(name = "jd_url", columnDefinition = "text")
    private String jdUrl;

    @Column(name = "jd_text", columnDefinition = "text")
    private String jdText;

    @Column(length = 100)
    private String location;

    @Column(name = "salary_min_lpa", precision = 5, scale = 2)
    private BigDecimal salaryMinLpa;

    @Column(name = "salary_max_lpa", precision = 5, scale = 2)
    private BigDecimal salaryMaxLpa;

    @Column(length = 50)
    private String source;

    @Column(name = "applied_on")
    private LocalDate appliedOn;

    @Column(name = "follow_up_on")
    private LocalDate followUpOn;

    // Optimistic locking: Hibernate adds "WHERE version = ?" to every UPDATE and increments it
    @Version
    @Column(nullable = false)
    private Integer version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
