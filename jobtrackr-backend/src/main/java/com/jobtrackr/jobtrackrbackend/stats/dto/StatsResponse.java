package com.jobtrackr.jobtrackrbackend.stats.dto;

// GET /stats. The four status counts are the current column sizes; total is their sum.
// responseRate = (interview + offer + rejected) / total, between 0 and 1 (0 when total is 0):
// the share of applications that got any answer at all. The client formats it as a percentage.
public record StatsResponse(
        long total,
        long applied,
        long interview,
        long offer,
        long rejected,
        double responseRate
) {
}
