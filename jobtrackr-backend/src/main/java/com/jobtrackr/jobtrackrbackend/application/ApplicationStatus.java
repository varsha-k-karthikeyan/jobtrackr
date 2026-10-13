package com.jobtrackr.jobtrackrbackend.application;

// The four board columns. Declaration order is the left-to-right column order (getBoard uses an EnumMap).
// Must stay in sync with the CHECK constraint on applications.status (V1__init.sql)
public enum ApplicationStatus {
    APPLIED,
    INTERVIEW,
    OFFER,
    REJECTED
}
