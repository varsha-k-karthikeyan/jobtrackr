package com.jobtrackr.jobtrackrbackend.application;

import com.jobtrackr.jobtrackrbackend.application.dto.ApplicationListFilter;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

// Small, composable WHERE-clause fragments. Optional filters return an always-true predicate when absent.
public final class ApplicationSpecifications {

    private ApplicationSpecifications() {
    }

    public static Specification<Application> ownedBy(Long userId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Application> hasStatus(ApplicationStatus status) {
        return (root, query, criteriaBuilder) -> status == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.equal(root.get("status"), status);
    }

    // Text columns searched with a case-insensitive "contains": every text field the list view shows
    private static final List<String> SEARCHABLE_TEXT_FIELDS = List.of("company", "role", "location", "source");

    // Case-insensitive "contains" across the searchable text fields, plus the status: "offer" or "inter"
    // also finds applications in that column. The status is matched in Java against the enum names and
    // turned into "status IN (...)", which keeps the SQL simple and type-safe (no casting the enum column).
    public static Specification<Application> matchesSearchText(String searchText) {
        return (root, query, criteriaBuilder) -> {
            if (searchText == null || searchText.isBlank()) {
                return criteriaBuilder.conjunction();
            }
            String normalized = searchText.trim().toLowerCase(Locale.ROOT);
            String pattern = "%" + escapeLike(normalized) + "%";

            List<Predicate> anyMatch = new ArrayList<>();
            for (String field : SEARCHABLE_TEXT_FIELDS) {
                anyMatch.add(criteriaBuilder.like(criteriaBuilder.lower(root.get(field)), pattern, '\\'));
            }
            List<ApplicationStatus> matchingStatuses = Arrays.stream(ApplicationStatus.values())
                    .filter(status -> status.name().toLowerCase(Locale.ROOT).contains(normalized))
                    .toList();
            if (!matchingStatuses.isEmpty()) {
                anyMatch.add(root.get("status").in(matchingStatuses));
            }
            return criteriaBuilder.or(anyMatch.toArray(Predicate[]::new));
        };
    }

    // The single entry point the service uses, so the owner filter can never be forgotten
    public static Specification<Application> filter(Long userId, ApplicationListFilter filter) {
        return Specification.allOf(
                ownedBy(userId),
                hasStatus(filter.status()),
                matchesSearchText(filter.searchText()),
                salaryOverlaps(filter.salaryFrom(), filter.salaryTo()));
    }

    // The job's range [min, max] overlaps the requested [from, to]  <=>  max >= from AND min <= to.
    // COALESCE lets a job with only one end act as a single amount; with both ends NULL the comparison is
    // NULL, so a job without any salary drops out as soon as either bound is given.
    public static Specification<Application> salaryOverlaps(BigDecimal from, BigDecimal to) {
        return (root, query, criteriaBuilder) -> {
            Expression<BigDecimal> min = root.get("salaryMinLpa");
            Expression<BigDecimal> max = root.get("salaryMaxLpa");
            List<Predicate> bounds = new ArrayList<>();
            if (from != null) {
                bounds.add(criteriaBuilder.greaterThanOrEqualTo(criteriaBuilder.coalesce(max, min), from));
            }
            if (to != null) {
                bounds.add(criteriaBuilder.lessThanOrEqualTo(criteriaBuilder.coalesce(min, max), to));
            }
            return bounds.isEmpty() ? criteriaBuilder.conjunction() : criteriaBuilder.and(bounds.toArray(Predicate[]::new));
        };
    }

    // Treat % and _ typed by the user as literal characters, not LIKE wildcards
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
