package com.moviebooking.ticket.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.moviebooking.booking.dto.BookingDto.BookingResponse;
import com.moviebooking.booking.service.BookingService;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Builds the e-ticket for a paid booking: a landscape PDF with a QR code, and the QR code as a PNG.
 * Only CONFIRMED bookings get tickets, and only the customer who owns the booking can download one
 * (the ownership check happens in BookingService.getMine).
 */
@Service
public class TicketService {

    private static final Color INK = new Color(30, 27, 75);
    private static final Color ACCENT = new Color(124, 58, 237);
    private static final Color MUTED = new Color(107, 114, 128);
    private static final Color LINE = new Color(229, 231, 235);
    private static final Color STUB = new Color(245, 243, 255);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final BookingService bookingService;

    public TicketService(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** The printable ticket as PDF bytes. */
    public byte[] ticketPdf(Long customerId, Long bookingId) {
        BookingResponse b = paidBooking(customerId, bookingId);
        byte[] qr = qrBytes(payload(b));
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Ticket-shaped page: landscape, sized to the ticket so there is no empty area below the footer.
            Document doc = new Document(new Rectangle(595f, 345f), 0, 0, 0, 0);
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(buildTicket(b, qr));
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Could not create the ticket PDF", e);
        }
    }

    /** The QR code on its own, as PNG bytes (shown on the confirmation page). */
    public byte[] qrPng(Long customerId, Long bookingId) {
        return qrBytes(payload(paidBooking(customerId, bookingId)));
    }

    /** Suggested download name, e.g. cinemax-ticket-BK1A2B3C.pdf. */
    public String fileName(Long customerId, Long bookingId) {
        return "cinemax-ticket-" + paidBooking(customerId, bookingId).reference() + ".pdf";
    }

    private BookingResponse paidBooking(Long customerId, Long bookingId) {
        BookingResponse b = bookingService.getMine(customerId, bookingId);
        if (!"CONFIRMED".equals(b.status())) {
            throw new IllegalStateException("Your ticket is available once the booking is paid.");
        }
        return b;
    }

    /** What the QR code carries: enough for staff to match the ticket to the booking. */
    private static String payload(BookingResponse b) {
        return "CINEMAX|" + b.reference() + "|" + b.movieTitle() + "|" + b.showTime() + "|"
                + String.join(",", b.seatCodes());
    }

    private static byte[] qrBytes(String text) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 320, 320);
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Could not create the QR code", e);
        }
    }

    private static PdfPTable buildTicket(BookingResponse b, byte[] qr) throws Exception {
        PdfPTable page = new PdfPTable(new float[]{63f, 37f});
        page.setWidthPercentage(100);

        // Header band
        PdfPCell brand = textCell("CINEMAX  |  E-TICKET", font(13, Font.BOLD, Color.WHITE), INK, 14);
        brand.setColspan(1);
        page.addCell(brand);
        PdfPCell refHead = textCell("Booking  " + b.reference(), font(11, Font.BOLD, Color.WHITE), INK, 14);
        refHead.setHorizontalAlignment(Element.ALIGN_RIGHT);
        page.addCell(refHead);

        // Left: film and showtime details
        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.setPadding(20);
        left.addElement(new Paragraph(b.movieTitle(), font(22, Font.BOLD, INK)));
        left.addElement(new Paragraph(b.theatreName() + "  |  " + b.screenName(), font(11, Font.NORMAL, MUTED)));
        left.addElement(spacer(10));
        left.addElement(new Paragraph(
                b.showTime().format(DAY) + "  at  " + b.showTime().format(TIME), font(14, Font.BOLD, ACCENT)));
        left.addElement(spacer(12));
        left.addElement(detailTable(
                "Seats", String.join(", ", b.seatCodes()),
                "Customer", b.customerName(),
                "Amount paid", money(b.totalAmount()),
                "Status", b.status()));
        page.addCell(left);

        // Right: QR stub
        Image qrImage = Image.getInstance(qr);
        qrImage.scaleAbsolute(128, 128);
        PdfPCell stub = new PdfPCell();
        stub.setBorder(Rectangle.LEFT);
        stub.setBorderColor(LINE);
        stub.setBackgroundColor(STUB);
        stub.setPadding(18);
        stub.setHorizontalAlignment(Element.ALIGN_CENTER);
        stub.setVerticalAlignment(Element.ALIGN_MIDDLE);
        stub.addElement(qrImage);
        Paragraph caption = new Paragraph("Scan at the entrance", font(9, Font.NORMAL, MUTED));
        caption.setAlignment(Element.ALIGN_CENTER);
        caption.setSpacingBefore(6);
        stub.addElement(caption);
        page.addCell(stub);

        // Footer
        PdfPCell footer = textCell(
                "Arrive 15 minutes before the show. This ticket is valid for this showtime only. "
                        + "Demo ticket issued in sandbox mode.",
                font(8.5f, Font.NORMAL, MUTED), new Color(250, 250, 252), 9);
        footer.setColspan(2);
        page.addCell(footer);
        return page;
    }

    /** Two-column label/value table. Takes label/value pairs. */
    private static PdfPTable detailTable(String... pairs) {
        PdfPTable t = new PdfPTable(new float[]{1.1f, 2f});
        t.setWidthPercentage(100);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            PdfPCell label = textCell(pairs[i], font(10, Font.NORMAL, MUTED), null, 6);
            PdfPCell value = textCell(pairs[i + 1], font(10.5f, Font.BOLD, INK), null, 6);
            label.setBorder(Rectangle.BOTTOM);
            value.setBorder(Rectangle.BOTTOM);
            label.setBorderColor(LINE);
            value.setBorderColor(LINE);
            t.addCell(label);
            t.addCell(value);
        }
        return t;
    }

    private static PdfPCell textCell(String text, Font font, Color background, float padding) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBorder(Rectangle.NO_BORDER);
        if (background != null) {
            c.setBackgroundColor(background);
        }
        c.setPadding(padding);
        return c;
    }

    private static Paragraph spacer(float height) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(height);
        return p;
    }

    private static Font font(float size, int style, Color color) {
        return new Font(Font.HELVETICA, size, style, color);
    }

    private static String money(BigDecimal amount) {
        return String.format(Locale.ENGLISH, "LKR %,.2f", amount);
    }
}
