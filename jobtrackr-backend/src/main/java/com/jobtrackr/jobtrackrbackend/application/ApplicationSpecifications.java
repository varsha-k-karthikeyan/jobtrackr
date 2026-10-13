package com.jobtrackr.jobtrackrbackend.application;

import org.springframework.data.jpa.domain.Specification;

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

    // Case-insensitive "contains" on company or role
    public static Specification<Application> matchesSearchText(String searchText) {
        return (root, query, criteriaBuilder) -> {
            if (searchText == null || searchText.isBlank()) {
                return criteriaBuilder.conjunction();
            }
            String pattern = "%" + escapeLike(searchText.trim().toLowerCase()) + "%";
            return criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("company")), pattern, '\\'),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("role")), pattern, '\\'));
        };
    }

    // The single entry point the service uses, so the owner filter can never be forgotten
    public static Specification<Application> filter(Long userId, ApplicationStatus status, String searchText) {
        return Specification.allOf(ownedBy(userId), hasStatus(status), matchesSearchText(searchText));
    }

    // Treat % and _ typed by the user as literal characters, not LIKE wildcards
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
