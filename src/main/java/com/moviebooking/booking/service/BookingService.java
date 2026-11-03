package com.moviebooking.booking.service;

import com.moviebooking.booking.dto.BookingDto.BookingRequest;
import com.moviebooking.booking.dto.BookingDto.BookingResponse;
import com.moviebooking.booking.dto.BookingDto.BookingUpdateRequest;
import com.moviebooking.booking.dto.BookingDto.CancelPreview;
import com.moviebooking.booking.dto.BookingDto.CancelSeatsRequest;
import com.moviebooking.booking.dto.BookingDto.PromoQuote;
import com.moviebooking.booking.dto.BookingDto.CancellationResult;
import com.moviebooking.booking.dto.BookingDto.RescheduleRequest;
import com.moviebooking.booking.entity.Booking;
import com.moviebooking.booking.entity.BookingStatus;
import com.moviebooking.booking.policy.CancellationPolicy;
import com.moviebooking.booking.repository.BookingRepository;
import com.moviebooking.exception.BusinessException;
import com.moviebooking.exception.ResourceNotFoundException;
import com.moviebooking.notification.service.NotificationService;
import com.moviebooking.promo.service.PromoService;
import com.moviebooking.payment.entity.Payment;
import com.moviebooking.payment.entity.PaymentMethodType;
import com.moviebooking.payment.entity.PaymentStatus;
import com.moviebooking.payment.processor.PaymentCharge;
import com.moviebooking.payment.processor.PaymentProcessor;
import com.moviebooking.payment.repository.PaymentRepository;
import com.moviebooking.show.entity.Showtime;
import com.moviebooking.show.repository.ShowtimeRepository;
import com.moviebooking.show.service.ShowService;
import com.moviebooking.user.entity.Customer;
import com.moviebooking.user.repository.CustomerRepository;
import com.moviebooking.waitlist.service.WaitlistService;
import com.moviebooking.wallet.service.WalletService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Booking CRUD and seat reservation.
 * CREATE: create()   READ: listMine() / getMine() / listAll()   UPDATE: updateSeats()   DELETE: cancel()
 */
@Service
@Transactional
public class BookingService {

    private static final int MAX_SEATS_PER_BOOKING = 10;
    /** Moves follow the same rule as cancellations: they close 2 hours before the original show. */
    static final long MOVE_CUTOFF_MINUTES = 120;

    private final BookingRepository bookings;
    private final ShowtimeRepository showtimes;
    private final CustomerRepository customers;
    private final PaymentRepository payments;
    private final ShowService showService;
    private final List<CancellationPolicy> policies;
    private final List<PaymentProcessor> processors;
    private final NotificationService notifications;
    private final PromoService promos;
    private final WalletService wallet;
    private final WaitlistService waitlist;
    private final int holdMinutes;

    public BookingService(BookingRepository bookings, ShowtimeRepository showtimes, CustomerRepository customers,
                          PaymentRepository payments, ShowService showService,
                          List<CancellationPolicy> policies, List<PaymentProcessor> processors,
                          NotificationService notifications, PromoService promos, WalletService wallet,
                          WaitlistService waitlist,
                          @Value("${app.booking.hold-minutes:10}") int holdMinutes) {
        this.notifications = notifications;
        this.promos = promos;
        this.wallet = wallet;
        this.waitlist = waitlist;
        this.holdMinutes = holdMinutes;
        this.bookings = bookings;
        this.showtimes = showtimes;
        this.customers = customers;
        this.payments = payments;
        this.showService = showService;
        this.policies = policies;
        this.processors = processors;
    }

    /** CREATE: reserve seats. The booking waits for payment (PENDING_PAYMENT). */
    public BookingResponse create(Long customerId, BookingRequest req) {
        Customer customer = customers.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        Showtime show = lockShow(req.showId());
        if (!show.getStartTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException("This showtime has already started");
        }
        Set<String> seats = validateSeats(show, req.seatCodes(), -1L);
        // Each seat is priced by the band of its row, then a promo code takes its discount off the total.
        BigDecimal total = showService.totalForSeats(show, seats);
        PromoQuote quote = null;
        if (req.promoCode() != null && !req.promoCode().isBlank()) {
            quote = promos.applyTo(null, req.promoCode(), total);
            total = quote.newTotal();
        }
        Booking booking = new Booking(newReference(), customer, show, seats, total,
                LocalDateTime.now().plusMinutes(holdMinutes));
        if (quote != null) {
            booking.applyDiscount(quote.code(), quote.discount());
        }
        return BookingResponse.from(bookings.save(booking));
    }

