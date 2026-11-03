package com.moviebooking.waitlist.dto;

import com.moviebooking.waitlist.entity.WaitlistEntry;

import java.time.LocalDateTime;

/** DTOs for the waitlist. */
public final class WaitlistDto {

    private WaitlistDto() {
    }

    public record WaitlistResponse(
            Long id,
            Long showId,
            String movieTitle,
            String theatreName,
            String screenName,
            LocalDateTime showTime,
            LocalDateTime joinedAt,
            boolean notified,
            long peopleAhead) {

        public static WaitlistResponse from(WaitlistEntry e, long peopleAhead) {
            return new WaitlistResponse(e.getId(), e.getShow().getId(),
                    e.getShow().getMovie().getTitle(), e.getShow().getScreen().getTheatre().getName(),
                    e.getShow().getScreen().getName(), e.getShow().getStartTime(), e.getJoinedAt(),
                    e.getNotifiedAt() != null, peopleAhead);
        }
    }
}
