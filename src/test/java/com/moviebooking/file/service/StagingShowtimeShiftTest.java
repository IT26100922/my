package com.moviebooking.file.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The showtime rows in the MySQL import table have fixed dates, so on an old database they can all be
 * in the past. The import moves them forward by whole days, the same amount for every row, so the
 * timetable keeps its shape.
 */
class StagingShowtimeShiftTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 9, 0);

    @Test
    void noShiftWhenTheEarliestShowIsStillAhead() {
        List<LocalDateTime> starts = List.of(
                LocalDateTime.of(2026, 10, 13, 15, 0),
                LocalDateTime.of(2026, 10, 14, 11, 0));
        assertThat(FileService.dayShiftToFuture(starts, NOW)).isZero();
    }

    @Test
    void shiftsByWholeDaysUntilTheEarliestShowIsInTheFuture() {
        // Earliest staged show is 2026-10-13 11:00, which is already past at 12:00 on the same day.
        LocalDateTime later = LocalDateTime.of(2026, 10, 13, 12, 0);
        List<LocalDateTime> starts = List.of(
                LocalDateTime.of(2026, 10, 13, 11, 0),
                LocalDateTime.of(2026, 10, 14, 19, 0));
        long shift = FileService.dayShiftToFuture(starts, later);
        assertThat(shift).isEqualTo(1);
        // After the shift every show starts after 'later', and the gap between shows is unchanged.
        assertThat(LocalDateTime.of(2026, 10, 13, 11, 0).plusDays(shift)).isAfter(later);
    }

    @Test
    void shiftCoversSeveralWeeksOfOldStagedDates() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 2, 8, 0);
        List<LocalDateTime> starts = List.of(LocalDateTime.of(2026, 10, 13, 11, 0));
        long shift = FileService.dayShiftToFuture(starts, now);
        assertThat(LocalDateTime.of(2026, 10, 13, 11, 0).plusDays(shift)).isAfter(now);
        // Smallest possible move: one day less would still be in the past.
        assertThat(LocalDateTime.of(2026, 10, 13, 11, 0).plusDays(shift - 1)).isBefore(now);
    }

    @Test
    void ignoresNullStartTimes() {
        List<LocalDateTime> starts = java.util.Arrays.asList(
                null, LocalDateTime.of(2026, 10, 1, 11, 0), null);
        long shift = FileService.dayShiftToFuture(starts, NOW);
        assertThat(shift).isPositive();
        assertThat(LocalDateTime.of(2026, 10, 1, 11, 0).plusDays(shift)).isAfter(NOW);
    }

    @Test
    void emptyStagingTableNeedsNoShift() {
        assertThat(FileService.dayShiftToFuture(List.of(), NOW)).isZero();
    }
}
