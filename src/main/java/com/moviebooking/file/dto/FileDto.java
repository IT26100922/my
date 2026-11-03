package com.moviebooking.file.dto;

import java.util.List;

/** Results of the MySQL import/export actions used by the admin Files & Reports page. */
public final class FileDto {

    private FileDto() {
    }

    /**
     * Outcome of one import.
     *
     * @param source   the MySQL staging table the rows came from
     * @param staged   how many rows the staging table holds
     * @param imported how many rows were created in the live table
     * @param skipped  how many were left alone because they already exist
     * @param errors   one line per staging row that could not be imported
     */
    public record ImportResult(String source, int staged, int imported, int skipped, List<String> errors) {
    }

    /**
     * Row counts for the three staging tables and their live counterparts.
     * The dashboard cards show these numbers, so it is visible at a glance that everything is in MySQL.
     */
    public record StagingStatus(String dataSource,
                                long stagedMovies, long liveMovies,
                                long stagedUsers, long liveUsers,
                                long stagedShowtimes, long liveShowtimes) {
    }

    /** Outcome of copying a live table back into its staging table. */
    public record RefreshResult(String target, int rows) {
    }
}
