package com.moviebooking.report.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.moviebooking.report.dto.ReportDto.DayPoint;
import com.moviebooking.report.dto.ReportDto.MethodRow;
import com.moviebooking.report.dto.ReportDto.MovieRow;
import com.moviebooking.report.dto.ReportDto.SalesReport;
import com.moviebooking.report.dto.ReportDto.Totals;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Builds the printable sales report (PDF) with vector charts - no images, so it stays sharp at any zoom.
 * Page 1: headline numbers, daily revenue bars, payment-method split, top movies.
 * Page 2: the same figures as tables, so the report can be read and checked line by line.
 */
@Service
public class ReportPdfService {

    // brand colours
    private static final Color INK = new Color(22, 22, 31);
    private static final Color MUTED = new Color(120, 118, 138);
    private static final Color ACCENT = new Color(248, 68, 100);
    private static final Color ACCENT_2 = new Color(255, 138, 76);
    private static final Color OK = new Color(31, 122, 61);
    private static final Color PANEL = new Color(246, 245, 249);
    private static final Color LIGHT = new Color(198, 195, 212);
    private static final Color LINE = new Color(214, 211, 224);
    private static final Color[] SERIES = {
            new Color(248, 68, 100), new Color(255, 138, 76), new Color(89, 165, 255),
            new Color(55, 214, 122), new Color(155, 110, 240), new Color(255, 176, 32)
    };

    private static final float LEFT = 40;
    private static final float WIDTH = PageSize.A4.getWidth() - 80;

