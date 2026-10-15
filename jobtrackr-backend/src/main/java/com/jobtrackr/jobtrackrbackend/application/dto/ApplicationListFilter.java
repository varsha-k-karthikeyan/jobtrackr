package com.jobtrackr.jobtrackrbackend.application.dto;

import com.jobtrackr.jobtrackrbackend.application.ApplicationStatus;

import java.math.BigDecimal;

// The query-string filters of GET /applications, bound by Spring MVC straight into this record
// (e.g. ?status=OFFER&searchText=pune&salaryFrom=20&salaryTo=30). Every field is optional.
//
// salaryFrom / salaryTo (lakhs per annum) select the jobs whose salary range OVERLAPS [salaryFrom, salaryTo]:
// a job paying 25-35 matches 20-30. A job with only a minimum or only a maximum is treated as that single
// amount; a job with no salary at all matches only when no salary filter is given.
public record ApplicationListFilter(
        ApplicationStatus status,
        String searchText,
        BigDecimal salaryFrom,
        BigDecimal salaryTo
) {
}