    /** READ: the logged-in customer's bookings, newest first. */
    @Transactional(readOnly = true)
    public List<BookingResponse> listMine(Long customerId) {
        return bookings.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(BookingResponse::from).toList();
    }

    /** READ: one booking that belongs to this customer. */
    @Transactional(readOnly = true)
    public BookingResponse getMine(Long customerId, Long bookingId) {
        return BookingResponse.from(findOwned(customerId, bookingId));
    }

    /** READ (admin): one page of bookings, newest first, for the paged admin table. */
    @Transactional(readOnly = true)
    public Page<BookingResponse> page(int page, int size) {
        return bookings.findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))))
                .map(BookingResponse::from);
    }

    /** READ (admin): all bookings. */
    @Transactional(readOnly = true)
    public List<BookingResponse> listAll() {
        return bookings.findAllByOrderByCreatedAtDesc().stream().map(BookingResponse::from).toList();
    }

    /** UPDATE: change seats while the booking is still awaiting payment. */
    public BookingResponse updateSeats(Long customerId, Long bookingId, BookingUpdateRequest req) {
        Booking booking = findOwned(customerId, bookingId);
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BusinessException("Only bookings awaiting payment can change seats");
        }
        if (booking.isHoldExpired(LocalDateTime.now())) {
            throw new BusinessException("Your seat hold has expired. Please reserve the seats again.");
        }
        lockShow(booking.getShow().getId());
        Set<String> seats = validateSeats(booking.getShow(), req.seatCodes(), bookingId);
        booking.replaceSeats(seats, showService.totalForSeats(booking.getShow(), seats));
        return BookingResponse.from(booking);
    }

    /**
     * DELETE (cancel):
     * - awaiting payment -> the booking row is deleted, seats are released;
     * - confirmed -> cancellation policy decides the refund, payment is marked refunded.
     */
    public CancellationResult cancel(Long customerId, Long bookingId) {
        Booking booking = findOwned(customerId, bookingId);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BusinessException("This booking is already cancelled");
        }
        if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
            bookings.delete(booking);
            return new CancellationResult("Reservation removed and seats released",
                    "No payment taken", BigDecimal.ZERO.setScale(2), "DELETED");
        }

        CancellationPolicy policy = resolvePolicy(hoursBeforeShow(booking));
        BigDecimal refund = policy.refundFor(booking.getTotalAmount());
        refundAcross(booking, refund);
        booking.markCancelled();
        // The money goes back into the wallet at once, so the customer can spend it again immediately.
        wallet.credit(customerId, refund, "Refund for booking " + booking.getReference());
        notifications.notifyBookingCancelled(booking, refund);
        // Seats are free again: tell the people waiting for this show, first come first served.
        waitlist.notifyFreedSeats(booking.getShow(), booking.getSeatCount());
        return new CancellationResult("Booking cancelled", policy.name(), refund,
                booking.getStatus().name());
    }

    /**
     * UPDATE (partial cancel): give back some of the seats and keep the rest.
     *
     * <p>The refund follows exactly the same policy as a full cancellation, but only on the value of the
     * seats that are being given up. The last remaining seat cannot be dropped this way — for that the
     * customer cancels the whole booking, so a booking always keeps at least one seat.</p>
     */
    public CancellationResult cancelSeats(Long customerId, Long bookingId, CancelSeatsRequest req) {
        Booking booking = findOwned(customerId, bookingId);
        if (!BookingStatus.paidStatuses().contains(booking.getStatus())) {
            throw new BusinessException("Only a paid booking can give seats back");
        }
        if (req.seatCodes() == null || req.seatCodes().isEmpty()) {
            throw new BusinessException("Choose the seat(s) to give back");
        }
        Set<String> wanted = req.seatCodes().stream()
                .map(code -> code == null ? "" : code.trim().toUpperCase())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> owned = new LinkedHashSet<>(booking.getSeatCodes());
        if (!owned.containsAll(wanted)) {
            throw new BusinessException("These seats are not part of this booking");
        }
        if (wanted.size() >= owned.size()) {
            throw new BusinessException("Use Cancel booking to give back every seat");
        }
        Set<String> kept = new LinkedHashSet<>(owned);
        kept.removeAll(wanted);

        BigDecimal releasedValue = showService.totalForSeats(booking.getShow(), wanted);
        BigDecimal newTotal = showService.totalForSeats(booking.getShow(), kept);
        CancellationPolicy policy = resolvePolicy(hoursBeforeShow(booking));
        BigDecimal refund = policy.refundFor(releasedValue);

        booking.keepOnly(kept, newTotal);
        refundAcross(booking, refund);
        wallet.credit(customerId, refund, "Refund for " + wanted.size() + " seat(s) on "
                + booking.getReference());
        notifications.notifySeatsReleased(booking, wanted, refund);
        waitlist.notifyFreedSeats(booking.getShow(), wanted.size());
        return new CancellationResult("Seat(s) " + String.join(", ", wanted) + " released", policy.name(),
                refund, booking.getStatus().name());
    }

    /** READ: what cancelling now would refund. Nothing is changed. */
    @Transactional(readOnly = true)
    public CancelPreview cancelPreview(Long customerId, Long bookingId) {
        Booking booking = findOwned(customerId, bookingId);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BusinessException("This booking is already cancelled");
        }
        if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
            return new CancelPreview("RELEASE_SEATS", BigDecimal.ZERO.setScale(2), 0, true);
        }
        long hours = hoursBeforeShow(booking);
        CancellationPolicy policy = resolvePolicy(hours);
        return new CancelPreview(policy.name(), policy.refundFor(booking.getTotalAmount()), hours, false);
    }

    /**
     * UPDATE (move): moves a confirmed booking to another showtime of the same film.
     * - allowed until MOVE_CUTOFF_MINUTES before the original show;
     * - the customer chooses the same number of seats on the new show;
     * - a higher price is charged with req.method and req.reference; a lower price is refunded to the payments.
     */
    public BookingResponse reschedule(Long customerId, Long bookingId, RescheduleRequest req) {
        Booking booking = findOwned(customerId, bookingId);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException("Only confirmed bookings can be moved to another showtime");
        }
        Showtime from = booking.getShow();
        LocalDateTime now = LocalDateTime.now();
        if (!from.getStartTime().isAfter(now)) {
            throw new BusinessException("This showtime has already started");
        }
        if (Duration.between(now, from.getStartTime()).toMinutes() < MOVE_CUTOFF_MINUTES) {
            throw new BusinessException("Bookings can be moved until 2 hours before the show");
        }
        if (req.showId().equals(from.getId())) {
            throw new BusinessException("Choose a different showtime");
        }
        Showtime to = lockShow(req.showId());
        if (!to.getMovie().getId().equals(from.getMovie().getId())) {
            throw new BusinessException("The new showtime must be for the same film");
        }
        if (!to.getStartTime().isAfter(now)) {
            throw new BusinessException("That showtime has already started");
        }
        if (req.seatCodes() == null || req.seatCodes().size() != booking.getSeatCount()) {
            throw new BusinessException("Choose " + booking.getSeatCount() + " seat(s) for the new showtime");
        }
        Set<String> seats = validateSeats(to, req.seatCodes(), -1L);
        BigDecimal newTotal = showService.totalForSeats(to, seats);
        BigDecimal difference = newTotal.subtract(booking.getTotalAmount()).setScale(2, RoundingMode.HALF_UP);

        if (difference.signum() > 0) {
            if (req.method() == null || req.method().isBlank()) {
                throw new BusinessException("Pay the difference of LKR " + difference.toPlainString() + " to move to this showtime");
            }
            chargeDifference(booking, difference, req.method(), req.reference());
        } else if (difference.signum() < 0) {
            refundAcross(booking, difference.negate());
        }
        booking.moveTo(to, seats, newTotal);
        notifications.notifyBookingMoved(booking, from);
        return BookingResponse.from(booking);
    }

    /** DELETE (admin): remove a cancelled booking together with its payment records. */
    public void adminDelete(Long bookingId) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
        if (booking.getStatus() != BookingStatus.CANCELLED) {
            throw new BusinessException("Only cancelled bookings can be deleted");
        }
        payments.deleteAll(payments.findByBookingId(bookingId));
        bookings.delete(booking);
    }

    /**
     * Validates requested seats: format, layout, duplicates and clashes with other bookings.
     * Returns normalised seat codes (upper case).
     */
    private Set<String> validateSeats(Showtime show, List<String> rawSeats, Long excludeBookingId) {
        if (rawSeats == null || rawSeats.isEmpty()) {
            throw new BusinessException("Select at least one seat");
        }
        if (rawSeats.size() > MAX_SEATS_PER_BOOKING) {
            throw new BusinessException("You can book at most " + MAX_SEATS_PER_BOOKING + " seats per booking");
        }
        Set<String> seats = new LinkedHashSet<>();
        for (String raw : rawSeats) {
            String code = raw == null ? "" : raw.trim().toUpperCase();
            // ABSTRACTION: the screen decides whether a seat exists in its layout.
            if (!show.getScreen().isValidSeat(code)) {
                throw new BusinessException("Invalid seat: " + raw);
            }
            if (!seats.add(code)) {
                throw new BusinessException("Seat " + code + " was selected twice");
            }
        }
        Set<String> taken = new HashSet<>(bookings.findHeldSeatCodes(show.getId(),
                excludeBookingId == null ? -1L : excludeBookingId, BookingStatus.activeStatuses(), LocalDateTime.now()));
        List<String> clashes = seats.stream().filter(taken::contains).sorted().toList();
        if (!clashes.isEmpty()) {
            throw new BusinessException("Seat(s) already taken: " + String.join(", ", clashes));
        }
        return seats;
    }

    /**
     * Sweeper: unpaid bookings whose hold ran out are deleted, which releases their seats.
     * The customer receives a HOLD_EXPIRED notification.
     */
    @Scheduled(fixedDelayString = "${app.booking.hold-sweep-ms:30000}", initialDelayString = "${app.booking.hold-sweep-ms:30000}")
    public void releaseExpiredHolds() {
        for (Booking b : bookings.findExpiredHolds(BookingStatus.PENDING_PAYMENT, LocalDateTime.now())) {
            notifications.notifyHoldExpired(b.getCustomer(), b.getReference(), b.getShow().getMovie().getTitle());
            bookings.delete(b);
        }
    }

    /** Locks the showtime row so seat checks and seat writes for one show run one at a time. */
    private Showtime lockShow(Long id) {
        return showtimes.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Showtime not found: " + id));
    }

    private static long hoursBeforeShow(Booking booking) {
        return Duration.between(LocalDateTime.now(), booking.getShow().getStartTime()).toHours();
    }

    /** Charges the extra amount for a more expensive showtime, with the same processors as checkout. */
    private void chargeDifference(Booking booking, BigDecimal difference, String method, String reference) {
        PaymentMethodType type;
        try {
            type = PaymentMethodType.valueOf(method.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Unsupported payment method: " + method);
        }
        PaymentProcessor processor = processors.stream()
                .filter(p -> p.method() == type)
                .findFirst()
                .orElseThrow(() -> new BusinessException("Unsupported payment method: " + method));
        PaymentCharge charge = processor.charge(difference, reference);
        payments.save(new Payment(charge.transactionRef(), booking, charge.totalAmount(), charge.convenienceFee(),
                type, PaymentStatus.SUCCESS, charge.maskedReference()));
    }

    /**
     * Spreads a refund over the booking's payments, oldest first. Only the seat amount is refunded, never the
     * convenience fee, and each payment gives back at most its own remaining seat amount.
     */
    private void refundAcross(Booking booking, BigDecimal amount) {
        BigDecimal left = amount;
        for (Payment payment : payments.findByBookingId(booking.getId())) {
            if (left.signum() <= 0) {
                break;
            }
            if (payment.getStatus() == PaymentStatus.REFUNDED) {
                continue;
            }
            BigDecimal seatPart = payment.getAmount().subtract(payment.getConvenienceFee());
            BigDecimal room = seatPart.subtract(payment.getRefundedAmount());
            if (room.signum() <= 0) {
                continue;
            }
            BigDecimal part = left.min(room);
            payment.refund(part, seatPart);
            left = left.subtract(part);
        }
    }

    private CancellationPolicy resolvePolicy(long hoursBeforeShow) {
        return policies.stream()
                .filter(p -> p.supports(hoursBeforeShow))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No cancellation policy matches"));
    }

    private Booking findOwned(Long customerId, Long bookingId) {
        return bookings.findById(bookingId)
                .filter(b -> b.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
    }

    private Showtime findShow(Long id) {
        return showtimes.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Showtime not found: " + id));
    }

    private static String newReference() {
        return "BK" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
