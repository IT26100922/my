package com.moviebooking.theatre.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract screen (auditorium). Subclasses: StandardScreen, PremiumScreen, VipScreen.
 * <p>
 * INHERITANCE + POLYMORPHISM: each screen type defines its own category and price multiplier.
 * ABSTRACTION: callers use isValidSeat() and getAllSeatCodes() without knowing how seats are laid out.
 * ENCAPSULATION: layout values are changed only through setLayout(), which validates them.
 */
@Entity
@Table(name = "screens")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "screen_type", discriminatorType = DiscriminatorType.STRING)
public abstract class Screen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    @ManyToOne(optional = false)
    @JoinColumn(name = "theatre_id", nullable = false)
    private Theatre theatre;

    @Column(nullable = false)
    private Integer rowCount;

    @Column(nullable = false)
    private Integer seatsPerRow;

    protected Screen() {
        // required by JPA
    }

    /** POLYMORPHISM: the screen type code stored in the database. */
    public abstract String getScreenType();

    /** POLYMORPHISM: human-readable category shown in the UI. */
    public abstract String getCategory();

    /** POLYMORPHISM: multiplier applied to the base ticket price. */
    public abstract BigDecimal getPriceMultiplier();

    /** Validates the layout: rows A..Z (max 26), seats per row 1..30. */
    public void setLayout(int rows, int seatsPerRow) {
        if (rows < 1 || rows > 26) {
            throw new IllegalArgumentException("Row count must be between 1 and 26");
        }
        if (seatsPerRow < 1 || seatsPerRow > 30) {
            throw new IllegalArgumentException("Seats per row must be between 1 and 30");
        }
        this.rowCount = rows;
        this.seatsPerRow = seatsPerRow;
    }

    public int getTotalSeats() {
        return rowCount * seatsPerRow;
    }

    /** ABSTRACTION: a seat code like "B7" is valid only if it exists in this layout. */
    public boolean isValidSeat(String seatCode) {
        if (seatCode == null || !seatCode.matches("^[A-Z]\\d{1,2}$")) {
            return false;
        }
        int rowIndex = seatCode.charAt(0) - 'A';
        int seatNumber = Integer.parseInt(seatCode.substring(1));
        return rowIndex < rowCount && seatNumber >= 1 && seatNumber <= seatsPerRow;
    }

    /** Builds every seat code in the layout: A1..A12, B1..B12, ... */
    public List<String> getAllSeatCodes() {
        List<String> codes = new ArrayList<>();
        for (int r = 0; r < rowCount; r++) {
            for (int s = 1; s <= seatsPerRow; s++) {
                codes.add((char) ('A' + r) + String.valueOf(s));
            }
        }
        return codes;
    }

    /**
     * Which band a row belongs to. The screen is split in three: the front third is REGULAR, the middle
     * third PREMIUM and the back third VIP. A one or two row screen stays REGULAR, because a band that is a
     * single row of seats is not worth the extra price.
     */
    public SeatBand bandForRow(int rowIndex) {
        if (rowCount == null || rowCount < 3 || rowIndex < 0 || rowIndex >= rowCount) {
            return SeatBand.REGULAR;
        }
        int third = Math.max(1, rowCount / 3);
        if (rowIndex >= rowCount - third) {
            return SeatBand.VIP;
        }
        if (rowIndex >= rowCount - 2 * third) {
            return SeatBand.PREMIUM;
        }
        return SeatBand.REGULAR;
    }

    /** Band of a seat code such as "F7". Unknown codes fall back to REGULAR. */
    public SeatBand bandForSeat(String seatCode) {
        if (seatCode == null || seatCode.isBlank()) {
            return SeatBand.REGULAR;
        }
        int rowIndex = Character.toUpperCase(seatCode.trim().charAt(0)) - 'A';
        if (rowCount == null || rowIndex < 0 || rowIndex >= rowCount) {
            return SeatBand.REGULAR;
        }
        return bandForRow(rowIndex);
    }

    /** Rows of each band, for the seat map legend: [VIP, "F-H"], [PREMIUM, "D-E"], [REGULAR, "A-C"]. */
    public Map<SeatBand, String> bandRowLabels() {
        Map<SeatBand, StringBuilder> ranges = new LinkedHashMap<>();
        for (int r = 0; r < rowCount; r++) {
            ranges.computeIfAbsent(bandForRow(r), k -> new StringBuilder()).append((char) ('A' + r));
        }
        Map<SeatBand, String> labels = new LinkedHashMap<>();
        for (SeatBand band : new SeatBand[]{SeatBand.REGULAR, SeatBand.PREMIUM, SeatBand.VIP}) {
            StringBuilder rows = ranges.get(band);
            if (rows == null) {
                continue;
            }
            labels.put(band, rows.length() == 1
                    ? rows.toString()
                    : rows.charAt(0) + "-" + rows.charAt(rows.length() - 1));
        }
        return labels;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Theatre getTheatre() {
        return theatre;
    }

    public void setTheatre(Theatre theatre) {
        this.theatre = theatre;
    }

    public Integer getRowCount() {
        return rowCount;
    }

    public Integer getSeatsPerRow() {
        return seatsPerRow;
    }
}
