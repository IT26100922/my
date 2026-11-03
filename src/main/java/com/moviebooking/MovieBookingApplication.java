package com.moviebooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class MovieBookingApplication {

    public static void main(String[] args) {
        // The cinema works in Sri Lanka time. Using the same zone as the MySQL connector (serverTimezone=Asia/Colombo)
        // keeps stored showtimes, "now" and the seed files in step on every machine.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Colombo"));
        SpringApplication.run(MovieBookingApplication.class, args);
    }
}
