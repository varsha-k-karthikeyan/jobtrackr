package com.jobtrackr.jobtrackrbackend.stats;

import com.jobtrackr.jobtrackrbackend.common.CurrentUserProvider;
import com.jobtrackr.jobtrackrbackend.stats.dto.StatsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.jobtrackr.jobtrackrbackend.application.ApplicationStatus.APPLIED;
import static com.jobtrackr.jobtrackrbackend.application.ApplicationStatus.INTERVIEW;
import static com.jobtrackr.jobtrackrbackend.application.ApplicationStatus.OFFER;
import static com.jobtrackr.jobtrackrbackend.application.ApplicationStatus.REJECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private StatsRepository statsRepository;
    @Mock
    private CurrentUserProvider currentUser;
    @InjectMocks
    private StatsService service;

    @BeforeEach
    void setUp() {
        when(currentUser.getUserId()).thenReturn(USER_ID);
    }

    @Test
    void getStats_mapsEachStatusAndComputesResponseRate() {
        when(statsRepository.countByStatus(USER_ID)).thenReturn(List.of(
                new StatusCount(APPLIED, 6L),
                new StatusCount(INTERVIEW, 4L),
                new StatusCount(OFFER, 2L),
                new StatusCount(REJECTED, 4L)));

        StatsResponse stats = service.getStats();

        assertThat(stats.total()).isEqualTo(16);
        assertThat(stats.applied()).isEqualTo(6);
        assertThat(stats.interview()).isEqualTo(4);
        assertThat(stats.offer()).isEqualTo(2);
        assertThat(stats.rejected()).isEqualTo(4);
        // (4 + 2 + 4) / 16
        assertThat(stats.responseRate()).isCloseTo(0.625, within(1e-9));
    }

    @Test
    void getStats_statusMissingFromQueryResult_countsAsZero() {
        when(statsRepository.countByStatus(USER_ID)).thenReturn(List.of(
                new StatusCount(APPLIED, 3L),
                new StatusCount(OFFER, 1L)));

        StatsResponse stats = service.getStats();

        assertThat(stats.total()).isEqualTo(4);
        assertThat(stats.interview()).isZero();
        assertThat(stats.rejected()).isZero();
        assertThat(stats.responseRate()).isCloseTo(0.25, within(1e-9));
    }

    @Test
    void getStats_noApplications_returnsZerosWithoutDividingByZero() {
        when(statsRepository.countByStatus(USER_ID)).thenReturn(List.of());

        StatsResponse stats = service.getStats();

        assertThat(stats.total()).isZero();
        assertThat(stats.responseRate()).isZero();
    }

    @Test
    void getStats_onlyAppliedCards_responseRateIsZero() {
        when(statsRepository.countByStatus(USER_ID)).thenReturn(List.of(new StatusCount(APPLIED, 5L)));

        assertThat(service.getStats().responseRate()).isZero();
    }
}