    public byte[] build(SalesReport r) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, LEFT, LEFT, 36, 36);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.open();
            PdfContentByte cb = writer.getDirectContent();
            BaseFont regular = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            BaseFont bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);

            coverPage(cb, regular, bold, r);
            doc.newPage();
            tablePage(doc, regular, bold, r);
            doc.close();
        } catch (DocumentException | java.io.IOException ex) {
            throw new IllegalStateException("Could not build the PDF report: " + ex.getMessage(), ex);
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ page 1: dashboard

    private void coverPage(PdfContentByte cb, BaseFont regular, BaseFont bold, SalesReport r) {
        Totals t = r.totals();
        float pageH = PageSize.A4.getHeight();
        float pageW = PageSize.A4.getWidth();

        // header band
        cb.setColorFill(INK);
        cb.rectangle(0, pageH - 108, pageW, 108);
        cb.fill();
        cb.setColorFill(ACCENT);
        cb.rectangle(0, pageH - 112, pageW, 4);
        cb.fill();

        text(cb, bold, 20, LEFT, pageH - 52, "CineMax - Sales Report", Color.WHITE);
        text(cb, regular, 9, LEFT, pageH - 70,
                "Period " + r.from() + " to " + r.to() + "   |   all figures in LKR", LIGHT);
        text(cb, regular, 9, LEFT, pageH - 86,
                "Generated " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), LIGHT);

        // headline figure, right aligned so it can never run into the text on the left
        right(cb, bold, 24, pageW - LEFT, pageH - 56, money(t.net(), true), Color.WHITE);
        right(cb, regular, 8, pageW - LEFT, pageH - 72, "NET REVENUE", LIGHT);
        right(cb, regular, 7.5f, pageW - LEFT, pageH - 88, "cancelled bookings excluded", LIGHT);

        // four KPI boxes, then another four
        float boxW = (WIDTH - 3 * 10) / 4;
        float y = pageH - 152;
        y = kpiRow(cb, regular, bold, y, boxW, new String[][]{
                {"Gross sales", money(t.gross(), true)},
                {"Refunds", money(t.refunds(), true)},
                {"Net sales", money(t.net(), true)},
                {"Convenience fees", money(t.fees(), true)}
        });
        y = kpiRow(cb, regular, bold, y - 10, boxW, new String[][]{
                {"Tickets sold", String.valueOf(t.tickets())},
                {"Payments", String.valueOf(t.payments())},
                {"Confirmed bookings", String.valueOf(t.confirmedBookings())},
                {"Occupancy", String.format(Locale.ROOT, "%.1f%%", t.occupancyPercent())}
        });

        // daily revenue bars
        float chartTop = sectionTitle(cb, regular, bold, y - 32, "Daily net revenue");
        dailyBars(cb, regular, bold, chartTop, r.daily());

        // bottom band: payment-method donut on the left, top movies on the right
        float bandTop = 330;
        float donutSize = 118;
        float donutX = LEFT;
        float donutBottom = bandTop - donutSize - 34;
        text(cb, bold, 11, donutX, bandTop, "Payment methods", INK);
        methodDonut(cb, regular, bold, donutX, donutBottom, donutSize, r.byMethod());
        legend(cb, regular, bold, donutX + donutSize + 14, bandTop - 20, r.byMethod());

        float mvX = LEFT + 300;
        movieBars(cb, regular, bold, mvX, bandTop, WIDTH - 300, r.byMovie());
    }

    private float kpiRow(PdfContentByte cb, BaseFont regular, BaseFont bold, float top, float w, String[][] boxes) {
        float h = 52;
        for (int i = 0; i < boxes.length; i++) {
            float x = LEFT + i * (w + 10);
            cb.setColorFill(PANEL);
            cb.roundRectangle(x, top - h, w, h, 7);
            cb.fill();
            cb.setColorFill(ACCENT);
            cb.rectangle(x, top - h, 3, h);
            cb.fill();
            text(cb, regular, 7.5f, x + 12, top - 17, boxes[i][0].toUpperCase(Locale.ROOT), MUTED);
            // shrink the value a little when the number is long, so it always fits inside the box
            float size = boxes[i][1].length() > 12 ? 11 : boxes[i][1].length() > 9 ? 13 : 15;
            text(cb, bold, size, x + 12, top - 38, boxes[i][1], INK);
        }
        return top - h;
    }

    private float sectionTitle(PdfContentByte cb, BaseFont regular, BaseFont bold, float top, String title) {
        text(cb, bold, 11.5f, LEFT, top, title, INK);
        cb.setColorStroke(LINE);
        cb.setLineWidth(0.6f);
        cb.moveTo(LEFT, top - 7);
        cb.lineTo(LEFT + WIDTH, top - 7);
        cb.stroke();
        return top - 26;
    }

    /** Vertical bars, one per day, with a grid, axis labels and a value label on every bar. */
    private void dailyBars(PdfContentByte cb, BaseFont regular, BaseFont bold, float top, List<DayPoint> days) {
        float h = 132;
        float base = top - h;
        BigDecimal max = BigDecimal.ONE;
        for (DayPoint d : days) {
            if (d.net().compareTo(max) > 0) max = d.net();
        }

        // horizontal grid + money axis on the left
        cb.setLineWidth(0.4f);
        for (int i = 0; i <= 4; i++) {
            float gy = base + h * i / 4f;
            cb.setColorStroke(i == 0 ? LINE : new Color(235, 233, 242));
            cb.moveTo(LEFT + 40, gy);
            cb.lineTo(LEFT + WIDTH, gy);
            cb.stroke();
            BigDecimal v = max.multiply(BigDecimal.valueOf(i)).divide(BigDecimal.valueOf(4), 0, RoundingMode.HALF_UP);
            right(cb, regular, 6.2f, LEFT + 34, gy - 2, shortMoney(v), MUTED);
        }

        int n = Math.max(days.size(), 1);
        float plotW = WIDTH - 44;
        float slot = plotW / n;
        float barW = Math.max(3, Math.min(24, slot * 0.6f));
        for (int i = 0; i < days.size(); i++) {
            DayPoint d = days.get(i);
            float x = LEFT + 44 + slot * i + (slot - barW) / 2f;
            float bh = d.net().floatValue() / max.floatValue() * (h - 8);
            if (d.net().signum() > 0 && bh < 2) bh = 2;
            cb.setColorFill(d.net().signum() > 0 ? ACCENT : new Color(226, 223, 234));
            cb.rectangle(x, base, barW, Math.max(bh, 1.5f));
            cb.fill();
            String label = d.date().length() >= 10 ? d.date().substring(8, 10) + "/" + d.date().substring(5, 7) : d.date();
            centre(cb, regular, 6.2f, x + barW / 2f, base - 10, label, MUTED);
            if (d.net().signum() > 0) {
                centre(cb, regular, 5.6f, x + barW / 2f, base + bh + 3, shortMoney(d.net()), MUTED);
            }
        }
        text(cb, regular, 6.8f, LEFT, base - 24,
                days.size() + " day(s) - each bar is one day's net revenue, after refunds", MUTED);
    }

    /** Donut of payment methods; the legend is drawn separately so it never sits on the ring. */
    private void methodDonut(PdfContentByte cb, BaseFont regular, BaseFont bold,
                             float x, float y, float size, List<MethodRow> methods) {
        BigDecimal total = BigDecimal.ZERO;
        for (MethodRow m : methods) total = total.add(m.amount());

        float cx = x + size / 2f;
        float cy = y + size / 2f;
        float rOuter = size / 2f - 2;
        float rInner = rOuter * 0.6f;

        if (total.signum() <= 0) {
            cb.setColorFill(new Color(235, 233, 242));
            cb.circle(cx, cy, rOuter);
            cb.fill();
            cb.setColorFill(Color.WHITE);
            cb.circle(cx, cy, rInner);
            cb.fill();
            return;
        }

        float angle = 90;
        for (int i = 0; i < methods.size(); i++) {
            MethodRow m = methods.get(i);
            float extent = m.amount().floatValue() / total.floatValue() * 360f;
            cb.setColorFill(SERIES[i % SERIES.length]);
            cb.moveTo(cx, cy);
            cb.arc(cx - rOuter, cy - rOuter, cx + rOuter, cy + rOuter, angle, -extent);
            cb.closePath();
            cb.fill();
            angle -= extent;
        }
        cb.setColorFill(Color.WHITE);
        cb.circle(cx, cy, rInner);
        cb.fill();
        centre(cb, bold, 9, cx, cy + 3, shortMoney(total), INK);
        centre(cb, regular, 5.8f, cx, cy - 8, "collected", MUTED);
    }

    /** Colour swatch + method name + percent, one row per method. */
    private void legend(PdfContentByte cb, BaseFont regular, BaseFont bold,
                        float x, float top, List<MethodRow> methods) {
        BigDecimal total = BigDecimal.ZERO;
        for (MethodRow m : methods) total = total.add(m.amount());
        float y = top;
        for (int i = 0; i < methods.size(); i++) {
            MethodRow m = methods.get(i);
            cb.setColorFill(SERIES[i % SERIES.length]);
            cb.roundRectangle(x, y, 7, 7, 2);
            cb.fill();
            int pct = total.signum() == 0 ? 0 : Math.round(m.amount().floatValue() / total.floatValue() * 100f);
            text(cb, regular, 7.6f, x + 12, y + 1, label(m.method()) + "  " + pct + "%", INK);
            text(cb, regular, 6.8f, x + 12, y - 9, shortMoney(m.amount()) + "  (" + m.count() + " payment(s))", MUTED);
            y -= 26;
        }
    }

    /** Horizontal bars: the five best-earning movies, each with its own label line and bar. */
    private void movieBars(PdfContentByte cb, BaseFont regular, BaseFont bold,
                           float x, float top, float w, List<MovieRow> movies) {
        text(cb, bold, 11, x, top, "Top movies by net revenue", INK);
        if (movies.isEmpty()) {
            text(cb, regular, 8.5f, x, top - 20, "No tickets sold in this period.", MUTED);
            return;
        }
        BigDecimal max = BigDecimal.ONE;
        for (MovieRow m : movies) if (m.net().compareTo(max) > 0) max = m.net();

        int shown = Math.min(5, movies.size());
        float rowH = 40;
        float y = top - 26;
        for (int i = 0; i < shown; i++) {
            MovieRow m = movies.get(i);
            String title = m.title().length() > 24 ? m.title().substring(0, 23) + "." : m.title();
            text(cb, regular, 7.8f, x, y + 12, title, INK);
            right(cb, bold, 7.6f, x + w, y + 12, shortMoney(m.net()), INK);
            text(cb, regular, 6.4f, x, y + 3, m.tickets() + " ticket(s)", MUTED);
            float bw = m.net().floatValue() / max.floatValue() * w;
            cb.setColorFill(new Color(238, 236, 244));
            cb.roundRectangle(x, y - 6, w, 6, 3);
            cb.fill();
            cb.setColorFill(i == 0 ? ACCENT : ACCENT_2);
            cb.roundRectangle(x, y - 6, Math.max(bw, 3), 6, 3);
            cb.fill();
            y -= rowH;
        }
    }

    // ------------------------------------------------------------------ page 2: the numbers as tables

    private void tablePage(Document doc, BaseFont regular, BaseFont bold, SalesReport r) throws DocumentException {
        Totals t = r.totals();
        Font h1 = new Font(bold, 15, Font.BOLD, INK);
        Font small = new Font(regular, 8.5f, Font.NORMAL, MUTED);

        doc.add(new com.lowagie.text.Paragraph("CineMax - Sales Report (detail)", h1));
        doc.add(new com.lowagie.text.Paragraph(
                "Period " + r.from() + " to " + r.to() + "  |  generated "
                        + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        + "  |  currency LKR", small));
        space(doc, 8);

        // summary
        doc.add(heading("Summary", bold));
        PdfPTable summary = new PdfPTable(new float[]{3, 2});
        summary.setWidthPercentage(100);
        row(summary, regular, bold, "Gross sales", money(t.gross(), true));
        row(summary, regular, bold, "Refunds", money(t.refunds(), true));
        strongRow(summary, regular, bold, "Net sales", money(t.net(), true));
        row(summary, regular, bold, "Convenience fees collected", money(t.fees(), true));
        row(summary, regular, bold, "Payments", String.valueOf(t.payments()));
        row(summary, regular, bold, "Tickets sold", String.valueOf(t.tickets()));
        row(summary, regular, bold, "Bookings (confirmed / cancelled)",
                t.confirmedBookings() + " / " + t.cancelledBookings());
        row(summary, regular, bold, "Showtimes in period", String.valueOf(t.showCount()));
        row(summary, regular, bold, "Occupancy", String.format(Locale.ROOT, "%.1f%%", t.occupancyPercent()));
        doc.add(summary);
        space(doc, 7);

        // per movie
        doc.add(heading("Revenue by movie", bold));
        if (r.byMovie().isEmpty()) {
            doc.add(new com.lowagie.text.Paragraph("No tickets were sold in this period.", small));
        } else {
            PdfPTable mv = new PdfPTable(new float[]{1, 4, 2, 2, 2});
            mv.setWidthPercentage(100);
            header(mv, regular, bold, "#", "Movie", "Tickets", "Net (LKR)", "Share");
            BigDecimal totalNet = BigDecimal.ZERO;
            for (MovieRow m : r.byMovie()) totalNet = totalNet.add(m.net());
            int i = 1;
            for (MovieRow m : r.byMovie()) {
                String share = totalNet.signum() == 0 ? "0%"
                        : Math.round(m.net().floatValue() / totalNet.floatValue() * 100) + "%";
                row(mv, regular, bold, String.valueOf(i++), m.title(), String.valueOf(m.tickets()),
                        money(m.net(), true), share);
            }
            doc.add(mv);
        }
        space(doc, 7);

        // per method
        doc.add(heading("Revenue by payment method", bold));
        PdfPTable mt = new PdfPTable(new float[]{3, 2, 3});
        mt.setWidthPercentage(100);
        header(mt, regular, bold, "Method", "Payments", "Amount (LKR)");
        for (MethodRow m : r.byMethod()) {
            row(mt, regular, bold, label(m.method()), String.valueOf(m.count()), money(m.amount(), true));
        }
        doc.add(mt);
        space(doc, 7);

        // daily - printed as two side-by-side blocks so a 30-day period still fits on one page
        doc.add(heading("Daily net revenue", bold));
        PdfPTable dy = new PdfPTable(new float[]{2, 2, 1.4f, 2, 2, 1.4f});
        dy.setWidthPercentage(100);
        header(dy, regular, bold, "Date", "Net (LKR)", "Tickets", "Date", "Net (LKR)", "Tickets");
        List<DayPoint> days = r.daily();
        int half = (days.size() + 1) / 2;
        for (int i = 0; i < half; i++) {
            DayPoint a = days.get(i);
            compact(dy, regular, a.date());
            compact(dy, regular, money(a.net(), true));
            compact(dy, regular, String.valueOf(a.tickets()));
            int j = i + half;
            if (j < days.size()) {
                DayPoint b = days.get(j);
                compact(dy, regular, b.date());
                compact(dy, regular, money(b.net(), true));
                compact(dy, regular, String.valueOf(b.tickets()));
            } else {
                compact(dy, regular, "");
                compact(dy, regular, "");
                compact(dy, regular, "");
            }
        }
        doc.add(dy);
        space(doc, 7);

        doc.add(new com.lowagie.text.Paragraph(
                "Note: payments on bookings with status CANCELLED are left out of every money figure, "
                        + "ticket count and chart above. They remain visible in the transaction history "
                        + "and in data/reports/.", small));
    }

    // ------------------------------------------------------------------ small helpers

    private void header(PdfPTable table, BaseFont regular, BaseFont bold, String... cells) {
        for (String c : cells) {
            PdfPCell cell = new PdfPCell(new Phrase(c, new Font(bold, 8.5f, Font.BOLD, Color.WHITE)));
            cell.setBackgroundColor(INK);
            cell.setPadding(4);
            cell.setBorderWidth(0);
            table.addCell(cell);
        }
    }

    /** One normal row of cells. */
    private void row(PdfPTable table, BaseFont regular, BaseFont bold, String... cells) {
        for (String c : cells) font(table, regular, c, false);
    }

    /** One emphasised row (used for Net sales). */
    private void strongRow(PdfPTable table, BaseFont regular, BaseFont bold, String... cells) {
        for (String c : cells) font(table, bold, c, true);
    }

    /** Smaller, tighter cell used by the long daily table so a full month still fits on the page. */
    private void compact(PdfPTable table, BaseFont f, String value) {
        PdfPCell cell = new PdfPCell(new Phrase(value == null ? "" : value,
                new Font(f, 7.2f, Font.NORMAL, INK)));
        cell.setPadding(2.2f);
        cell.setBorderColor(LINE);
        cell.setBorderWidth(0.5f);
        table.addCell(cell);
    }

    private void font(PdfPTable table, BaseFont f, String value, boolean strong) {
        PdfPCell cell = new PdfPCell(new Phrase(value == null ? "" : value,
                new Font(f, 8.5f, strong ? Font.BOLD : Font.NORMAL, INK)));
        cell.setPadding(3.2f);
        cell.setBorderColor(LINE);
        cell.setBorderWidth(0.5f);
        if (strong) cell.setBackgroundColor(PANEL);
        table.addCell(cell);
    }

    private com.lowagie.text.Paragraph heading(String text, BaseFont bold) {
        com.lowagie.text.Paragraph p = new com.lowagie.text.Paragraph(text,
                new Font(bold, 11, Font.BOLD, INK));
        p.setSpacingAfter(4);
        return p;
    }

    private void space(Document doc, float h) throws DocumentException {
        com.lowagie.text.Paragraph p = new com.lowagie.text.Paragraph(" ");
        p.setLeading(h);
        doc.add(p);
    }

    private void text(PdfContentByte cb, BaseFont font, float size, float x, float y, String value, Color color) {
        cb.beginText();
        cb.setFontAndSize(font, size);
        cb.setColorFill(color);
        cb.setTextMatrix(x, y);
        cb.showText(value == null ? "" : value);
        cb.endText();
    }

    /** Draws text whose right edge sits at x. */
    private void right(PdfContentByte cb, BaseFont font, float size, float x, float y, String value, Color color) {
        cb.beginText();
        cb.setFontAndSize(font, size);
        cb.setColorFill(color);
        cb.showTextAligned(Element.ALIGN_RIGHT, value == null ? "" : value, x, y, 0);
        cb.endText();
    }

    /** Draws text centred on x. */
    private void centre(PdfContentByte cb, BaseFont font, float size, float x, float y, String value, Color color) {
        cb.beginText();
        cb.setFontAndSize(font, size);
        cb.setColorFill(color);
        cb.showTextAligned(Element.ALIGN_CENTER, value == null ? "" : value, x, y, 0);
        cb.endText();
    }

    private static String money(BigDecimal v, boolean grouped) {
        if (v == null) v = BigDecimal.ZERO;
        String s = v.setScale(2, RoundingMode.HALF_UP).toPlainString();
        if (!grouped) return s;
        String[] parts = s.split("\\.");
        StringBuilder sb = new StringBuilder();
        String whole = parts[0];
        for (int i = 0; i < whole.length(); i++) {
            if (i > 0 && (whole.length() - i) % 3 == 0 && whole.charAt(i - 1) != '-') sb.append(',');
            sb.append(whole.charAt(i));
        }
        return sb + "." + parts[1];
    }

    private static String shortMoney(BigDecimal v) {
        if (v == null) return "0";
        double d = v.doubleValue();
        if (Math.abs(d) >= 1_000_000) return String.format(Locale.ROOT, "%.2fM", d / 1_000_000);
        if (Math.abs(d) >= 1_000) return String.format(Locale.ROOT, "%.1fk", d / 1_000);
        return String.format(Locale.ROOT, "%.0f", d);
    }

    private static String label(String method) {
        return switch (method) {
            case "CARD" -> "Card";
            case "WALLET" -> "Mobile wallet";
            case "CASH_COUNTER" -> "Cinema counter";
            default -> method;
        };
    }
}
